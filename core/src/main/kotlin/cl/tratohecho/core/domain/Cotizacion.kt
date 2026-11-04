package cl.tratohecho.core.domain

import java.time.Instant
import java.time.LocalDate

enum class EstadoCotizacion {
    BORRADOR,
    ENVIADA,
    ACEPTADA,
    RECHAZADA,
    VENCIDA,

    /** Una versión enviada que fue sustituida por una versión posterior (contraoferta). */
    REEMPLAZADA,
    ;

    val esFinal: Boolean get() = this in setOf(ACEPTADA, RECHAZADA, VENCIDA, REEMPLAZADA)

    fun puedePasarA(destino: EstadoCotizacion): Boolean = destino in (TRANSICIONES[this] ?: emptySet())

    private companion object {
        val TRANSICIONES =
            mapOf(
                BORRADOR to setOf(ENVIADA),
                ENVIADA to setOf(ACEPTADA, RECHAZADA, VENCIDA, REEMPLAZADA),
                VENCIDA to setOf(REEMPLAZADA),
            )
    }
}

enum class MedioAceptacion { CORREO, FIRMA, VERBAL, OTRO }

data class Aceptacion(
    val fecha: LocalDate,
    val nombre: String,
    val medio: MedioAceptacion,
    val detalle: String? = null,
) {
    init {
        requerir(nombre.isNotBlank(), "aceptacion.nombre") { "Indica quién aceptó la cotización" }
    }
}

/** Copia de los datos del cliente al momento de cotizar: el documento enviado no cambia aunque se edite el cliente. */
data class DatosCliente(
    val razonSocial: String,
    val rut: String?,
    val giro: String?,
    val direccion: String?,
    val contacto: String?,
    val email: String?,
    val telefono: String?,
)

