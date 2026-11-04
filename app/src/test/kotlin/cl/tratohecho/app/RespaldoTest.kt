package cl.tratohecho.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class RespaldoTest : BaseIntegracion() {
    @Test
    fun `crear un respaldo, cambiar datos y restaurarlo deja todo como antes`() {
        post("/api/clientes", """{"razonSocial":"Antes del respaldo"}""").andExpect(status().isCreated)
        val nombre = post("/api/respaldos").andExpect(status().isOk).cuerpo()["nombre"].asString()
        val zip =
            get("/api/respaldos/$nombre")
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsByteArray
        assertThat(zip.size).isGreaterThan(1000)

        post("/api/clientes", """{"razonSocial":"Después del respaldo"}""").andExpect(status().isCreated)
        get("/api/clientes").andExpect(jsonPath("$.total").value(2))

        mvc
            .perform(multipart("/api/respaldos/restaurar").file(MockMultipartFile("archivo", nombre, "application/zip", zip)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.respaldoPrevio").exists())
        get(
            "/api/clientes",
        ).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].razonSocial").value("Antes del respaldo"))

        // La app sigue funcionando sobre la base restaurada.
        post("/api/clientes", """{"razonSocial":"Tras restaurar"}""").andExpect(status().isCreated)
        get("/api/respaldos").andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `rechaza archivos que no son respaldos`() {
        mvc
            .perform(
                multipart("/api/respaldos/restaurar").file(MockMultipartFile("archivo", "x.zip", "application/zip", "hola".toByteArray())),
            ).andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.codigo").value("respaldo_invalido"))
    }

    @Test
    fun `no permite descargar archivos fuera de la carpeta de respaldos`() {
        get("/api/respaldos/..%2F..%2Fetc%2Fpasswd").andExpect(status().is4xxClientError)
    }
}
