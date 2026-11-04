package cl.tratohecho.core.domain

/** Violación de una regla de negocio. `codigo` es estable y lo consume la API/UI. */
open class ReglaDeNegocioException(
    val codigo: String,
    mensaje: String,
) : RuntimeException(mensaje)

class DatoInvalidoException(
    val campo: String,
    mensaje: String,
) : ReglaDeNegocioException("dato_invalido", mensaje)

class NoEncontradoException(
    entidad: String,
    id: Any,
) : RuntimeException("$entidad $id no existe")

internal fun requerir(
    condicion: Boolean,
    campo: String,
    mensaje: () -> String,
) {
    if (!condicion) throw DatoInvalidoException(campo, mensaje())
}
