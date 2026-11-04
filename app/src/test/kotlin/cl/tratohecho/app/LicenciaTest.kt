package cl.tratohecho.app

import cl.tratohecho.app.licencia.FormatoLicencia
import cl.tratohecho.app.licencia.Licencia
import cl.tratohecho.app.licencia.TipoLicencia
import org.junit.jupiter.api.Test
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

class LicenciaTest : BaseIntegracion() {
    @Test
    fun `la version de prueba limita las cotizaciones hasta activar una licencia valida`() {
        val clienteId = post("/api/clientes", """{"razonSocial":"Cliente"}""").cuerpo()["id"].asLong()
        val cuerpo = """{"clienteId":$clienteId,"fechaEmision":"2026-01-10","fechaValidez":"2026-02-10"}"""
        repeat(2) { post("/api/cotizaciones", cuerpo).andExpect(status().isCreated) }
        post("/api/cotizaciones", cuerpo).andExpect(status().isUnprocessableContent).andExpect(jsonPath("$.codigo").value("limite_prueba"))

        // Una licencia firmada con otra clave no sirve.
        val (_, otraPrivada) = FormatoLicencia.nuevoParDeClaves()
        val falsa = FormatoLicencia.firmar(licencia(), FormatoLicencia.clavePrivada(otraPrivada))
        post("/api/sistema/licencia", """{"clave":"$falsa"}""").andExpect(jsonPath("$.codigo").value("licencia_invalida"))
        // Alterar el contenido invalida la firma.
        val valida = FormatoLicencia.firmar(licencia(), FormatoLicencia.clavePrivada(CLAVES.second))
        val alterada = valida.split(".").let { "${it[0]}.${it[1].dropLast(2)}AA.${it[2]}" }
        post("/api/sistema/licencia", """{"clave":"$alterada"}""").andExpect(jsonPath("$.codigo").value("licencia_invalida"))

        post("/api/sistema/licencia", """{"clave":"$valida"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.licenciada").value(true))
            .andExpect(jsonPath("$.licencia.titular").value("Ferretería Los Andes SpA"))
        post("/api/cotizaciones", cuerpo).andExpect(status().isCreated)
    }

    private fun licencia() =
        Licencia("TH-0001", "Ferretería Los Andes SpA", "76086428-5", TipoLicencia.INDIVIDUAL, LocalDate.of(2026, 11, 4))

    companion object {
        val CLAVES = FormatoLicencia.nuevoParDeClaves()

        @DynamicPropertySource
        @JvmStatic
        fun licencias(registro: DynamicPropertyRegistry) {
            registro.add("tratohecho.licencia.clave-publica") { CLAVES.first }
            registro.add("tratohecho.licencia.limite-prueba") { "2" }
        }
    }
}
