package cl.tratohecho.app.web

import cl.tratohecho.core.domain.DatoInvalidoException
import cl.tratohecho.core.domain.NoEncontradoException
import cl.tratohecho.core.domain.ReglaDeNegocioException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/** Errores como Problem Details (RFC 9457) con un `codigo` estable que la interfaz puede interpretar. */
@RestControllerAdvice
class ManejoErrores {
    @ExceptionHandler(DatoInvalidoException::class)
    fun datoInvalido(e: DatoInvalidoException) =
        problema(HttpStatus.BAD_REQUEST, e.codigo, e.message).apply { setProperty("campo", e.campo) }

    @ExceptionHandler(ReglaDeNegocioException::class)
    fun reglaDeNegocio(e: ReglaDeNegocioException) = problema(HttpStatus.UNPROCESSABLE_CONTENT, e.codigo, e.message)

    @ExceptionHandler(NoEncontradoException::class)
    fun noEncontrado(e: NoEncontradoException) = problema(HttpStatus.NOT_FOUND, "no_encontrado", e.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validacion(e: MethodArgumentNotValidException) =
        problema(HttpStatus.BAD_REQUEST, "validacion", "Revisa los datos ingresados").apply {
            setProperty("errores", e.bindingResult.fieldErrors.map { mapOf("campo" to it.field, "mensaje" to it.defaultMessage) })
        }

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun formato(e: Exception) = problema(HttpStatus.BAD_REQUEST, "formato", "El formato de la solicitud no es válido")

    @ExceptionHandler(IllegalArgumentException::class, ArithmeticException::class)
    fun argumento(e: RuntimeException) = problema(HttpStatus.BAD_REQUEST, "dato_invalido", e.message)

    private fun problema(
        estado: HttpStatus,
        codigo: String,
        detalle: String?,
    ) = ProblemDetail.forStatusAndDetail(estado, detalle ?: estado.reasonPhrase).apply { setProperty("codigo", codigo) }
}
