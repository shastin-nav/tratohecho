package cl.tratohecho.core.domain

import java.math.BigDecimal

/** Línea de una cotización. Guarda una copia de los datos del catálogo para que cambios posteriores de precio no alteren el documento. */
data class LineaCotizacion(
    val descripcion: String,
    val cantidad: Cantidad,
    val precioUnitario: Long,
    val unidad: String? = null,
    val descuento: Porcentaje = Porcentaje.CERO,
    val afectaIva: Boolean = true,
    val codigo: String? = null,
    val itemCatalogoId: Long? = null,
) {
    init {
        requerir(descripcion.isNotBlank(), "descripcion") { "Cada línea necesita una descripción" }
        requerir(precioUnitario >= 0, "precioUnitario") { "El precio unitario no puede ser negativo" }
    }
}

sealed interface DescuentoGlobal {
    data object Ninguno : DescuentoGlobal

    data class PorPorcentaje(
        val porcentaje: Porcentaje,
    ) : DescuentoGlobal

    data class PorMonto(
        val monto: Long,
    ) : DescuentoGlobal {
        init {
            requerir(monto >= 0, "descuentoGlobal") { "El descuento no puede ser negativo" }
        }
    }
}

data class TotalLinea(
    val bruto: Long,
    val descuento: Long,
    val subtotal: Long,
)

data class Totales(
    val lineas: List<TotalLinea>,
    val subtotalAfecto: Long,
    val subtotalExento: Long,
    val descuentoAfecto: Long,
    val descuentoExento: Long,
    val netoAfecto: Long,
    val exento: Long,
    val iva: Long,
    val total: Long,
) {
    val descuentoGlobal: Long get() = descuentoAfecto + descuentoExento
}

/**
 * Cálculo de totales (ver docs/adr/0004-calculo-iva.md):
 *  1. Cada línea: redondear(cantidad × precio) − redondear(bruto × % descuento), al peso.
 *  2. El descuento global se reparte entre lo afecto y lo exento en proporción a sus subtotales.
 *  3. El IVA se calcula UNA vez sobre el neto afecto total y se redondea al peso (no línea a línea).
 */
object CalculadoraCotizacion {
    fun calcular(
        lineas: List<LineaCotizacion>,
        descuentoGlobal: DescuentoGlobal,
        tasaIva: Porcentaje,
    ): Totales {
        val totalesLinea =
            lineas.map { linea ->
                val bruto = Clp.redondear(linea.cantidad.aBigDecimal() * BigDecimal.valueOf(linea.precioUnitario))
                val descuento = Clp.redondear(BigDecimal.valueOf(bruto) * linea.descuento.comoFraccion())
                TotalLinea(bruto, descuento, bruto - descuento)
            }
        val subtotalAfecto = lineas.zip(totalesLinea).filter { it.first.afectaIva }.sumOf { it.second.subtotal }
        val subtotalExento = lineas.zip(totalesLinea).filterNot { it.first.afectaIva }.sumOf { it.second.subtotal }
        val subtotal = subtotalAfecto + subtotalExento

        val (descuentoAfecto, descuentoExento) =
            when (descuentoGlobal) {
                DescuentoGlobal.Ninguno -> {
                    0L to 0L
                }

                is DescuentoGlobal.PorPorcentaje -> {
                    val f = descuentoGlobal.porcentaje.comoFraccion()
                    Clp.redondear(BigDecimal.valueOf(subtotalAfecto) * f) to Clp.redondear(BigDecimal.valueOf(subtotalExento) * f)
                }

                is DescuentoGlobal.PorMonto -> {
                    if (descuentoGlobal.monto > subtotal) {
                        throw ReglaDeNegocioException(
                            "descuento_excede_subtotal",
                            "El descuento (${Clp.formatear(descuentoGlobal.monto)}) supera el subtotal (${Clp.formatear(subtotal)})",
                        )
                    }
                    if (subtotal == 0L) {
                        0L to 0L
                    } else {
                        val afecto =
                            Clp.redondear(
                                BigDecimal.valueOf(descuentoGlobal.monto) * BigDecimal.valueOf(subtotalAfecto) /
                                    BigDecimal.valueOf(subtotal),
                            )
                        afecto to (descuentoGlobal.monto - afecto)
                    }
                }
            }

        val netoAfecto = subtotalAfecto - descuentoAfecto
        val exento = subtotalExento - descuentoExento
        val iva = Clp.redondear(BigDecimal.valueOf(netoAfecto) * tasaIva.comoFraccion())
        return Totales(
            lineas = totalesLinea,
            subtotalAfecto = subtotalAfecto,
            subtotalExento = subtotalExento,
            descuentoAfecto = descuentoAfecto,
            descuentoExento = descuentoExento,
            netoAfecto = netoAfecto,
            exento = exento,
            iva = iva,
            total = netoAfecto + exento + iva,
        )
    }
}
