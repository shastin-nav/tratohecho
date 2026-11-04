package cl.tratohecho.app

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Flujo completo por la API. Se ejecuta contra SQLite y contra PostgreSQL (misma suite, ver subclases). */
abstract class EscenarioCotizacion : BaseIntegracion() {
    private fun crearCliente(
        rut: String?,
        nombre: String,
    ): Long {
        val rutJson = rut?.let { "\"$it\"" } ?: "null"
        return post("/api/clientes", """{"rut":$rutJson,"razonSocial":"$nombre","email":"contacto@ejemplo.cl","comuna":"Ñuñoa"}""")
            .andExpect(status().isCreated)
            .cuerpo()["id"]
            .asLong()
    }

    private fun cuerpoCotizacion(
        clienteId: Long,
        precio: Long = 3333,
        validez: String = "2099-12-31",
    ) = """
        {"clienteId":$clienteId,"vendedor":"Andrea","fechaEmision":"2026-01-10","fechaValidez":"$validez",
         "lineas":[
           {"descripcion":"Instalación split","cantidad":2.5,"precioUnitario":$precio,"unidad":"un","descuento":10,"afectaIva":true},
           {"descripcion":"Capacitación","cantidad":1,"precioUnitario":1000,"afectaIva":true}
         ],
         "descuentoGlobal":{"tipo":"PORCENTAJE","valor":5},
         "formaPago":"Contado","plazoEntrega":"5 días hábiles"}
        """.trimIndent()

