package cl.tratohecho.app.web

import cl.tratohecho.core.application.Pagina
import cl.tratohecho.core.domain.Aceptacion
import cl.tratohecho.core.domain.Cantidad
import cl.tratohecho.core.domain.Cliente
import cl.tratohecho.core.domain.ContenidoCotizacion
import cl.tratohecho.core.domain.Cotizacion
import cl.tratohecho.core.domain.DatosCliente
import cl.tratohecho.core.domain.DescuentoGlobal
import cl.tratohecho.core.domain.Empresa
import cl.tratohecho.core.domain.EstadoCotizacion
import cl.tratohecho.core.domain.EventoCotizacion
import cl.tratohecho.core.domain.ItemCatalogo
import cl.tratohecho.core.domain.LineaCotizacion
import cl.tratohecho.core.domain.MedioAceptacion
import cl.tratohecho.core.domain.Porcentaje
import cl.tratohecho.core.domain.Rut
import cl.tratohecho.core.domain.Totales
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class PaginaDto<T>(
    val items: List<T>,
    val total: Long,
    val pagina: Int,
    val tamano: Int,
)

fun <T, R> Pagina<T>.mapear(f: (T) -> R) = PaginaDto(items.map(f), total, pagina, tamano)

private fun String?.rutOpcional(): Rut? = this?.takeIf { it.isNotBlank() }?.let(Rut::parse)

// ---------- Clientes ----------

data class ClienteEntrada(
    val rut: String? = null,
    @field:NotBlank @field:Size(max = 200) val razonSocial: String = "",
    @field:Size(max = 200) val giro: String? = null,
    @field:Size(max = 200) val contacto: String? = null,
    @field:Size(max = 200) val email: String? = null,
    @field:Size(max = 50) val telefono: String? = null,
    @field:Size(max = 300) val direccion: String? = null,
    @field:Size(max = 100) val comuna: String? = null,
    @field:Size(max = 100) val ciudad: String? = null,
    @field:Size(max = 5000) val notas: String? = null,
) {
    fun aDominio() = Cliente(null, rut.rutOpcional(), razonSocial, giro, contacto, email, telefono, direccion, comuna, ciudad, notas)
}

data class ClienteDto(
    val id: Long,
    val rut: String?,
    val razonSocial: String,
    val giro: String?,
    val contacto: String?,
    val email: String?,
    val telefono: String?,
    val direccion: String?,
    val comuna: String?,
    val ciudad: String?,
    val notas: String?,
) {
    companion object {
        fun de(c: Cliente) =
            ClienteDto(
                c.id!!,
                c.rut?.formateado(),
                c.razonSocial,
                c.giro,
                c.contacto,
                c.email,
                c.telefono,
                c.direccion,
                c.comuna,
                c.ciudad,
                c.notas,
            )
    }
}

// ---------- Catálogo ----------

data class ItemEntrada(
    @field:Size(max = 50) val codigo: String? = null,
    @field:NotBlank @field:Size(max = 200) val nombre: String = "",
    @field:Size(max = 5000) val descripcion: String? = null,
    @field:Size(max = 30) val unidad: String? = null,
    @field:NotNull @field:PositiveOrZero val precioNeto: Long? = null,
    val afectoIva: Boolean = true,
    val activo: Boolean = true,
) {
    fun aDominio() = ItemCatalogo(null, codigo, nombre, descripcion, unidad, precioNeto!!, afectoIva, activo)
}

data class ItemDto(
    val id: Long,
    val codigo: String?,
    val nombre: String,
    val descripcion: String?,
    val unidad: String?,
    val precioNeto: Long,
    val afectoIva: Boolean,
    val activo: Boolean,
) {
    companion object {
        fun de(i: ItemCatalogo) = ItemDto(i.id!!, i.codigo, i.nombre, i.descripcion, i.unidad, i.precioNeto, i.afectoIva, i.activo)
    }
}

// ---------- Empresa ----------

