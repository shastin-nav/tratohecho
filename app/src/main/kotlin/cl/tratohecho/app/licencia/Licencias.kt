package cl.tratohecho.app.licencia

import cl.tratohecho.core.domain.ReglaDeNegocioException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDate
import java.util.Base64

enum class TipoLicencia { INDIVIDUAL, EMPRESA }

/** Licencia perpetua firmada con Ed25519: se verifica sin conexión ni servidor (ver docs/adr/0006-licencias.md). */
data class Licencia(
    val id: String,
    val titular: String,
    val rut: String?,
    val tipo: TipoLicencia,
    val emitida: LocalDate,
) {
    fun aTextoPlano(): String =
        buildString {
            appendLine("id=$id")
            appendLine("titular=$titular")
            rut?.let { appendLine("rut=$it") }
            appendLine("tipo=${tipo.name}")
            append("emitida=$emitida")
        }

    companion object {
        fun desdeTextoPlano(texto: String): Licencia {
            val campos =
                texto
                    .lines()
                    .filter { "=" in it }
                    .associate { it.substringBefore("=") to it.substringAfter("=") }
            return Licencia(
                id = campos["id"] ?: error("falta id"),
                titular = campos["titular"] ?: error("falta titular"),
                rut = campos["rut"],
                tipo = TipoLicencia.valueOf(campos["tipo"] ?: error("falta tipo")),
                emitida = LocalDate.parse(campos["emitida"] ?: error("falta emitida")),
            )
        }
    }
}

object FormatoLicencia {
    private val codificador = Base64.getUrlEncoder().withoutPadding()
    private val decodificador = Base64.getUrlDecoder()

    fun firmar(
        licencia: Licencia,
        clavePrivada: PrivateKey,
    ): String {
        val contenido = licencia.aTextoPlano().toByteArray()
        val firma =
            Signature.getInstance("Ed25519").apply { initSign(clavePrivada) }.run {
                update(contenido)
                sign()
            }
        return "TH1." + codificador.encodeToString(contenido) + "." + codificador.encodeToString(firma)
    }

    fun verificar(
        textoLicencia: String,
        clavePublica: PublicKey,
    ): Licencia {
        val invalida = ReglaDeNegocioException("licencia_invalida", "La clave de licencia no es válida")
        val partes = textoLicencia.trim().replace(Regex("\\s"), "").split(".")
        if (partes.size != 3 || partes[0] != "TH1") throw invalida
        val (contenido, firma) =
            try {
                decodificador.decode(partes[1]) to decodificador.decode(partes[2])
            } catch (_: IllegalArgumentException) {
                throw invalida
            }
        val valida =
            try {
                Signature.getInstance("Ed25519").run {
                    initVerify(clavePublica)
                    update(contenido)
                    verify(firma)
                }
            } catch (_: java.security.SignatureException) {
                false
            }
        if (!valida) throw invalida
        return try {
            Licencia.desdeTextoPlano(String(contenido))
        } catch (_: RuntimeException) {
            throw invalida
        }
    }

    fun clavePublica(base64: String): PublicKey =
        KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64)))

    fun clavePrivada(base64: String): PrivateKey =
        KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)))

    fun nuevoParDeClaves(): Pair<String, String> {
        val par = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val b64 = Base64.getEncoder()
        return b64.encodeToString(par.public.encoded) to b64.encodeToString(par.private.encoded)
    }
}