    @Test
    fun `flujo completo de una cotizacion`() {
        put(
            "/api/empresa",
            """{"razonSocial":"Climatización Austral SpA","rut":"76.086.428-5","tasaIva":19,"diasValidez":15,"formaPago":"Transferencia"}""",
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.rut").value("76.086.428-5"))

        val clienteId = crearCliente("12.345.678-5", "Panadería Las Delicias")
        post("/api/clientes", """{"rut":"12345678-9","razonSocial":"RUT malo"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.codigo").value("dato_invalido"))
        post("/api/clientes", """{"rut":"123456785","razonSocial":"Duplicado"}""")
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.codigo").value("rut_duplicado"))

        // Totales: ver CalculadoraCotizacionTest (8.075 neto, 1.534 IVA, 9.609 total).
        val creada = post("/api/cotizaciones", cuerpoCotizacion(clienteId)).andExpect(status().isCreated).cuerpo()
        val id = creada["id"].asLong()
        assertThat(creada["estado"].asString()).isEqualTo("BORRADOR")
        assertThat(creada["totales"]["neto"].asLong()).isEqualTo(8_075)
        assertThat(creada["totales"]["iva"].asLong()).isEqualTo(1_534)
        assertThat(creada["totales"]["total"].asLong()).isEqualTo(9_609)
        assertThat(creada["cliente"]["rut"].asString()).isEqualTo("12.345.678-5")

        post("/api/cotizaciones/$id/enviar").andExpect(status().isOk).andExpect(jsonPath("$.estado").value("ENVIADA"))
        put("/api/cotizaciones/$id", cuerpoCotizacion(clienteId, precio = 4000))
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.codigo").value("no_editable"))

        // Contraoferta: nueva versión con el mismo folio.
        val v2 = post("/api/cotizaciones/$id/nueva-version").andExpect(status().isCreated).cuerpo()
        val idV2 = v2["id"].asLong()
        assertThat(v2["folio"].asLong()).isEqualTo(creada["folio"].asLong())
        assertThat(v2["version"].asInt()).isEqualTo(2)
        get("/api/cotizaciones/$id").andExpect(jsonPath("$.estado").value("REEMPLAZADA"))
        put("/api/cotizaciones/$idV2", cuerpoCotizacion(clienteId, precio = 4000)).andExpect(status().isOk)
        post("/api/cotizaciones/$idV2/enviar").andExpect(status().isOk)
        post("/api/cotizaciones/$idV2/aceptar", """{"nombre":"Rosa Muñoz","medio":"CORREO","detalle":"Respondió el correo"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.estado").value("ACEPTADA"))
            .andExpect(jsonPath("$.aceptacion.nombre").value("Rosa Muñoz"))

        get("/api/cotizaciones/$idV2/versiones").andExpect(jsonPath("$.length()").value(2))
        get("/api/cotizaciones/$idV2/historial")
            .andExpect(jsonPath("$[0].hacia").value("BORRADOR"))
            .andExpect(jsonPath("$[2].hacia").value("ACEPTADA"))
        get("/api/cotizaciones?q=panader").andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].version").value(2))
        get("/api/cotizaciones/resumen").andExpect(status().isOk)

        // PDF con los datos y totales correctos.
        val pdf =
            get("/api/cotizaciones/$idV2/pdf")
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsByteArray
        assertThat(String(pdf, 0, 5)).isEqualTo("%PDF-")
        val texto = Loader.loadPDF(pdf).use { PDFTextStripper().getText(it) }.replace(Regex("\\s+"), " ")
        assertThat(texto).contains("COTIZACIÓN", "Climatización Austral SpA", "76.086.428-5", "Panadería Las Delicias", "12.345.678-5")
        assertThat(texto).contains("Forma de pago", "Contado", "IVA 19 %", "VERSIÓN DE PRUEBA")
        val v2Totales = get("/api/cotizaciones/$idV2").cuerpo()["totales"]
        assertThat(texto).contains("$%,d".format(v2Totales["total"].asLong()).replace(',', '.'))

        // Derecho de supresión: con cotizaciones se anonimiza.
        get("/api/clientes/$clienteId/exportar").andExpect(status().isOk).andExpect(jsonPath("$.cotizaciones.length()").value(2))
        delete("/api/clientes/$clienteId").andExpect(jsonPath("$.resultado").value("ANONIMIZADO"))
        get("/api/cotizaciones/$idV2")
            .andExpect(jsonPath("$.cliente.razonSocial").value("Cliente eliminado"))
            .andExpect(jsonPath("$.aceptacion.nombre").value("Cliente eliminado"))
        val sinCotizaciones = crearCliente(null, "Cliente nuevo")
        delete("/api/clientes/$sinCotizaciones").andExpect(jsonPath("$.resultado").value("ELIMINADO"))
        get("/api/clientes/$sinCotizaciones").andExpect(status().isNotFound)
    }

    @Test
    fun `catalogo con codigo unico y busqueda`() {
        post("/api/catalogo", """{"codigo":"MAN-01","nombre":"Mantención preventiva","precioNeto":35000,"unidad":"un"}""")
            .andExpect(status().isCreated)
        post("/api/catalogo", """{"codigo":"man-01","nombre":"Otro","precioNeto":1}""")
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.codigo").value("codigo_duplicado"))
        post("/api/catalogo", """{"nombre":"Sin precio"}""").andExpect(status().isBadRequest)
        get("/api/catalogo?q=preventiva&soloActivos=true").andExpect(jsonPath("$.items[0].codigo").value("MAN-01"))
    }

    @Test
    fun `descuento mayor al subtotal y calculo en vivo`() {
        post(
            "/api/cotizaciones/calcular",
            """{"lineas":[{"descripcion":"x","cantidad":3,"precioUnitario":102}],"descuentoGlobal":{"tipo":"NINGUNO","valor":0}}""",
        ).andExpect(jsonPath("$.iva").value(58))
            .andExpect(jsonPath("$.total").value(364))
        post(
            "/api/cotizaciones/calcular",
            """{"lineas":[{"descripcion":"x","cantidad":1,"precioUnitario":100}],"descuentoGlobal":{"tipo":"MONTO","valor":101}}""",
        ).andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.codigo").value("descuento_excede_subtotal"))
    }

    @Test
    fun `no se puede enviar una cotizacion sin lineas ni aceptar un borrador`() {
        val clienteId = crearCliente(null, "Cliente sin líneas")
        val vacia =
            post(
                "/api/cotizaciones",
                """{"clienteId":$clienteId,"fechaEmision":"2026-01-10","fechaValidez":"2099-01-10","lineas":[]}""",
            ).andExpect(status().isCreated).cuerpo()["id"].asLong()
        post("/api/cotizaciones/$vacia/enviar").andExpect(jsonPath("$.codigo").value("sin_lineas"))
        post(
            "/api/cotizaciones/$vacia/aceptar",
            """{"nombre":"X","medio":"VERBAL"}""",
        ).andExpect(jsonPath("$.codigo").value("transicion_invalida"))
        delete("/api/cotizaciones/$vacia").andExpect(status().isNoContent)
        get("/api/cotizaciones/$vacia").andExpect(status().isNotFound)
    }
}

class EscenarioSqliteTest : EscenarioCotizacion()
