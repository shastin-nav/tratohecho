package cl.tratohecho.app.web

import cl.tratohecho.app.pdf.GeneradorPdf
import cl.tratohecho.core.application.CotizacionServicio
import cl.tratohecho.core.application.FiltroCotizaciones
import cl.tratohecho.core.application.Paginacion
import cl.tratohecho.core.domain.Aceptacion
import cl.tratohecho.core.domain.CalculadoraCotizacion
import cl.tratohecho.core.domain.EstadoCotizacion
import cl.tratohecho.core.domain.Porcentaje
import jakarta.validation.Valid
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.LocalDate

@RestController
@RequestMapping("/api/cotizaciones")
class CotizacionesController(
    private val servicio: CotizacionServicio,
    private val pdf: GeneradorPdf,
    private val reloj: Clock,
) {
    private fun hoy() = LocalDate.now(reloj)

    @GetMapping
    fun buscar(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) estado: EstadoCotizacion?,
        @RequestParam(required = false) clienteId: Long?,
        @RequestParam(required = false) vendedor: String?,
        @RequestParam(defaultValue = "false") todasLasVersiones: Boolean,
        @RequestParam(defaultValue = "0") pagina: Int,
        @RequestParam(defaultValue = "20") tamano: Int,
    ) = servicio
        .buscar(FiltroCotizaciones(q, estado, clienteId, vendedor, todasLasVersiones), Paginacion(pagina, tamano))
        .mapear { CotizacionDto.de(it, hoy()) }

    @GetMapping("/resumen")
    fun resumen() = servicio.resumen()

    @GetMapping("/plantilla")
    fun plantilla() = servicio.plantilla()

    /** Cálculo de totales para el editor: el servidor es la única fuente de verdad del IVA y los redondeos. */
    @PostMapping("/calcular")
    fun calcular(
        @Valid @RequestBody entrada: CalculoEntrada,
    ) = TotalesDto.de(
        CalculadoraCotizacion.calcular(
            entrada.lineas.map { it.aDominio() },
            entrada.descuentoGlobal.aDominio(),
            entrada.tasaIva?.let(Porcentaje::de) ?: Porcentaje.IVA_CHILE,
        ),
    )

    @GetMapping("/{id}")
    fun obtener(
        @PathVariable id: Long,
    ) = CotizacionDto.de(servicio.obtener(id), hoy())

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun crear(
        @Valid @RequestBody entrada: CotizacionEntrada,
    ) = CotizacionDto.de(servicio.crear(entrada.clienteId!!, entrada.contenido()), hoy())

    @PutMapping("/{id}")
    fun actualizar(
        @PathVariable id: Long,
        @Valid @RequestBody entrada: CotizacionEntrada,
    ) = CotizacionDto.de(servicio.actualizar(id, entrada.clienteId!!, entrada.contenido()), hoy())

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun eliminar(
        @PathVariable id: Long,
    ) = servicio.eliminarBorrador(id)

    @PostMapping("/{id}/enviar")
    fun enviar(
        @PathVariable id: Long,
    ) = CotizacionDto.de(servicio.enviar(id), hoy())

    @PostMapping("/{id}/aceptar")
    fun aceptar(
        @PathVariable id: Long,
        @Valid @RequestBody entrada: AceptacionEntrada,
    ) = CotizacionDto.de(
        servicio.aceptar(id, Aceptacion(entrada.fecha ?: hoy(), entrada.nombre, entrada.medio!!, entrada.detalle)),
        hoy(),
    )

    @PostMapping("/{id}/rechazar")
    fun rechazar(
        @PathVariable id: Long,
        @Valid @RequestBody(required = false) entrada: RechazoEntrada?,
    ) = CotizacionDto.de(servicio.rechazar(id, entrada?.motivo), hoy())

    @PostMapping("/{id}/nueva-version")
    @ResponseStatus(HttpStatus.CREATED)
    fun nuevaVersion(
        @PathVariable id: Long,
    ) = CotizacionDto.de(servicio.nuevaVersion(id), hoy())

    @PostMapping("/{id}/duplicar")
    @ResponseStatus(HttpStatus.CREATED)
    fun duplicar(
        @PathVariable id: Long,
    ) = CotizacionDto.de(servicio.duplicar(id), hoy())

    @GetMapping("/{id}/historial")
    fun historial(
        @PathVariable id: Long,
    ) = servicio.historial(id).map(EventoDto::de)

    @GetMapping("/{id}/versiones")
    fun versiones(
        @PathVariable id: Long,
    ) = servicio.versiones(servicio.obtener(id).folio).map { CotizacionDto.de(it, hoy()) }

    @GetMapping("/{id}/pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun pdf(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "false") descargar: Boolean,
    ): ResponseEntity<ByteArray> {
        val cotizacion = servicio.obtener(id)
        val nombre = "Cotizacion-${cotizacion.folio}" + (if (cotizacion.version > 1) "-v${cotizacion.version}" else "") + ".pdf"
        val disposicion = if (descargar) ContentDisposition.attachment() else ContentDisposition.inline()
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.filename(nombre, StandardCharsets.UTF_8).build().toString())
            .body(pdf.generar(cotizacion))
    }
}
