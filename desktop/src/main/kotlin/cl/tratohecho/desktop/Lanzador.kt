package cl.tratohecho.desktop

import cl.tratohecho.app.TratohechoApplication
import cl.tratohecho.app.config.CarpetaDatos
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/**
 * Punto de entrada de la versión instalada:
 *  1. Una sola instancia por usuario (bloqueo de archivo). Si ya está abierta, solo abre el navegador.
 *  2. Levanta Spring Boot en 127.0.0.1 con un puerto libre y un token de sesión aleatorio.
 *  3. Abre el navegador predeterminado y deja un ícono en la bandeja del sistema para volver a abrir o salir.
 */
fun main(args: Array<String>) {
    val carpeta = System.getenv("TRATOHECHO_DATOS")?.let(Path::of) ?: CarpetaDatos.porDefecto()
    Files.createDirectories(carpeta)
    val instancia = Instancia(carpeta)
    if (!instancia.adquirir()) {
        instancia.urlPublicada()?.let(::abrirNavegador) ?: avisar("Tratohecho ya se está abriendo. Espera unos segundos.")
        exitProcess(0)
    }

    val puerto = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
    val token = ByteArray(32).also(SecureRandom()::nextBytes).let(Base64.getUrlEncoder().withoutPadding()::encodeToString)
    val url = "http://127.0.0.1:$puerto/?t=$token"

    val contexto: ConfigurableApplicationContext =
        try {
            SpringApplication(TratohechoApplication::class.java)
                .apply {
                    setHeadless(false) // necesario para la bandeja del sistema
                    setAdditionalProfiles("local")
                }.run(
                    *args,
                    "--server.port=$puerto",
                    "--server.address=127.0.0.1",
                    "--tratohecho.acceso-local.token=$token",
                    "--tratohecho.datos.directorio=$carpeta",
                    "--logging.file.name=${carpeta.resolve("tratohecho.log")}",
                )
        } catch (e: Exception) {
            avisar("No se pudo iniciar Tratohecho.\n\n${e.message}\n\nRevisa el registro en ${carpeta.resolve("tratohecho.log")}")
            instancia.liberar()
            exitProcess(1)
        }

    instancia.publicar(url)
    val salir = {
        contexto.close()
        instancia.liberar()
        exitProcess(0)
    }
    Runtime.getRuntime().addShutdownHook(Thread { instancia.liberar() })
    mostrarBandeja(url, carpeta, salir)
    if (System.getenv("TRATOHECHO_SIN_NAVEGADOR") == null) abrirNavegador(url)
}

private fun mostrarBandeja(
    url: String,
    carpeta: Path,
    salir: () -> Unit,
) {
    // Cualquier problema gráfico (sin escritorio, X11 ausente…) no debe impedir usar la app desde el navegador.
    runCatching { agregarIconoBandeja(url, carpeta, salir) }
}

private fun agregarIconoBandeja(
    url: String,
    carpeta: Path,
    salir: () -> Unit,
) {
    if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return
    val imagen = Toolkit.getDefaultToolkit().getImage(object {}.javaClass.getResource("/icono-bandeja.png"))
    val menu =
        PopupMenu().apply {
            add(MenuItem("Abrir Tratohecho").apply { addActionListener { abrirNavegador(url) } })
            add(MenuItem("Abrir carpeta de datos").apply { addActionListener { runCatching { Desktop.getDesktop().open(carpeta.toFile()) } } })
            addSeparator()
            add(MenuItem("Salir").apply { addActionListener { salir() } })
        }
    val icono =
        TrayIcon(imagen, "Tratohecho", menu).apply {
            isImageAutoSize = true
            addActionListener { abrirNavegador(url) }
        }
    SystemTray.getSystemTray().add(icono)
}

fun abrirNavegador(url: String) {
    val uri = URI(url)
    val abierto =
        runCatching {
            check(!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            Desktop.getDesktop().browse(uri)
        }.isSuccess
    if (abierto) return
    val so = System.getProperty("os.name").lowercase()
    val comando =
        when {
            so.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
            so.contains("mac") -> listOf("open", url)
            else -> listOf("xdg-open", url)
        }
    runCatching { ProcessBuilder(comando).inheritIO().start() }
        .onFailure { System.err.println("Abre en tu navegador: $url") }
}

private fun avisar(mensaje: String) {
    System.err.println(mensaje)
    if (!GraphicsEnvironment.isHeadless()) runCatching { JOptionPane.showMessageDialog(null, mensaje, "Tratohecho", JOptionPane.WARNING_MESSAGE) }
}

/** Bloqueo de instancia única y archivo con la URL de la sesión (legible solo por el usuario actual). */
private class Instancia(
    carpeta: Path,
) {
    private val archivoBloqueo = carpeta.resolve("instancia.lock")
    private val archivoUrl = carpeta.resolve("instancia.properties")
    private var canal: FileChannel? = null
    private var bloqueo: FileLock? = null

    fun adquirir(): Boolean {
        canal = FileChannel.open(archivoBloqueo, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        bloqueo = runCatching { canal!!.tryLock() }.getOrNull()
        return bloqueo != null
    }

    fun publicar(url: String) {
        Files.deleteIfExists(archivoUrl)
        runCatching { Files.createFile(archivoUrl, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))) }
            .onFailure { Files.createFile(archivoUrl) } // Windows: la carpeta %APPDATA% ya es privada del usuario
        Files.newOutputStream(archivoUrl).use { Properties().apply { setProperty("url", url) }.store(it, null) }
    }

    fun urlPublicada(): String? =
        runCatching { Files.newInputStream(archivoUrl).use { Properties().apply { load(it) }.getProperty("url") } }.getOrNull()

    fun liberar() {
        runCatching { Files.deleteIfExists(archivoUrl) }
        runCatching { bloqueo?.release() }
        runCatching { canal?.close() }
    }
}
