package cl.tratohecho.app

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/** La misma suite contra PostgreSQL (motor de la demo). Se omite si no hay Docker disponible; en CI sí corre. */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("pgtest")
class EscenarioPostgresTest : EscenarioCotizacion() {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
