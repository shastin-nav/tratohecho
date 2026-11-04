package cl.tratohecho.app.seguridad

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/** Cabeceras de seguridad básicas para todas las respuestas. */
class FiltroCabeceras : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("X-Frame-Options", "SAMEORIGIN")
        response.setHeader("Referrer-Policy", "no-referrer")
        if (!request.requestURI.startsWith("/api/docs")) {
            response.setHeader(
                "Content-Security-Policy",
                "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; frame-src 'self' blob:; object-src 'none'",
            )
        }
        chain.doFilter(request, response)
    }
}
