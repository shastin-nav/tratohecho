package cl.tratohecho.app.persistencia

import cl.tratohecho.core.application.CatalogoRepositorio
import cl.tratohecho.core.application.Pagina
import cl.tratohecho.core.application.Paginacion
import cl.tratohecho.core.domain.ItemCatalogo
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class CatalogoRepositorioJdbc(
    private val jdbc: JdbcClient,
) : CatalogoRepositorio {
    private val mapper =
        RowMapper { rs, _ ->
            ItemCatalogo(
                id = rs.getLong("id"),
                codigo = rs.textoONull("codigo"),
                nombre = rs.getString("nombre"),
                descripcion = rs.textoONull("descripcion"),
                unidad = rs.textoONull("unidad"),
                precioNeto = rs.getLong("precio_neto"),
                afectoIva = rs.getBoolean("afecto_iva"),
                activo = rs.getBoolean("activo"),
            )
        }

    override fun guardar(item: ItemCatalogo): ItemCatalogo {
        val params =
            mapOf(
                "codigo" to item.codigo.normalizado(),
                "nombre" to item.nombre.trim(),
                "descripcion" to item.descripcion.normalizado(),
                "unidad" to item.unidad.normalizado(),
                "precioNeto" to item.precioNeto,
                "afectoIva" to item.afectoIva,
                "activo" to item.activo,
            )
        if (item.id == null) {
            val id =
                jdbc
                    .sql(
                        """
                        INSERT INTO item_catalogo (codigo, nombre, descripcion, unidad, precio_neto, afecto_iva, activo)
                        VALUES (:codigo, :nombre, :descripcion, :unidad, :precioNeto, :afectoIva, :activo)
                        RETURNING id
                        """.trimIndent(),
                    ).params(params)
                    .query(Long::class.java)
                    .single()
            return item.copy(id = id)
        }
        jdbc
            .sql(
                """
                UPDATE item_catalogo SET codigo = :codigo, nombre = :nombre, descripcion = :descripcion, unidad = :unidad,
                       precio_neto = :precioNeto, afecto_iva = :afectoIva, activo = :activo
                WHERE id = :id
                """.trimIndent(),
            ).params(params + ("id" to item.id))
            .update()
        return item
    }

    override fun buscarPorId(id: Long): ItemCatalogo? =
        jdbc
            .sql("SELECT * FROM item_catalogo WHERE id = :id")
            .param("id", id)
            .query(mapper)
            .optional()
            .orElse(null)

    override fun buscar(
        texto: String?,
        soloActivos: Boolean,
        paginacion: Paginacion,
    ): Pagina<ItemCatalogo> {
        val condiciones = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        texto.normalizado()?.let {
            condiciones += "(LOWER(nombre) LIKE :patron ESCAPE '\\' OR LOWER(COALESCE(codigo, '')) LIKE :patron ESCAPE '\\')"
            params["patron"] = patronContiene(it)
        }
        if (soloActivos) condiciones += "activo = TRUE"
        val donde = if (condiciones.isEmpty()) "" else "WHERE " + condiciones.joinToString(" AND ")
        val total =
            jdbc
                .sql("SELECT COUNT(*) FROM item_catalogo $donde")
                .params(params)
                .query(Long::class.java)
                .single()
        val items =
            jdbc
                .sql("SELECT * FROM item_catalogo $donde ORDER BY nombre LIMIT :limite OFFSET :desde")
                .params(params + mapOf("limite" to paginacion.tamano, "desde" to paginacion.offset))
                .query(mapper)
                .list()
        return Pagina(items, total, paginacion.pagina, paginacion.tamano)
    }

    override fun existeCodigo(
        codigo: String,
        excluirId: Long?,
    ): Boolean =
        jdbc
            .sql("SELECT COUNT(*) FROM item_catalogo WHERE LOWER(codigo) = :codigo AND id <> :excluir")
            .param("codigo", codigo.trim().lowercase())
            .param("excluir", excluirId ?: -1L)
            .query(Long::class.java)
            .single() > 0
}
