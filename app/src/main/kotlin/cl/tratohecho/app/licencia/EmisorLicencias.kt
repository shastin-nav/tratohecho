package cl.tratohecho.app.licencia

import java.time.LocalDate
import kotlin.system.exitProcess

/**
 * Herramienta del vendedor (no se usa dentro de la app):
 *   generar-claves                          → imprime un par de claves nuevo
 *   emitir <id> <titular> <tipo> [rut]      → firma una licencia con TRATOHECHO_CLAVE_PRIVADA
 * Ejecutar con: ./gradlew :app:emitirLicencia --args="emitir TH-0001 'Ferretería Los Andes SpA' INDIVIDUAL 76086428-5"
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "generar-claves" -> {
            val (publica, privada) = FormatoLicencia.nuevoParDeClaves()
            println("Clave pública (va en application.yml / TRATOHECHO_CLAVE_PUBLICA):\n$publica\n")
            println("Clave privada (guárdala fuera del repositorio; NUNCA la publiques):\n$privada")
        }

        "emitir" -> {
            if (args.size < 4) uso()
            val privada = System.getenv("TRATOHECHO_CLAVE_PRIVADA") ?: error("Define TRATOHECHO_CLAVE_PRIVADA")
            val licencia =
                Licencia(
                    id = args[1],
                    titular = args[2],
                    tipo = TipoLicencia.valueOf(args[3].uppercase()),
                    rut = args.getOrNull(4),
                    emitida = LocalDate.now(),
                )
            println(FormatoLicencia.firmar(licencia, FormatoLicencia.clavePrivada(privada)))
        }

        else -> uso()
    }
}

private fun uso(): Nothing {
    System.err.println("Uso: generar-claves | emitir <id> <titular> <INDIVIDUAL|EMPRESA> [rut]")
    exitProcess(2)
}
