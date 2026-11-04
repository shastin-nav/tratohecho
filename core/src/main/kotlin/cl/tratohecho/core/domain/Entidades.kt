package cl.tratohecho.core.domain

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

internal fun validarEmail(email: String?) {
    requerir(email.isNullOrBlank() || EMAIL.matches(email), "email") { "El correo \"$email\" no es válido" }
}

data class Cliente(
    val id: Long? = null,
    /** Opcional: una persona natural puede no querer entregar su RUT para una cotización. */
    val rut: Rut? = null,
    val razonSocial: String,
    val giro: String? = null,
    val contacto: String? = null,
    val email: String? = null,
    val telefono: String? = null,
    val direccion: String? = null,
    val comuna: String? = null,
    val ciudad: String? = null,
    val notas: String? = null,
) {
    init {
        requerir(razonSocial.isNotBlank(), "razonSocial") { "El nombre o razón social es obligatorio" }
        validarEmail(email)
    }

    fun datosParaCotizacion(): DatosCliente =
        DatosCliente(
            razonSocial = razonSocial,
            rut = rut?.formateado(),
            giro = giro,
            direccion = listOfNotNull(direccion, comuna, ciudad).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
            contacto = contacto,
            email = email,
            telefono = telefono,
        )

    companion object {
        const val NOMBRE_ANONIMIZADO = "Cliente eliminado"
        val DATOS_ANONIMIZADOS = DatosCliente(NOMBRE_ANONIMIZADO, null, null, null, null, null, null)
    }
}

data class ItemCatalogo(
    val id: Long? = null,
    val codigo: String? = null,
    val nombre: String,
    val descripcion: String? = null,
    val unidad: String? = null,
    /** Precio neto (sin IVA) en pesos. */
    val precioNeto: Long,
    val afectoIva: Boolean = true,
    val activo: Boolean = true,
) {
    init {
        requerir(nombre.isNotBlank(), "nombre") { "El nombre del producto o servicio es obligatorio" }
        requerir(precioNeto >= 0, "precioNeto") { "El precio no puede ser negativo" }
    }
}

data class Empresa(
    val razonSocial: String,
    val rut: Rut? = null,
    val giro: String? = null,
    val direccion: String? = null,
    val comuna: String? = null,
    val ciudad: String? = null,
    val telefono: String? = null,
    val email: String? = null,
    val sitioWeb: String? = null,
    val tasaIva: Porcentaje = Porcentaje.IVA_CHILE,
    val diasValidez: Int = 15,
    val formaPago: String? = null,
    val plazoEntrega: String? = null,
    val garantia: String? = null,
    val condiciones: String? = null,
    val tieneLogo: Boolean = false,
) {
    init {
        requerir(razonSocial.isNotBlank(), "razonSocial") { "La razón social es obligatoria" }
        requerir(diasValidez in 1..365, "diasValidez") { "La validez debe estar entre 1 y 365 días" }
        validarEmail(email)
    }

    companion object {
        val POR_DEFECTO = Empresa(razonSocial = "Mi empresa")
    }
}
