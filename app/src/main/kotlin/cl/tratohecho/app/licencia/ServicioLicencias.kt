package cl.tratohecho.app.licencia

import cl.tratohecho.app.config.Modo
import cl.tratohecho.app.config.PropiedadesTratohecho
import cl.tratohecho.app.persistencia.CotizacionRepositorioJdbc
import cl.tratohecho.core.application.PoliticaDeUso
import cl.tratohecho.core.domain.ReglaDeNegocioException
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import java.time.Clock

data class EstadoLicencia(
    val licenciada: Boolean,
    val licencia: Licencia?,
    val cotizacionesUsadas: Long,
    val limitePrueba: Int,
)

@Service
class ServicioLicencias(
    private val jdbc: JdbcClient,
    private val cotizaciones: CotizacionRepositorioJdbc,
    private val propiedades: PropiedadesTratohecho,
    private val reloj: Clock,
) : PoliticaDeUso {
    private val log = LoggerFactory.getLogger(javaClass)
    private val clavePublica by lazy { FormatoLicencia.clavePublica(propiedades.licencia.clavePublica) }

    fun licenciaActiva(): Licencia? {
        val texto =
            jdbc
                .sql("SELECT contenido FROM licencia WHERE id = 1")
                .query(String::class.java)
                .optional()
                .orElse(null) ?: return null
        return try {
            FormatoLicencia.verificar(texto, clavePublica)
        } catch (e: ReglaDeNegocioException) {
            log.warn("La licencia guardada no es válida: {}", e.message)
            null
        }
    }

    fun estado() =
        EstadoLicencia(
            licenciada = propiedades.modo == Modo.DEMO || licenciaActiva() != null,
            licencia = licenciaActiva(),
            cotizacionesUsadas = cotizaciones.contarFolios(),
            limitePrueba = propiedades.licencia.limitePrueba,
        )

    fun activar(texto: String): Licencia {
        val licencia = FormatoLicencia.verificar(texto, clavePublica)
        jdbc.sql("DELETE FROM licencia").update()
        jdbc
            .sql("INSERT INTO licencia (id, contenido, activada_en) VALUES (1, :contenido, :ahora)")
            .param("contenido", texto.trim())
            .param("ahora", reloj.millis())
            .update()
        return licencia
    }

    override fun contarCotizacionesCreadas(): Long = cotizaciones.contarFolios()

    override fun verificarPuedeCrearCotizacion(totalCreadas: Long) {
        if (propiedades.modo == Modo.DEMO || licenciaActiva() != null) return
        if (totalCreadas >= propiedades.licencia.limitePrueba) {
            throw ReglaDeNegocioException(
                "limite_prueba",
                "La versión de prueba permite ${propiedades.licencia.limitePrueba} cotizaciones. Activa tu licencia para seguir cotizando.",
            )
        }
    }
}
