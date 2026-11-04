package cl.tratohecho.core.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class CotizacionTest {
    private val hoy = LocalDate.of(2026, 11, 4)
    private val ahora = Instant.parse("2026-11-04T12:00:00Z")

    private fun borrador(lineas: List<LineaCotizacion> = listOf(LineaCotizacion("Mantención", Cantidad.de(1), 50_000))) =
        Cotizacion(
            id = 1,
            folio = 100,
            clienteId = 7,
            cliente = DatosCliente("Ferretería Los Andes SpA", "76.086.428-5", null, null, null, null, null),
            fechaEmision = hoy,
            fechaValidez = hoy.plusDays(15),
            lineas = lineas,
            creadaEn = ahora,
            actualizadaEn = ahora,
        )

    private val aceptacion = Aceptacion(hoy, "María Pérez", MedioAceptacion.CORREO)

    @Test
    fun `ciclo feliz borrador, enviada, aceptada`() {
        val enviada = borrador().enviar(hoy, ahora)
        assertThat(enviada.estado).isEqualTo(EstadoCotizacion.ENVIADA)
        assertThat(enviada.enviadaEn).isEqualTo(ahora)
        val aceptada = enviada.aceptar(aceptacion, hoy.plusDays(3), ahora)
        assertThat(aceptada.estado).isEqualTo(EstadoCotizacion.ACEPTADA)
        assertThat(aceptada.aceptacion).isEqualTo(aceptacion)
    }

    @Test
    fun `no se puede enviar sin lineas`() {
        assertThatThrownBy { borrador(emptyList()).enviar(hoy, ahora) }
            .isInstanceOf(ReglaDeNegocioException::class.java)
            .extracting("codigo")
            .isEqualTo("sin_lineas")
    }

    @Test
    fun `no se puede aceptar un borrador`() {
        assertThatThrownBy { borrador().aceptar(aceptacion, hoy, ahora) }
            .extracting("codigo")
            .isEqualTo("transicion_invalida")
    }

    @Test
    fun `una enviada no se puede editar`() {
        val enviada = borrador().enviar(hoy, ahora)
        val contenido =
            ContenidoCotizacion(null, hoy, hoy.plusDays(10), enviada.lineas, DescuentoGlobal.Ninguno, null, null, null, null)
        assertThatThrownBy { enviada.conContenido(enviada.cliente, 7, contenido, ahora) }
            .extracting("codigo")
            .isEqualTo("no_editable")
    }

    @Test
    fun `una enviada con validez expirada se considera vencida y no se puede aceptar`() {
        val enviada = borrador().enviar(hoy, ahora)
        val despues = hoy.plusDays(16)
        assertThat(enviada.estadoAl(hoy.plusDays(15))).isEqualTo(EstadoCotizacion.ENVIADA)
        assertThat(enviada.estadoAl(despues)).isEqualTo(EstadoCotizacion.VENCIDA)
        assertThatThrownBy { enviada.aceptar(aceptacion, despues, ahora) }
            .extracting("codigo")
            .isEqualTo("cotizacion_vencida")
        assertThat(enviada.vencer(despues, ahora).estado).isEqualTo(EstadoCotizacion.VENCIDA)
    }

    @Test
    fun `nueva version deja la anterior reemplazada y crea un borrador con el mismo folio`() {
        val enviada = borrador().enviar(hoy, ahora)
        val (anterior, nueva) = enviada.nuevaVersion(hoy.plusDays(5), 15, ahora)
        assertThat(anterior.estado).isEqualTo(EstadoCotizacion.REEMPLAZADA)
        assertThat(anterior.id).isEqualTo(1)
        assertThat(nueva.id).isNull()
        assertThat(nueva.folio).isEqualTo(100)
        assertThat(nueva.version).isEqualTo(2)
        assertThat(nueva.numero).isEqualTo("N° 100-v2")
        assertThat(nueva.estado).isEqualTo(EstadoCotizacion.BORRADOR)
        assertThat(nueva.fechaValidez).isEqualTo(hoy.plusDays(20))
        assertThat(nueva.lineas).isEqualTo(enviada.lineas)
    }

    @Test
    fun `un borrador no tiene nueva version y una aceptada tampoco`() {
        assertThatThrownBy { borrador().nuevaVersion(hoy, 15, ahora) }.extracting("codigo").isEqualTo("transicion_invalida")
        val aceptada = borrador().enviar(hoy, ahora).aceptar(aceptacion, hoy, ahora)
        assertThatThrownBy { aceptada.nuevaVersion(hoy, 15, ahora) }.extracting("codigo").isEqualTo("transicion_invalida")
    }

    @Test
    fun `duplicar crea un folio nuevo en borrador`() {
        val aceptada = borrador().enviar(hoy, ahora).aceptar(aceptacion, hoy, ahora)
        val copia = aceptada.duplicar(101, hoy.plusDays(30), 15, ahora)
        assertThat(copia.folio).isEqualTo(101)
        assertThat(copia.version).isEqualTo(1)
        assertThat(copia.estado).isEqualTo(EstadoCotizacion.BORRADOR)
        assertThat(copia.aceptacion).isNull()
        assertThat(copia.fechaValidez).isEqualTo(hoy.plusDays(45))
    }

    @Test
    fun `la validez no puede ser anterior a la emision`() {
        assertThatThrownBy { borrador().copy(fechaValidez = hoy.minusDays(1)) }.isInstanceOf(DatoInvalidoException::class.java)
    }
}
