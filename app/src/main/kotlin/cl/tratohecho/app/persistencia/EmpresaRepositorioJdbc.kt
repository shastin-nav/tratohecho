package cl.tratohecho.app.persistencia

import cl.tratohecho.core.application.EmpresaRepositorio
import cl.tratohecho.core.domain.Empresa
import cl.tratohecho.core.domain.Porcentaje
import cl.tratohecho.core.domain.Rut
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.SqlParameterValue
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Types

@Repository
class EmpresaRepositorioJdbc(
    private val jdbc: JdbcClient,
) : EmpresaRepositorio {
    private val mapper =
        RowMapper { rs, _ ->
            Empresa(
                razonSocial = rs.getString("razon_social"),
                rut = rs.textoONull("rut")?.let(Rut::parseOrNull),
                giro = rs.textoONull("giro"),
                direccion = rs.textoONull("direccion"),
                comuna = rs.textoONull("comuna"),
                ciudad = rs.textoONull("ciudad"),
                telefono = rs.textoONull("telefono"),
                email = rs.textoONull("email"),
                sitioWeb = rs.textoONull("sitio_web"),
                tasaIva = Porcentaje(rs.getInt("tasa_iva_pb")),
                diasValidez = rs.getInt("dias_validez"),
                formaPago = rs.textoONull("forma_pago"),
                plazoEntrega = rs.textoONull("plazo_entrega"),
                garantia = rs.textoONull("garantia"),
                condiciones = rs.textoONull("condiciones"),
                tieneLogo = rs.getBoolean("tiene_logo"),
            )
        }

    override fun obtener(): Empresa =
        jdbc
            .sql("SELECT e.*, CASE WHEN e.logo IS NULL THEN FALSE ELSE TRUE END AS tiene_logo FROM empresa e WHERE id = 1")
            .query(mapper)
            .single()

    override fun guardar(empresa: Empresa): Empresa {
        jdbc
            .sql(
                """
                UPDATE empresa SET razon_social = :razonSocial, rut = :rut, giro = :giro, direccion = :direccion, comuna = :comuna,
                       ciudad = :ciudad, telefono = :telefono, email = :email, sitio_web = :sitioWeb, tasa_iva_pb = :tasaIva,
                       dias_validez = :diasValidez, forma_pago = :formaPago, plazo_entrega = :plazoEntrega, garantia = :garantia,
                       condiciones = :condiciones
                WHERE id = 1
                """.trimIndent(),
            ).params(
                mapOf(
                    "razonSocial" to empresa.razonSocial.trim(),
                    "rut" to empresa.rut?.normalizado(),
                    "giro" to empresa.giro.normalizado(),
                    "direccion" to empresa.direccion.normalizado(),
                    "comuna" to empresa.comuna.normalizado(),
                    "ciudad" to empresa.ciudad.normalizado(),
                    "telefono" to empresa.telefono.normalizado(),
                    "email" to empresa.email.normalizado(),
                    "sitioWeb" to empresa.sitioWeb.normalizado(),
                    "tasaIva" to empresa.tasaIva.puntosBasicos,
                    "diasValidez" to empresa.diasValidez,
                    "formaPago" to empresa.formaPago.normalizado(),
                    "plazoEntrega" to empresa.plazoEntrega.normalizado(),
                    "garantia" to empresa.garantia.normalizado(),
                    "condiciones" to empresa.condiciones.normalizado(),
                ),
            ).update()
        return obtener()
    }

    override fun obtenerLogo(): ByteArray? =
        jdbc
            .sql("SELECT logo FROM empresa WHERE id = 1")
            .query { rs, _ -> rs.getBytes("logo") }
            .list()
            .firstOrNull()

    fun tipoLogo(): String? =
        jdbc
            .sql("SELECT logo_tipo FROM empresa WHERE id = 1")
            .query(String::class.java)
            .optional()
            .orElse(null)

    override fun guardarLogo(
        contenido: ByteArray?,
        tipo: String?,
    ) {
        jdbc
            .sql("UPDATE empresa SET logo = :logo, logo_tipo = :tipo WHERE id = 1")
            .param("logo", SqlParameterValue(Types.BINARY, contenido))
            .param("tipo", tipo)
            .update()
    }

    override fun reservarFolio(): Long =
        jdbc
            .sql("UPDATE empresa SET siguiente_folio = siguiente_folio + 1 WHERE id = 1 RETURNING siguiente_folio - 1")
            .query(Long::class.java)
            .single()
}
