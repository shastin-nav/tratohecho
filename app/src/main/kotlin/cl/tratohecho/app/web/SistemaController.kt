package cl.tratohecho.app.web

import cl.tratohecho.app.config.Modo
import cl.tratohecho.app.config.PropiedadesTratohecho
import cl.tratohecho.app.licencia.EstadoLicencia
import cl.tratohecho.app.licencia.ServicioLicencias
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SistemaDto(
    val modo: Modo,
    val version: String,
    val licencia: EstadoLicencia,
    val carpetaDatos: String?,
)

data class ActivacionEntrada(
    @field:NotBlank val clave: String = "",
)

@RestController
@RequestMapping("/api/sistema")
class SistemaController(
    private val propiedades: PropiedadesTratohecho,
    private val licencias: ServicioLicencias,
    private val build: ObjectProvider<BuildProperties>,
) {
    @GetMapping
    fun estado() =
        SistemaDto(
            modo = propiedades.modo,
            version = build.ifAvailable?.version ?: "desarrollo",
            licencia = licencias.estado(),
            carpetaDatos =
                propiedades.datos.directorio
                    .toString()
                    .takeIf { propiedades.modo == Modo.LOCAL },
        )

    @PostMapping("/licencia")
    fun activar(
        @Valid @RequestBody entrada: ActivacionEntrada,
    ): EstadoLicencia {
        licencias.activar(entrada.clave)
        return licencias.estado()
    }
}