data class Cotizacion(
    val id: Long? = null,
    val folio: Long,
    val version: Int = 1,
    val estado: EstadoCotizacion = EstadoCotizacion.BORRADOR,
    val clienteId: Long,
    val cliente: DatosCliente,
    val vendedor: String? = null,
    val fechaEmision: LocalDate,
    val fechaValidez: LocalDate,
    val moneda: Moneda = Moneda.CLP,
    val tasaIva: Porcentaje = Porcentaje.IVA_CHILE,
    val lineas: List<LineaCotizacion> = emptyList(),
    val descuentoGlobal: DescuentoGlobal = DescuentoGlobal.Ninguno,
    val formaPago: String? = null,
    val plazoEntrega: String? = null,
    val garantia: String? = null,
    val observaciones: String? = null,
    val aceptacion: Aceptacion? = null,
    val motivoRechazo: String? = null,
    val enviadaEn: Instant? = null,
    val creadaEn: Instant,
    val actualizadaEn: Instant,
) {
    init {
        requerir(!fechaValidez.isBefore(fechaEmision), "fechaValidez") { "La validez no puede ser anterior a la fecha de emisión" }
        requerir(version >= 1, "version") { "La versión debe ser 1 o mayor" }
    }

    val totales: Totales by lazy { CalculadoraCotizacion.calcular(lineas, descuentoGlobal, tasaIva) }

    val editable: Boolean get() = estado == EstadoCotizacion.BORRADOR

    val numero: String get() = if (version == 1) "N° $folio" else "N° $folio-v$version"

    /** Estado considerando el paso del tiempo: una ENVIADA con validez expirada se considera VENCIDA. */
    fun estadoAl(hoy: LocalDate): EstadoCotizacion =
        if (estado == EstadoCotizacion.ENVIADA && hoy.isAfter(fechaValidez)) EstadoCotizacion.VENCIDA else estado

    fun conContenido(
        cliente: DatosCliente,
        clienteId: Long,
        cambios: ContenidoCotizacion,
        ahora: Instant,
    ): Cotizacion {
        exigirEditable()
        return copy(
            clienteId = clienteId,
            cliente = cliente,
            vendedor = cambios.vendedor,
            fechaEmision = cambios.fechaEmision,
            fechaValidez = cambios.fechaValidez,
            lineas = cambios.lineas,
            descuentoGlobal = cambios.descuentoGlobal,
            formaPago = cambios.formaPago,
            plazoEntrega = cambios.plazoEntrega,
            garantia = cambios.garantia,
            observaciones = cambios.observaciones,
            actualizadaEn = ahora,
        ).also { it.totales } // valida el descuento global contra el subtotal
    }

    fun enviar(
        hoy: LocalDate,
        ahora: Instant,
    ): Cotizacion {
        exigirTransicion(EstadoCotizacion.ENVIADA)
        if (lineas.isEmpty()) throw ReglaDeNegocioException("sin_lineas", "La cotización no tiene líneas")
        if (fechaValidez.isBefore(hoy)) {
            throw ReglaDeNegocioException("validez_expirada", "La fecha de validez ya pasó; actualízala antes de enviar")
        }
        return copy(estado = EstadoCotizacion.ENVIADA, enviadaEn = ahora, actualizadaEn = ahora)
    }

    fun aceptar(
        aceptacion: Aceptacion,
        hoy: LocalDate,
        ahora: Instant,
    ): Cotizacion {
        if (estadoAl(hoy) == EstadoCotizacion.VENCIDA) {
            throw ReglaDeNegocioException(
                "cotizacion_vencida",
                "La cotización venció el $fechaValidez; crea una nueva versión con validez actualizada",
            )
        }
        exigirTransicion(EstadoCotizacion.ACEPTADA)
        return copy(estado = EstadoCotizacion.ACEPTADA, aceptacion = aceptacion, actualizadaEn = ahora)
    }

    fun rechazar(
        motivo: String?,
        ahora: Instant,
    ): Cotizacion {
        exigirTransicion(EstadoCotizacion.RECHAZADA)
        return copy(estado = EstadoCotizacion.RECHAZADA, motivoRechazo = motivo?.takeIf { it.isNotBlank() }, actualizadaEn = ahora)
    }

    fun vencer(
        hoy: LocalDate,
        ahora: Instant,
    ): Cotizacion {
        check(estadoAl(hoy) == EstadoCotizacion.VENCIDA && estado == EstadoCotizacion.ENVIADA) { "La cotización $numero no está vencida" }
        return copy(estado = EstadoCotizacion.VENCIDA, actualizadaEn = ahora)
    }

    /**
     * Modificar una oferta ya enviada constituye una contraoferta (Código de Comercio): la versión enviada queda
     * inmutable como REEMPLAZADA y se crea una nueva versión en borrador con el mismo folio.
     */
    fun nuevaVersion(
        hoy: LocalDate,
        diasValidez: Int,
        ahora: Instant,
    ): Pair<Cotizacion, Cotizacion> {
        exigirTransicion(EstadoCotizacion.REEMPLAZADA)
        val reemplazada = copy(estado = EstadoCotizacion.REEMPLAZADA, actualizadaEn = ahora)
        val nueva =
            copy(
                id = null,
                version = version + 1,
                estado = EstadoCotizacion.BORRADOR,
                fechaEmision = hoy,
                fechaValidez = maxOf(fechaValidez, hoy.plusDays(diasValidez.toLong())),
                aceptacion = null,
                motivoRechazo = null,
                enviadaEn = null,
                creadaEn = ahora,
                actualizadaEn = ahora,
            )
        return reemplazada to nueva
    }

    /** Copia como cotización nueva (otro folio), útil para clientes o trabajos parecidos. */
    fun duplicar(
        nuevoFolio: Long,
        hoy: LocalDate,
        diasValidez: Int,
        ahora: Instant,
    ): Cotizacion =
        copy(
            id = null,
            folio = nuevoFolio,
            version = 1,
            estado = EstadoCotizacion.BORRADOR,
            fechaEmision = hoy,
            fechaValidez = hoy.plusDays(diasValidez.toLong()),
            aceptacion = null,
            motivoRechazo = null,
            enviadaEn = null,
            creadaEn = ahora,
            actualizadaEn = ahora,
        )

    private fun exigirEditable() {
        if (!editable) {
            throw ReglaDeNegocioException(
                "no_editable",
                "La cotización $numero está ${estado.name.lowercase()}; para modificarla crea una nueva versión",
            )
        }
    }

    private fun exigirTransicion(destino: EstadoCotizacion) {
        if (!estado.puedePasarA(destino)) {
            throw ReglaDeNegocioException(
                "transicion_invalida",
                "No se puede pasar de ${estado.name.lowercase()} a ${destino.name.lowercase()}",
            )
        }
    }
}

/** Datos editables de una cotización en borrador. */
data class ContenidoCotizacion(
    val vendedor: String?,
    val fechaEmision: LocalDate,
    val fechaValidez: LocalDate,
    val lineas: List<LineaCotizacion>,
    val descuentoGlobal: DescuentoGlobal,
    val formaPago: String?,
    val plazoEntrega: String?,
    val garantia: String?,
    val observaciones: String?,
)

data class EventoCotizacion(
    val cotizacionId: Long,
    val desde: EstadoCotizacion?,
    val hacia: EstadoCotizacion,
    val ocurridoEn: Instant,
    val detalle: String? = null,
)
