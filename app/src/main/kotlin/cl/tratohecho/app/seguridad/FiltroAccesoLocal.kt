package cl.tratohecho.app.seguridad

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URI
import java.security.MessageDigest

/**
 * Protege la instalación local (ver docs/adr/0003-seguridad-modo-local.md):
 *  - Solo acepta el encabezado Host 127.0.0.1/localhost → bloquea ataques de DNS rebinding.
 *  - El lanzador abre el navegador con ?t=<token>; el token se canjea por una cookie HttpOnly + SameSite=Strict.
 *  - La API exige esa cookie; las peticiones que cambian datos además deben venir del mismo origen.
 * Así, ni otras páginas web abiertas en el navegador ni otros usuarios del equipo pueden usar la API.
 */
class FiltroAccesoLocal(
    private val token: String,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (nombreHost(request.getHeader("Host") ?: request.serverName) !in HOSTS_PERMITIDOS) {
            rechazar(response, HttpStatus.FORBIDDEN, "host_no_permitido", "Solo se permite el acceso desde este equipo")
            return
        }

        val tokenUrl = request.getParameter(PARAMETRO)
        if (tokenUrl != null && request.method == "GET") {
            if (iguales(tokenUrl, token)) {
                val cookie =
                    ResponseCookie
                        .from(COOKIE, token)
                        .httpOnly(true)
                        .path("/")
                        .sameSite("Strict")
                        .build()
                response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
            }
            response.sendRedirect(request.requestURI) // quita el token de la barra de direcciones y del historial
            return
        }

        if (request.requestURI.startsWith("/api/")) {
            val cookie = request.cookies?.firstOrNull { it.name == COOKIE }?.value
            if (cookie == null || !iguales(cookie, token)) {
                rechazar(response, HttpStatus.UNAUTHORIZED, "sesion_local", "Abre Tratohecho desde el ícono de la aplicación")
                return
            }
            if (request.method !in METODOS_SEGUROS && !mismoOrigen(request)) {
                rechazar(response, HttpStatus.FORBIDDEN, "origen_no_permitido", "Solicitud de otro origen bloqueada")
                return
            }
        }
        chain.doFilter(request, response)
    }

    private fun mismoOrigen(request: HttpServletRequest): Boolean {
        val origen = request.getHeader("Origin") ?: return true // navegadores siempre lo envían en POST cross-site
        return runCatching { URI(origen).host?.lowercase() in HOSTS_PERMITIDOS }.getOrDefault(false)
    }

    /** "127.0.0.1:8080" → "127.0.0.1"; "[::1]:8080" → "[::1]". */
    private fun nombreHost(host: String): String {
        val h = host.trim().lowercase()
        return if (h.startsWith("[")) h.substringBefore("]") + "]" else h.substringBefore(':')
    }

    private fun iguales(
        a: String,
        b: String,
    ) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private fun rechazar(
        response: HttpServletResponse,
        estado: HttpStatus,
        codigo: String,
        mensaje: String,
    ) {
        response.status = estado.value()
        response.contentType = "application/problem+json;charset=UTF-8"
        response.writer.write("""{"status":${estado.value()},"codigo":"$codigo","detail":"$mensaje"}""")
    }

    companion object {
        const val PARAMETRO = "t"
        const val COOKIE = "th_sesion"
        private val HOSTS_PERMITIDOS = setOf("127.0.0.1", "localhost", "[::1]")
        private val METODOS_SEGUROS = setOf("GET", "HEAD", "OPTIONS")
    }
}
