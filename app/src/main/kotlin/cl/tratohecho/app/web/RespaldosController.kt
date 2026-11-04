package cl.tratohecho.app.web

import cl.tratohecho.app.respaldo.ServicioRespaldos
import org.springframework.core.io.FileSystemResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import kotlin.io.path.name

@RestController
@RequestMapping("/api/respaldos")
class RespaldosController(
    private val respaldos: ServicioRespaldos,
) {
    @GetMapping
    fun listar() = respaldos.listar()

    @PostMapping
    fun crear() = mapOf("nombre" to respaldos.crear().name)

    @GetMapping("/{nombre}")
    fun descargar(
        @PathVariable nombre: String,
    ): ResponseEntity<FileSystemResource> {
        val archivo = respaldos.archivo(nombre)
        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(archivo.name)
                    .build()
                    .toString(),
            ).body(FileSystemResource(archivo))
    }

    @PostMapping("/restaurar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun restaurar(
        @RequestParam archivo: MultipartFile,
    ) = mapOf("respaldoPrevio" to respaldos.restaurar(archivo.bytes).name)
}
