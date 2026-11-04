package cl.tratohecho.core.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/** Moneda de una cotización. El MVP solo opera en pesos chilenos; UF/USD llegan en v1.1. */
enum class Moneda { CLP, }

/**
 * Cantidad con hasta 3 decimales (p. ej. 2,5 m² o 0,125 kg).
 * Se guarda como milésimas enteras para evitar errores de punto flotante y porque SQLite no tiene un tipo decimal exacto.
 */
@JvmInline
value class Cantidad(
    val milesimas: Long,
) {
    init {
        requerir(milesimas > 0, "cantidad") { "La cantidad debe ser mayor que cero" }
    }

    fun aBigDecimal(): BigDecimal = BigDecimal.valueOf(milesimas, ESCALA)

    override fun toString(): String = aBigDecimal().stripTrailingZeros().toPlainString()

    companion object {
        const val ESCALA = 3

        fun de(valor: BigDecimal): Cantidad {
            requerir(valor.stripTrailingZeros().scale() <= ESCALA, "cantidad") { "La cantidad admite como máximo 3 decimales" }
            return Cantidad(valor.setScale(ESCALA, RoundingMode.UNNECESSARY).unscaledValue().longValueExact())
        }

        fun de(valor: String): Cantidad = de(BigDecimal(valor))

        fun de(valor: Int): Cantidad = de(BigDecimal.valueOf(valor.toLong()))
    }
}

/** Porcentaje con hasta 2 decimales, almacenado en puntos básicos (19 % = 1900). */
@JvmInline
value class Porcentaje(
    val puntosBasicos: Int,
) {
    init {
        requerir(puntosBasicos in 0..MAXIMO, "porcentaje") { "El porcentaje debe estar entre 0 y 100" }
    }

    fun aBigDecimal(): BigDecimal = BigDecimal.valueOf(puntosBasicos.toLong(), 2)

    /** Fracción decimal exacta: 19 % → 0.19 */
    fun comoFraccion(): BigDecimal = BigDecimal.valueOf(puntosBasicos.toLong(), 4)

    val esCero: Boolean get() = puntosBasicos == 0

    override fun toString(): String = aBigDecimal().stripTrailingZeros().toPlainString() + " %"

    companion object {
        private const val MAXIMO = 10_000
        val CERO = Porcentaje(0)
        val IVA_CHILE = Porcentaje(1900)

        fun de(valor: BigDecimal): Porcentaje {
            requerir(valor.stripTrailingZeros().scale() <= 2, "porcentaje") { "El porcentaje admite como máximo 2 decimales" }
            return Porcentaje(valor.setScale(2, RoundingMode.UNNECESSARY).unscaledValue().intValueExact())
        }

        fun de(valor: String): Porcentaje = de(BigDecimal(valor))
    }
}

/** Utilidades para montos en pesos chilenos (enteros, sin decimales). */
object Clp {
    /** Redondeo comercial al peso: mitad hacia arriba. */
    fun redondear(valor: BigDecimal): Long = valor.setScale(0, RoundingMode.HALF_UP).longValueExact()

    fun formatear(monto: Long): String {
        val simbolos = DecimalFormatSymbols().apply { groupingSeparator = '.' }
        val formato = DecimalFormat("#,##0", simbolos)
        return (if (monto < 0) "-$" else "$") + formato.format(kotlin.math.abs(monto))
    }

    /** Precio con IVA → neto. Puede diferir en $1 del bruto original al volver a sumar el IVA, por redondeo. */
    fun netoDesdeBruto(
        bruto: Long,
        tasa: Porcentaje,
    ): Long = redondear(BigDecimal.valueOf(bruto).divide(BigDecimal.ONE + tasa.comoFraccion(), 10, RoundingMode.HALF_UP))

    fun brutoDesdeNeto(
        neto: Long,
        tasa: Porcentaje,
    ): Long = neto + redondear(BigDecimal.valueOf(neto) * tasa.comoFraccion())
}
