package cl.tratohecho.app.pdf

import cl.tratohecho.app.config.Modo
import cl.tratohecho.app.config.PropiedadesTratohecho
import cl.tratohecho.app.licencia.ServicioLicencias
import cl.tratohecho.app.persistencia.EmpresaRepositorioJdbc
import cl.tratohecho.core.domain.Clp
import cl.tratohecho.core.domain.Cotizacion
import cl.tratohecho.core.domain.DescuentoGlobal
import cl.tratohecho.core.domain.EstadoCotizacion
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.springframework.stereotype.Component
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

/** HTML/CSS (Thymeleaf en modo XML, para producir XHTML válido) → PDF con openhtmltopdf. */
@Component
class GeneradorPdf(
    private val empresas: EmpresaRepositorioJdbc,
    private val licencias: ServicioLicencias,
    private val propiedades: PropiedadesTratohecho,
) {
    private val motor =
        TemplateEngine().apply {
            setTemplateResolver(
                ClassLoaderTemplateResolver().apply {
                    prefix = "templates/pdf/"
                    suffix = ".xhtml"
                    templateMode = TemplateMode.XML
                    characterEncoding = "UTF-8"
                    isCacheable = true
                },
            )
        }

    private val fecha = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CL"))

    fun generar(cotizacion: Cotizacion): ByteArray {
        val html = motor.process("cotizacion", Context(Locale.forLanguageTag("es-CL"), variables(cotizacion)))
        return ByteArrayOutputStream().use { salida ->
            PdfRendererBuilder()
                .useFastMode()
                .withHtmlContent(html, null)
                .withProducer("Tratohecho")
                .toStream(salida)
                .run()
            salida.toByteArray()
        }
    }

    /** Expuesto para pruebas: el HTML intermedio que se convierte en PDF. */
    fun variables(c: Cotizacion): Map<String, Any?> {
        val empresa = empresas.obtener()
        val logo =
            empresas.obtenerLogo()?.let {
                "data:${empresas.tipoLogo() ?: "image/png"};base64," +
                    Base64.getEncoder().encodeToString(it)
            }
        val t = c.totales
        val marcaDeAgua =
            when {
                propiedades.modo == Modo.DEMO -> "DEMO"
                licencias.licenciaActiva() == null -> "VERSIÓN DE PRUEBA"
                c.estado == EstadoCotizacion.BORRADOR -> "BORRADOR"
                else -> null
            }
        val descuentoGlobal =
            when (val d = c.descuentoGlobal) {
                DescuentoGlobal.Ninguno -> null
                is DescuentoGlobal.PorPorcentaje -> "Descuento ${d.porcentaje}"
                is DescuentoGlobal.PorMonto -> "Descuento"
            }
        return mapOf(
            "c" to c,
            "empresa" to empresa,
            "empresaRut" to empresa.rut?.formateado(),
            "empresaDireccion" to listOfNotNull(empresa.direccion, empresa.comuna, empresa.ciudad).joinToString(", ").ifBlank { null },
            "logo" to logo,
            "clienteContacto" to listOfNotNull(c.cliente.contacto, c.cliente.telefono, c.cliente.email).joinToString(" · ").ifBlank { "—" },
            "marcaDeAgua" to marcaDeAgua,
            "fechaEmision" to c.fechaEmision.format(fecha),
            "fechaValidez" to c.fechaValidez.format(fecha),
            "lineas" to
                c.lineas.zip(t.lineas).map { (l, tl) ->
                    mapOf(
                        "codigo" to l.codigo,
                        "descripcion" to l.descripcion,
                        "cantidad" to l.cantidad.toString().replace('.', ','),
                        "unidad" to l.unidad,
                        "precio" to Clp.formatear(l.precioUnitario),
                        "descuento" to if (l.descuento.esCero) null else l.descuento.toString(),
                        "exenta" to !l.afectaIva,
                        "subtotal" to Clp.formatear(tl.subtotal),
                    )
                },
            "hayDescuentosLinea" to c.lineas.any { !it.descuento.esCero },
            "hayExento" to (t.subtotalExento > 0),
            "subtotal" to Clp.formatear(t.subtotalAfecto + t.subtotalExento),
            "descuentoGlobalTexto" to descuentoGlobal,
            "descuentoGlobal" to Clp.formatear(t.descuentoGlobal),
            "neto" to Clp.formatear(t.netoAfecto),
            "exento" to Clp.formatear(t.exento),
            "tasaIva" to c.tasaIva.toString(),
            "iva" to Clp.formatear(t.iva),
            "total" to Clp.formatear(t.total),
        )
    }
}
