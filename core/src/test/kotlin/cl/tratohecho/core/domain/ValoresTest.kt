package cl.tratohecho.core.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ValoresTest {
    @Test
    fun `formatea pesos chilenos`() {
        assertThat(Clp.formatear(0)).isEqualTo("$0")
        assertThat(Clp.formatear(1_234_567)).isEqualTo("$1.234.567")
        assertThat(Clp.formatear(-990)).isEqualTo("-$990")
    }

    @Test
    fun `convierte entre precio neto y bruto`() {
        assertThat(Clp.netoDesdeBruto(19_990, Porcentaje.IVA_CHILE)).isEqualTo(16_798)
        assertThat(Clp.brutoDesdeNeto(16_798, Porcentaje.IVA_CHILE)).isEqualTo(19_990)
        assertThat(Clp.brutoDesdeNeto(10_000, Porcentaje.IVA_CHILE)).isEqualTo(11_900)
    }

    @Test
    fun `cantidad admite hasta tres decimales y debe ser positiva`() {
        assertThat(Cantidad.de("2.5").milesimas).isEqualTo(2_500)
        assertThat(Cantidad.de("0.125").toString()).isEqualTo("0.125")
        assertThatThrownBy { Cantidad.de("1.0001") }.isInstanceOf(DatoInvalidoException::class.java)
        assertThatThrownBy { Cantidad.de("0") }.isInstanceOf(DatoInvalidoException::class.java)
    }

    @Test
    fun `porcentaje en puntos basicos`() {
        assertThat(Porcentaje.de("19").puntosBasicos).isEqualTo(1_900)
        assertThat(Porcentaje.de("12.5").toString()).isEqualTo("12.5 %")
        assertThatThrownBy { Porcentaje.de("100.01") }.isInstanceOf(DatoInvalidoException::class.java)
        assertThatThrownBy { Porcentaje.de("1.234") }.isInstanceOf(DatoInvalidoException::class.java)
    }
}
