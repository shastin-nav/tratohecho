package cl.tratohecho.app.persistencia

import cl.tratohecho.core.application.ClienteRepositorio
import cl.tratohecho.core.application.Pagina
import cl.tratohecho.core.application.Paginacion
import cl.tratohecho.core.domain.Cliente
import cl.tratohecho.core.domain.Rut
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Clock

@Repository
class ClienteRepositorioJdbc(
    private val jdbc: JdbcClient,
    private val reloj: Clock,
) : ClienteRepositorio {
    private val mapper =
        RowMapper { rs, _ ->
            Cliente(
                id = rs.getLong("id"),
                rut = rs.textoONull("rut")?.let(Rut::parseOrNull),
                razonSocial = rs.getString("razon_social"),
                giro = rs.textoONull("giro"),
                contacto = rs.textoONull("contacto"),
                email = rs.textoONull("email"),
                telefono = rs.textoONull("telefono"),
                direccion = rs.textoONull("direccion"),
                comuna = rs.textoONull("comuna"),
                ciudad = rs.textoONull("ciudad"),
                notas = rs.textoONull("notas"),
            )
        }

    override fun guardar(cliente: Cliente): Cliente {
        val params =
            mapOf(
                "rut" to cliente.rut?.normalizado(),
                "razonSocial" to cliente.razonSocial.trim(),
                "giro" to cliente.giro.normalizado(),
                "contacto" to cliente.contacto.normalizado(),
                "email" to cliente.email.normalizado(),
                "telefono" to cliente.telefono.normalizado(),
                "direccion" to cliente.direccion.normalizado(),
                "comuna" to cliente.comuna.normalizado(),
                "ciudad" to cliente.ciudad.normalizado(),
                "notas" to cliente.notas.normalizado(),
            )
        if (cliente.id == null) {
            val id =
                jdbc
                    .sql(
                        """
                        INSERT INTO cliente (rut, razon_social, giro, contacto, email, telefono, direccion, comuna, ciudad, notas, creado_en)
                        VALUES (:rut, :razonSocial, :giro, :contacto, :email, :telefono, :direccion, :comuna, :ciudad, :notas, :creadoEn)
                        RETURNING id
                        """.trimIndent(),
                    ).params(params + ("creadoEn" to reloj.millis()))
                    .query(Long::class.java)
                    .single()
            return cliente.copy(id = id)
        }
        jdbc
            .sql(
                """
                UPDATE cliente SET rut = :rut, razon_social = :razonSocial, giro = :giro, contacto = :contacto, email = :email,
                       telefono = :telefono, direccion = :direccion, comuna = :comuna, ciudad = :ciudad, notas = :notas
                WHERE id = :id
                """.trimIndent(),
            ).params(params + ("id" to cliente.id))
            .update()
        return cliente
    }

    override fun buscarPorId(id: Long): Cliente? =
        jdbc
            .sql("SELECT * FROM cliente WHERE id = :id")
            .param("id", id)
            .query(mapper)
            .optional()
            .orElse(null)

    override fun buscarPorRut(rutNormalizado: String): Cliente? =
        jdbc
            .sql("SELECT * FROM cliente WHERE rut = :rut")
            .param("rut", rutNormalizado)
            .query(mapper)
            .optional()
            .orElse(null)

    override fun buscar(
        texto: String?,
        paginacion: Paginacion,
    ): Pagina<Cliente> {
        val filtro = texto.normalizado()
        val donde =
            if (filtro == null) {
                ""
            } else {
                "WHERE LOWER(razon_social) LIKE :patron ESCAPE '\\' OR rut LIKE :rut OR LOWER(COALESCE(contacto, '')) LIKE :patron ESCAPE '\\'"
            }
        val params =
            if (filtro == null) {
                emptyMap()
            } else {
                mapOf("patron" to patronContiene(filtro), "rut" to "%" + filtro.replace(".", "").uppercase() + "%")
            }
        val total =
            jdbc
                .sql("SELECT COUNT(*) FROM cliente $donde")
                .params(params)
                .query(Long::class.java)
                .single()
        val items =
            jdbc
                .sql("SELECT * FROM cliente $donde ORDER BY razon_social LIMIT :limite OFFSET :desde")
                .params(params + mapOf("limite" to paginacion.tamano, "desde" to paginacion.offset))
                .query(mapper)
                .list()
        return Pagina(items, total, paginacion.pagina, paginacion.tamano)
    }

    override fun eliminar(id: Long) {
        jdbc.sql("DELETE FROM cliente WHERE id = :id").param("id", id).update()
    }
}
