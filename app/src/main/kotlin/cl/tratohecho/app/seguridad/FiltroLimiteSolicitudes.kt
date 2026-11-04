package cl.tratohecho.app.seguridad

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Límite simple por IP y minuto para la demo pública (ventana fija; suficiente para frenar abuso casual). */
class FiltroLimiteSolicitudes(
    private val porMinuto: Int,
    private val reloj: Clock,
) : OncePerRequestFilter() {
    private val ventanas = ConcurrentHashMap<String, Pair<Long, AtomicInteger>>()

    override fun shouldNotFilter(request: HttpServletRequest) = !request.requestURI.startsWith("/api/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val minuto = reloj.millis() / 60_000
        if (ventanas.size > 10_000) ventanas.entries.removeIf { it.value.first != minuto }
        val ventana =
            ventanas.compute(request.remoteAddr) { _, v ->
                if (v == null ||
                    v.first != minuto
                ) {
                    minuto to AtomicInteger()
                } else {
                    v
                }
            }!!
        if (ventana.second.incrementAndGet() > porMinuto) {
            response.status = 429
            response.setHeader("Retry-After", "60")
            response.contentType = "application/problem+json;charset=UTF-8"
            response.writer.write(
                """{"status":429,"codigo":"demasiadas_solicitudes","detail":"Demasiadas solicitudes; espera un minuto"}""",
            )
            return
        }
        chain.doFilter(request, response)
    }
}
