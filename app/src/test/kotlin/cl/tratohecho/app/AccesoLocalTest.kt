package cl.tratohecho.app

import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Path

@SpringBootTest
@AutoConfigureMockMvc
class AccesoLocalTest {
    @Autowired lateinit var mvc: MockMvc

    private val sesion = Cookie("th_sesion", TOKEN)

    @Test
    fun `sin la cookie de sesion la API responde 401`() {
        mvc.perform(get("/api/sistema")).andExpect(status().isUnauthorized).andExpect(jsonPath("$.codigo").value("sesion_local"))
    }

    @Test
    fun `el token de la URL se canjea por una cookie HttpOnly y SameSite Strict`() {
        val respuesta =
            mvc
                .perform(get("/").param("t", TOKEN))
                .andExpect(status().is3xxRedirection)
                .andExpect(header().string("Location", "/"))
                .andReturn()
                .response
        val cookie = respuesta.getHeader("Set-Cookie")!!
        assertThat(cookie).contains("th_sesion=$TOKEN", "HttpOnly", "SameSite=Strict")
        mvc.perform(get("/api/sistema").cookie(sesion)).andExpect(status().isOk).andExpect(jsonPath("$.modo").value("LOCAL"))
    }

    @Test
    fun `un token incorrecto no entrega cookie`() {
        val respuesta = mvc.perform(get("/").param("t", "otro")).andReturn().response
        assertThat(respuesta.getHeader("Set-Cookie")).isNull()
    }

    @Test
    fun `bloquea hosts distintos de localhost (DNS rebinding)`() {
        mvc
            .perform(get("/api/sistema").cookie(sesion).header("Host", "atacante.example:8080"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.codigo").value("host_no_permitido"))
    }

    @Test
    fun `bloquea escrituras desde otro origen`() {
        mvc
            .perform(
                post(
                    "/api/clientes",
                ).cookie(sesion).header("Origin", "https://atacante.example").contentType("application/json").content("{}"),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.codigo").value("origen_no_permitido"))
    }

    @Test
    fun `agrega cabeceras de seguridad`() {
        mvc
            .perform(get("/api/sistema").cookie(sesion))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().exists("Content-Security-Policy"))
    }

    companion object {
        const val TOKEN = "token-de-prueba"

        @TempDir
        @JvmStatic
        lateinit var carpeta: Path

        @DynamicPropertySource
        @JvmStatic
        fun propiedades(registro: DynamicPropertyRegistry) {
            registro.add("tratohecho.datos.directorio") { carpeta.toString() }
            registro.add("tratohecho.acceso-local.token") { TOKEN }
            registro.add("tratohecho.datos.respaldos-automaticos") { "0" }
        }
    }
}
