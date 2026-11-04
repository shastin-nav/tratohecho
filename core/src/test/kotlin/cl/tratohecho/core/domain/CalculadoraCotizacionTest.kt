package cl.tratohecho.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CalculadoraCotizacionTest {
    private val iva = Porcentaje.IVA_CHILE

    private fun linea(
        cantidad: String,
        precio: Long,
        descuento: String = "0",
        afecta: Boolean = true,
    ) = LineaCotizacion("ítem", Cantidad.de(cantidad), precio, descuento = Porcentaje.de(descuento), afectaIva = afecta)

    @Test
    fun `caso basico con linea exenta`() {
        val t = CalculadoraCotizacion.calcular(listOf(linea("2", 10_000), linea("1", 5_000, afecta = false)), DescuentoGlobal.Ninguno, iva)
        assertThat(t.netoAfecto).isEqualTo(20_000)
        assertThat(t.exento).isEqualTo(5_000)
        assertThat(t.iva).isEqualTo(3_800)
        assertThat(t.total).isEqualTo(28_800)
    }

    @Test
    fun `cantidad decimal, descuento de linea y descuento global porcentual`() {
        // 2,5 × 3.333 = 8.332,5 → 8.333; −10 % = 833,3 → 833; subtotal 7.500. Más 1.000 = 8.500.
        // Global 5 % = 425 → neto 8.075; IVA 1.534,25 → 1.534; total 9.609.
        val t =
            CalculadoraCotizacion.calcular(
                listOf(linea("2.5", 3_333, descuento = "10"), linea("1", 1_000)),
                DescuentoGlobal.PorPorcentaje(Porcentaje.de("5")),
                iva,
            )
        assertThat(t.lineas[0]).isEqualTo(TotalLinea(bruto = 8_333, descuento = 833, subtotal = 7_500))
        assertThat(t.subtotalAfecto).isEqualTo(8_500)
        assertThat(t.descuentoAfecto).isEqualTo(425)
        assertThat(t.netoAfecto).isEqualTo(8_075)
        assertThat(t.iva).isEqualTo(1_534)
        assertThat(t.total).isEqualTo(9_609)
    }

    @Test
    fun `descuento por monto se prorratea entre afecto y exento`() {
        val t =
            CalculadoraCotizacion.calcular(
                listOf(linea("1", 30_000), linea("1", 10_000, afecta = false)),
                DescuentoGlobal.PorMonto(1_000),
                iva,
            )
        assertThat(t.descuentoAfecto).isEqualTo(750)
        assertThat(t.descuentoExento).isEqualTo(250)
        assertThat(t.netoAfecto).isEqualTo(29_250)
        assertThat(t.iva).isEqualTo(5_558) // 5.557,5 → HALF_UP
        assertThat(t.total).isEqualTo(44_558)
    }

    @Test
    fun `el IVA se calcula sobre el total y no linea a linea`() {
        // Línea a línea: 3 × redondear(19,38) = 57. Sobre el total: redondear(58,14) = 58.
        val t = CalculadoraCotizacion.calcular(List(3) { linea("1", 102) }, DescuentoGlobal.Ninguno, iva)
        assertThat(t.iva).isEqualTo(58)
    }

    @Test
    fun `un descuento mayor que el subtotal se rechaza`() {
        assertThatThrownBy {
            CalculadoraCotizacion.calcular(listOf(linea("1", 1_000)), DescuentoGlobal.PorMonto(1_001), iva)
        }.isInstanceOf(ReglaDeNegocioException::class.java)
            .hasMessageContaining("supera el subtotal")
    }

    @Test
    fun `cotizacion vacia suma cero`() {
        val t = CalculadoraCotizacion.calcular(emptyList(), DescuentoGlobal.PorPorcentaje(Porcentaje.de("10")), iva)
        assertThat(t.total).isZero()
    }

    @Test
    fun `invariantes de los totales`() =
        runBlocking {
            val arbLinea =
                Arb.int(1..100_000).map { it }.let { cant ->
                    io.kotest.property.arbitrary.arbitrary {
                        LineaCotizacion(
                            descripcion = "x",
                            cantidad = Cantidad(cant.bind().toLong()),
                            precioUnitario = Arb.long(0L..5_000_000L).bind(),
                            descuento = Porcentaje(Arb.int(0..10_000).bind()),
                            afectaIva = Arb.boolean().bind(),
                        )
                    }
                }
            checkAll(Arb.list(arbLinea, 0..15), Arb.int(0..10_000)) { lineas, pctGlobal ->
                val t = CalculadoraCotizacion.calcular(lineas, DescuentoGlobal.PorPorcentaje(Porcentaje(pctGlobal)), iva)
                assertThat(t.total).isEqualTo(t.netoAfecto + t.exento + t.iva)
                assertThat(t.netoAfecto).isBetween(0, t.subtotalAfecto)
                assertThat(t.exento).isBetween(0, t.subtotalExento)
                assertThat(t.iva).isEqualTo(Clp.redondear(BigDecimal.valueOf(t.netoAfecto) * BigDecimal("0.19")))
                t.lineas.forEach { assertThat(it.subtotal).isBetween(0, it.bruto) }
            }
        }
}
