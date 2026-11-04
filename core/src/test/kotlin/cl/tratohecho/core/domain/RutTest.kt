package cl.tratohecho.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class RutTest {
    @ParameterizedTest
    @CsvSource(
        "12.345.678-5, 12.345.678-5, 12345678-5",
        "123456785, 12.345.678-5, 12345678-5",
        "11111111-1, 11.111.111-1, 11111111-1",
        "1-9, 1-9, 1-9",
        "6-k, 6-K, 6-K",
        "' 76.086.428-5 ', 76.086.428-5, 76086428-5",
    )
    fun `acepta RUT validos en distintos formatos`(
        entrada: String,
        formateado: String,
        normalizado: String,
    ) {
        val rut = Rut.parse(entrada)
        assertThat(rut.formateado()).isEqualTo(formateado)
        assertThat(rut.normalizado()).isEqualTo(normalizado)
    }

    @ParameterizedTest
    @ValueSource(strings = ["12.345.678-9", "", "-", "K", "abc-5", "0-0", "123.456.789-0", "12.345.678-X"])
    fun `rechaza RUT invalidos`(entrada: String) {
        assertThat(Rut.esValido(entrada)).isFalse()
        assertThatThrownBy { Rut.parse(entrada) }.isInstanceOf(DatoInvalidoException::class.java)
    }

    @Test
    fun `todo RUT generado se puede formatear y volver a leer`() =
        runBlocking {
            checkAll(Arb.int(1..99_999_999)) { cuerpo ->
                val rut = Rut.de(cuerpo)
                assertThat(Rut.parse(rut.formateado())).isEqualTo(rut)
                assertThat(Rut.parse(rut.normalizado())).isEqualTo(rut)
            }
        }

    @Test
    fun `cambiar el digito verificador invalida el RUT`() =
        runBlocking {
            checkAll(Arb.int(1..99_999_999)) { cuerpo ->
                val dv = Rut.calcularDv(cuerpo)
                val otro = "0123456789K".first { it != dv }
                assertThat(Rut.esValido("$cuerpo-$otro")).isFalse()
            }
        }
}
