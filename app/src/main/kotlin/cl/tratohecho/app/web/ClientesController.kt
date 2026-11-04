package cl.tratohecho.app.web

import cl.tratohecho.core.application.ClienteServicio
import cl.tratohecho.core.application.CotizacionServicio
import cl.tratohecho.core.application.FiltroCotizaciones
import cl.tratohecho.core.application.Paginacion
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
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
import java.time.Clock
import java.time.LocalDate

@RestController
@RequestMapping("/api/clientes")
class ClientesController(
    private val clientes: ClienteServicio,
    private val cotizaciones: CotizacionServicio,
    private val reloj: Clock,
) {
    @GetMapping
    fun buscar(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") pagina: Int,
        @RequestParam(defaultValue = "20") tamano: Int,
    ) = clientes.buscar(q, Paginacion(pagina, tamano)).mapear(ClienteDto::de)

    @GetMapping("/{id}")
    fun obtener(
        @PathVariable id: Long,
    ) = ClienteDto.de(clientes.obtener(id))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun crear(
        @Valid @RequestBody entrada: ClienteEntrada,
    ) = ClienteDto.de(clientes.crear(entrada.aDominio()))

    @PutMapping("/{id}")
    fun actualizar(
        @PathVariable id: Long,
        @Valid @RequestBody entrada: ClienteEntrada,
    ) = ClienteDto.de(clientes.actualizar(id, entrada.aDominio()))

    /** Supresión (Ley 21.719): borra o anonimiza según tenga cotizaciones. */
    @DeleteMapping("/{id}")
    fun eliminar(
        @PathVariable id: Long,
    ) = mapOf("resultado" to clientes.eliminar(id).name)

    /** Acceso y portabilidad (Ley 21.719): todos los datos del cliente y sus cotizaciones en JSON. */
    @GetMapping("/{id}/exportar")
    fun exportar(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any>> {
        val cliente = clientes.obtener(id)
        val hoy = LocalDate.now(reloj)
        val suyas =
            cotizaciones
                .buscar(FiltroCotizaciones(clienteId = id, todasLasVersiones = true), Paginacion(0, 200))
                .items
                .map { CotizacionDto.de(it, hoy) }
        val cuerpo = mapOf("exportadoEl" to hoy.toString(), "cliente" to ClienteDto.de(cliente), "cotizaciones" to suyas)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cliente-$id.json\"")
            .body(cuerpo)
    }
}
