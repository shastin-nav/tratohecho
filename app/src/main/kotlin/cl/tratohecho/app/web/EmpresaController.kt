package cl.tratohecho.app.web

import cl.tratohecho.app.persistencia.EmpresaRepositorioJdbc
import cl.tratohecho.core.application.EmpresaServicio
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/empresa")
class EmpresaController(
    private val empresas: EmpresaServicio,
    private val repositorio: EmpresaRepositorioJdbc,
) {
    @GetMapping
    fun obtener() = EmpresaDto.de(empresas.obtener())

    @PutMapping
    fun actualizar(
        @Valid @RequestBody entrada: EmpresaDto,
    ) = EmpresaDto.de(empresas.actualizar(entrada.aDominio()))

    @GetMapping("/logo")
    fun logo(): ResponseEntity<ByteArray> {
        val contenido = empresas.logo() ?: return ResponseEntity.notFound().build()
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(repositorio.tipoLogo() ?: MediaType.IMAGE_PNG_VALUE))
            .body(contenido)
    }

    @PutMapping("/logo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun cambiarLogo(
        @RequestParam archivo: MultipartFile,
    ): EmpresaDto {
        empresas.cambiarLogo(archivo.bytes, archivo.contentType)
        return EmpresaDto.de(empresas.obtener())
    }

    @DeleteMapping("/logo")
    fun quitarLogo(): EmpresaDto {
        empresas.cambiarLogo(null, null)
        return EmpresaDto.de(empresas.obtener())
    }
}
