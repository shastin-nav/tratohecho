package cl.tratohecho.app.respaldo

import cl.tratohecho.app.config.Modo
import cl.tratohecho.app.config.PropiedadesTratohecho
import cl.tratohecho.core.domain.ReglaDeNegocioException
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.info.BuildProperties
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.deleteIfExists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

data class Respaldo(
    val nombre: String,
    val bytes: Long,
    val creado: String,
)

/**
 * Respaldos de la base SQLite local en un .zip con manifiesto (ver docs/adr/0005-respaldos.md):
 *  - exportar: `VACUUM INTO` produce una copia consistente y compacta aunque la app esté en uso.
 *  - restaurar: valida el manifiesto y la integridad, respalda lo actual y usa `restore from` de sqlite-jdbc
 *    sobre la única conexión del pool, sin reiniciar la aplicación; luego aplica migraciones pendientes.
 */
@Service
class ServicioRespaldos(
    private val jdbc: JdbcClient,
    private val jdbcTemplate: JdbcTemplate,
    private val flyway: Flyway,
    private val propiedades: PropiedadesTratohecho,
    private val reloj: Clock,
    private val build: ObjectProvider<BuildProperties>,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sello = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    val carpeta: Path get() = propiedades.datos.directorio.resolve("respaldos")

    private fun exigirModoLocal() {
        if (propiedades.modo !=
            Modo.LOCAL
        ) {
            throw ReglaDeNegocioException("no_disponible", "Los respaldos solo existen en la versión instalada")
        }
    }

    private fun versionEsquema(): String =
        flyway
            .info()
            .current()
            ?.version
            ?.version ?: "0"

    /** Crea un respaldo y devuelve la ruta del .zip. */
    fun crear(prefijo: String = "respaldo"): Path {
        exigirModoLocal()
        Files.createDirectories(carpeta)
        val nombre = "$prefijo-${LocalDateTime.now(reloj).format(sello)}"
        val db = Files.createTempFile("tratohecho-", ".db")
        db.deleteIfExists() // VACUUM INTO exige que el destino no exista
        try {
            jdbc.sql("VACUUM INTO '${db.toAbsolutePath().toString().replace("'", "''")}'").update()
            val zip = carpeta.resolve("$nombre.zip")
            ZipOutputStream(Files.newOutputStream(zip)).use { salida ->
                salida.putNextEntry(ZipEntry(MANIFIESTO))
                Properties()
                    .apply {
                        setProperty("aplicacion", "tratohecho")
                        setProperty("versionAplicacion", build.ifAvailable?.version ?: "desarrollo")
                        setProperty("versionEsquema", versionEsquema())
                        setProperty("creado", LocalDateTime.now(reloj).toString())
                    }.store(salida, "Respaldo de Tratohecho")
                salida.closeEntry()
                salida.putNextEntry(ZipEntry(BASE))
                Files.copy(db, salida)
                salida.closeEntry()
            }
            return zip
        } finally {
            db.deleteIfExists()
        }
    }

    fun listar(): List<Respaldo> {
        exigirModoLocal()
        if (!Files.isDirectory(carpeta)) return emptyList()
        return carpeta
            .listDirectoryEntries("*.zip")
            .sortedByDescending { it.getLastModifiedTime() }
            .map { Respaldo(it.name, it.fileSize(), it.getLastModifiedTime().toString()) }
    }

    fun archivo(nombre: String): Path {
        exigirModoLocal()
        val ruta = carpeta.resolve(nombre).normalize()
        if (!ruta.startsWith(carpeta) || !nombre.endsWith(".zip") || !Files.isRegularFile(ruta)) {
            throw ReglaDeNegocioException("respaldo_inexistente", "No existe el respaldo $nombre")
        }
        return ruta
    }

    fun restaurar(zip: ByteArray): Path {
        exigirModoLocal()
        val temporal = Files.createTempFile("tratohecho-restaurar-", ".db")
        try {
            val manifiesto = extraer(zip, temporal)
            val versionRespaldo = manifiesto.getProperty("versionEsquema")?.toIntOrNull() ?: invalido("el manifiesto no indica la versión")
            val versionActual = versionEsquema().toIntOrNull() ?: 0
            if (versionRespaldo > versionActual) {
                throw ReglaDeNegocioException(
                    "respaldo_mas_nuevo",
                    "El respaldo viene de una versión más nueva de Tratohecho; actualiza la aplicación antes de restaurarlo",
                )
            }
            verificarIntegridad(temporal)
            val previo = crear("antes-de-restaurar")
            val ruta = temporal.toAbsolutePath().toString().replace("'", "''")
            // Comando propio de sqlite-jdbc: solo funciona con Statement simple (no PreparedStatement).
            jdbcTemplate.execute("restore from '$ruta'")
            flyway.migrate()
            log.info("Respaldo restaurado; el estado anterior quedó en {}", previo.name)
            return previo
        } finally {
            temporal.deleteIfExists()
        }
    }

    private fun extraer(
        zip: ByteArray,
        destino: Path,
    ): Properties {
        var manifiesto: Properties? = null
        var tieneBase = false
        ZipInputStream(zip.inputStream()).use { entrada ->
            generateSequence { entrada.nextEntry }.forEach { e ->
                when (e.name) {
                    MANIFIESTO -> manifiesto = Properties().apply { load(entrada) }
                    BASE -> {
                        Files.newOutputStream(destino).use { entrada.copyTo(it) }
                        tieneBase = true
                    }
                }
            }
        }
        val m = manifiesto ?: invalido("falta el manifiesto")
        if (m.getProperty("aplicacion") != "tratohecho" || !tieneBase) invalido("no es un respaldo de Tratohecho")
        return m
    }

    private fun verificarIntegridad(archivo: Path) {
        val cabecera = Files.newInputStream(archivo).use { it.readNBytes(16) }
        if (String(cabecera, Charsets.US_ASCII) != "SQLite format 3\u0000") invalido("la base de datos está dañada")
        java.sql.DriverManager.getConnection("jdbc:sqlite:${archivo.toAbsolutePath()}").use { conexion ->
            conexion.createStatement().use { st ->
                st.executeQuery("PRAGMA integrity_check").use { rs ->
                    if (!rs.next() || rs.getString(1) != "ok") invalido("la base de datos está dañada")
                }
            }
        }
    }

    private fun invalido(motivo: String): Nothing =
        throw ReglaDeNegocioException("respaldo_invalido", "El archivo no es un respaldo válido: $motivo")

    /** Respaldo automático al iniciar, conservando los últimos N. */
    @EventListener(ApplicationReadyEvent::class)
    fun respaldoAlIniciar() {
        if (propiedades.modo != Modo.LOCAL || propiedades.datos.respaldosAutomaticos <= 0) return
        try {
            crear("auto")
            carpeta
                .listDirectoryEntries("auto-*.zip")
                .sortedByDescending { it.name }
                .drop(propiedades.datos.respaldosAutomaticos)
                .forEach { it.deleteIfExists() }
        } catch (e: Exception) {
            log.warn("No se pudo crear el respaldo automático", e)
        }
    }

    companion object {
        const val MANIFIESTO = "manifiesto.properties"
        const val BASE = "tratohecho.db"
    }
}
