package cl.tratohecho.app

import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.file.Path

/** Base de las pruebas de integración contra SQLite (perfil local) en una carpeta temporal. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // cada clase con su propia base en su carpeta temporal
abstract class BaseIntegracion {
    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var json: ObjectMapper

    fun get(url: String): ResultActions = mvc.perform(MockMvcRequestBuilders.get(url))

    fun post(
        url: String,
        cuerpo: String? = null,
    ): ResultActions =
        mvc.perform(
            MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).apply { if (cuerpo != null) content(cuerpo) },
        )

    fun put(
        url: String,
        cuerpo: String,
    ): ResultActions = mvc.perform(MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(cuerpo))

    fun delete(url: String): ResultActions = mvc.perform(MockMvcRequestBuilders.delete(url))

    fun ResultActions.cuerpo(): JsonNode = json.readTree(andReturn().response.contentAsString)

    companion object {
        @TempDir
        @JvmStatic
        lateinit var carpeta: Path

        @DynamicPropertySource
        @JvmStatic
        fun propiedades(registro: DynamicPropertyRegistry) {
            registro.add("tratohecho.datos.directorio") { carpeta.toString() }
            registro.add("tratohecho.acceso-local.habilitado") { "false" }
            registro.add("tratohecho.datos.respaldos-automaticos") { "0" }
        }
    }
}