data class EmpresaDto(
    @field:NotBlank @field:Size(max = 200) val razonSocial: String = "",
    val rut: String? = null,
    @field:Size(max = 200) val giro: String? = null,
    @field:Size(max = 300) val direccion: String? = null,
    @field:Size(max = 100) val comuna: String? = null,
    @field:Size(max = 100) val ciudad: String? = null,
    @field:Size(max = 50) val telefono: String? = null,
    @field:Size(max = 200) val email: String? = null,
    @field:Size(max = 200) val sitioWeb: String? = null,
    @field:NotNull @field:Min(0) @field:Max(100) val tasaIva: BigDecimal? = BigDecimal("19"),
    @field:Min(1) @field:Max(365) val diasValidez: Int = 15,
    @field:Size(max = 500) val formaPago: String? = null,
    @field:Size(max = 500) val plazoEntrega: String? = null,
    @field:Size(max = 500) val garantia: String? = null,
    @field:Size(max = 5000) val condiciones: String? = null,
    val tieneLogo: Boolean = false,
) {
    fun aDominio() =
        Empresa(
            razonSocial,
            rut.rutOpcional(),
            giro,
            direccion,
            comuna,
            ciudad,
            telefono,
            email,
            sitioWeb,
            Porcentaje.de(tasaIva!!),
            diasValidez,
            formaPago,
            plazoEntrega,
            garantia,
            condiciones,
        )

    companion object {
        fun de(e: Empresa) =
            EmpresaDto(
                e.razonSocial,
                e.rut?.formateado(),
                e.giro,
                e.direccion,
                e.comuna,
                e.ciudad,
                e.telefono,
                e.email,
                e.sitioWeb,
                e.tasaIva.aBigDecimal().stripTrailingZeros(),
                e.diasValidez,
                e.formaPago,
                e.plazoEntrega,
                e.garantia,
                e.condiciones,
                e.tieneLogo,
            )
    }
}

// ---------- Cotizaciones ----------

enum class TipoDescuento { NINGUNO, PORCENTAJE, MONTO }

data class DescuentoDto(
    val tipo: TipoDescuento = TipoDescuento.NINGUNO,
    @field:PositiveOrZero val valor: BigDecimal = BigDecimal.ZERO,
) {
    fun aDominio(): DescuentoGlobal =
        when (tipo) {
            TipoDescuento.NINGUNO -> DescuentoGlobal.Ninguno
            TipoDescuento.PORCENTAJE -> DescuentoGlobal.PorPorcentaje(Porcentaje.de(valor))
            TipoDescuento.MONTO -> DescuentoGlobal.PorMonto(valor.longValueExact())
        }

    companion object {
        fun de(d: DescuentoGlobal) =
            when (d) {
                DescuentoGlobal.Ninguno -> DescuentoDto()
                is DescuentoGlobal.PorPorcentaje -> DescuentoDto(TipoDescuento.PORCENTAJE, d.porcentaje.aBigDecimal().stripTrailingZeros())
                is DescuentoGlobal.PorMonto -> DescuentoDto(TipoDescuento.MONTO, BigDecimal.valueOf(d.monto))
            }
    }
}

data class LineaEntrada(
    @field:NotBlank @field:Size(max = 1000) val descripcion: String = "",
    @field:NotNull val cantidad: BigDecimal? = null,
    @field:NotNull @field:PositiveOrZero val precioUnitario: Long? = null,
    @field:Size(max = 30) val unidad: String? = null,
    @field:PositiveOrZero @field:Max(100) val descuento: BigDecimal = BigDecimal.ZERO,
    val afectaIva: Boolean = true,
    @field:Size(max = 50) val codigo: String? = null,
    val itemCatalogoId: Long? = null,
) {
    fun aDominio() =
        LineaCotizacion(
            descripcion,
            Cantidad.de(cantidad!!),
            precioUnitario!!,
            unidad,
            Porcentaje.de(descuento),
            afectaIva,
            codigo,
            itemCatalogoId,
        )
}

data class CotizacionEntrada(
    @field:NotNull val clienteId: Long? = null,
    @field:Size(max = 100) val vendedor: String? = null,
    @field:NotNull val fechaEmision: LocalDate? = null,
    @field:NotNull val fechaValidez: LocalDate? = null,
    @field:Valid @field:Size(max = 500) val lineas: List<LineaEntrada> = emptyList(),
    @field:Valid val descuentoGlobal: DescuentoDto = DescuentoDto(),
    @field:Size(max = 500) val formaPago: String? = null,
    @field:Size(max = 500) val plazoEntrega: String? = null,
    @field:Size(max = 500) val garantia: String? = null,
    @field:Size(max = 5000) val observaciones: String? = null,
) {
    fun contenido() =
        ContenidoCotizacion(
            vendedor,
            fechaEmision!!,
            fechaValidez!!,
            lineas.map { it.aDominio() },
            descuentoGlobal.aDominio(),
            formaPago,
            plazoEntrega,
            garantia,
            observaciones,
        )
}

