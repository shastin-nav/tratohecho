package cl.tratohecho.app.config

import cl.tratohecho.core.application.CotizacionServicio
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Marca como VENCIDAS las cotizaciones enviadas cuya validez expiró: al iniciar y cada día a las 00:05 (hora de Chile). */
@Component
class Tareas(
    private val cotizaciones: CotizacionServicio,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun alIniciar() = marcarVencidas()

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Santiago")
    fun marcarVencidas() {
        val n = cotizaciones.marcarVencidas()
        if (n > 0) log.info("{} cotizaciones marcadas como vencidas", n)
    }
}
