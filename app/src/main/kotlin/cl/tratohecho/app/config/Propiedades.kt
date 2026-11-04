package cl.tratohecho.app.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path

enum class Modo { LOCAL, DEMO }

@ConfigurationProperties("tratohecho")
data class PropiedadesTratohecho(
    val modo: Modo = Modo.LOCAL,
    val datos: Datos = Datos(),
    val accesoLocal: AccesoLocal = AccesoLocal(),
    val licencia: Licencia = Licencia(),
    val demo: Demo = Demo(),
) {
    data class Datos(
        /** Carpeta donde viven la base de datos y los respaldos. Vacío = carpeta estándar del sistema operativo. */
        val directorio: Path = CarpetaDatos.porDefecto(),
        val respaldosAutomaticos: Int = 7,
    )

    data class AccesoLocal(
        val habilitado: Boolean = false,
        /** Token de sesión que entrega el lanzador de escritorio. */
        val token: String = "",
    )

    data class Licencia(
        /** Clave pública Ed25519 (X.509, Base64) que verifica las licencias. */
        val clavePublica: String = "",
        val limitePrueba: Int = 10,
    )

    data class Demo(
        val reinicioCron: String = "0 0 * * * *",
        val solicitudesPorMinuto: Int = 120,
    )
}

/** Carpeta de datos estándar de cada sistema operativo. */
object CarpetaDatos {
    fun porDefecto(): Path {
        val so = System.getProperty("os.name").lowercase()
        val home = Path.of(System.getProperty("user.home"))
        return when {
            so.contains("win") -> Path.of(System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").toString(), "Tratohecho")
            so.contains("mac") -> home.resolve("Library/Application Support/Tratohecho")
            else -> Path.of(System.getenv("XDG_DATA_HOME") ?: home.resolve(".local/share").toString(), "tratohecho")
        }
    }
}