data class CalculoEntrada(
    @field:Valid @field:Size(max = 500) val lineas: List<LineaEntrada> = emptyList(),
    @field:Valid val descuentoGlobal: DescuentoDto = DescuentoDto(),
    val tasaIva: BigDecimal? = null,
)

data class TotalesDto(
    val lineas: List<TotalLineaDto>,
    val subtotalAfecto: Long,
    val subtotalExento: Long,
    val descuentoGlobal: Long,
    val neto: Long,
    val exento: Long,
    val iva: Long,
    val total: Long,
) {
    data class TotalLineaDto(
        val bruto: Long,
        val descuento: Long,
        val subtotal: Long,
    )

    companion object {
        fun de(t: Totales) =
            TotalesDto(
                t.lineas.map { TotalLineaDto(it.bruto, it.descuento, it.subtotal) },
                t.subtotalAfecto,
                t.subtotalExento,
                t.descuentoGlobal,
                t.netoAfecto,
                t.exento,
                t.iva,
                t.total,
            )
    }
}

data class AceptacionEntrada(
    val fecha: LocalDate? = null,
    @field:NotBlank @field:Size(max = 200) val nombre: String = "",
    @field:NotNull val medio: MedioAceptacion? = null,
    @field:Size(max = 500) val detalle: String? = null,
)

data class RechazoEntrada(
    @field:Size(max = 500) val motivo: String? = null,
)

data class LineaDto(
    val descripcion: String,
    val cantidad: BigDecimal,
    val precioUnitario: Long,
    val unidad: String?,
    val descuento: BigDecimal,
    val afectaIva: Boolean,
    val codigo: String?,
    val itemCatalogoId: Long?,
)

data class CotizacionDto(
    val id: Long,
    val folio: Long,
    val version: Int,
    val numero: String,
    val estado: EstadoCotizacion,
    val editable: Boolean,
    val clienteId: Long,
    val cliente: DatosCliente,
    val vendedor: String?,
    val fechaEmision: LocalDate,
    val fechaValidez: LocalDate,
    val moneda: String,
    val tasaIva: BigDecimal,
    val lineas: List<LineaDto>,
    val descuentoGlobal: DescuentoDto,
    val formaPago: String?,
    val plazoEntrega: String?,
    val garantia: String?,
    val observaciones: String?,
    val aceptacion: Aceptacion?,
    val motivoRechazo: String?,
    val totales: TotalesDto,
    val enviadaEn: Instant?,
    val creadaEn: Instant,
    val actualizadaEn: Instant,
) {
    companion object {
        fun de(
            c: Cotizacion,
            hoy: LocalDate,
        ) = CotizacionDto(
            id = c.id!!,
            folio = c.folio,
            version = c.version,
            numero = c.numero,
            estado = c.estadoAl(hoy),
            editable = c.editable,
            clienteId = c.clienteId,
            cliente = c.cliente,
            vendedor = c.vendedor,
            fechaEmision = c.fechaEmision,
            fechaValidez = c.fechaValidez,
            moneda = c.moneda.name,
            tasaIva = c.tasaIva.aBigDecimal().stripTrailingZeros(),
            lineas =
                c.lineas.map {
                    LineaDto(
                        it.descripcion,
                        it.cantidad.aBigDecimal().stripTrailingZeros(),
                        it.precioUnitario,
                        it.unidad,
                        it.descuento.aBigDecimal().stripTrailingZeros(),
                        it.afectaIva,
                        it.codigo,
                        it.itemCatalogoId,
                    )
                },
            descuentoGlobal = DescuentoDto.de(c.descuentoGlobal),
            formaPago = c.formaPago,
            plazoEntrega = c.plazoEntrega,
            garantia = c.garantia,
            observaciones = c.observaciones,
            aceptacion = c.aceptacion,
            motivoRechazo = c.motivoRechazo,
            totales = TotalesDto.de(c.totales),
            enviadaEn = c.enviadaEn,
            creadaEn = c.creadaEn,
            actualizadaEn = c.actualizadaEn,
        )
    }
}

data class EventoDto(
    val desde: EstadoCotizacion?,
    val hacia: EstadoCotizacion,
    val ocurridoEn: Instant,
    val detalle: String?,
) {
    companion object {
        fun de(e: EventoCotizacion) = EventoDto(e.desde, e.hacia, e.ocurridoEn, e.detalle)
    }
}
