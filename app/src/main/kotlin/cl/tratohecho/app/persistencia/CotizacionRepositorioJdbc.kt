package cl.tratohecho.app.persistencia

import cl.tratohecho.core.application.CotizacionRepositorio
import cl.tratohecho.core.application.FiltroCotizaciones
import cl.tratohecho.core.application.Pagina
import cl.tratohecho.core.application.Paginacion
import cl.tratohecho.core.application.ResumenEstado
import cl.tratohecho.core.domain.Aceptacion
import cl.tratohecho.core.domain.Cantidad
import cl.tratohecho.core.domain.Cliente
import cl.tratohecho.core.domain.Cotizacion
import cl.tratohecho.core.domain.DatosCliente
import cl.tratohecho.core.domain.DescuentoGlobal
import cl.tratohecho.core.domain.EstadoCotizacion
import cl.tratohecho.core.domain.EventoCotizacion
import cl.tratohecho.core.domain.LineaCotizacion
import cl.tratohecho.core.domain.MedioAceptacion
import cl.tratohecho.core.domain.Moneda
import cl.tratohecho.core.domain.Porcentaje
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate

@Repository
class CotizacionRepositorioJdbc(
    private val jdbc: JdbcClient,
) : CotizacionRepositorio {
    private val lineaMapper =
        RowMapper { rs, _ ->
            LineaCotizacion(
                descripcion = rs.getString("descripcion"),
                cantidad = Cantidad(rs.getLong("cantidad_milesimas")),
                precioUnitario = rs.getLong("precio_unitario"),
                unidad = rs.textoONull("unidad"),
                descuento = Porcentaje(rs.getInt("descuento_pb")),
                afectaIva = rs.getBoolean("afecta_iva"),
                codigo = rs.textoONull("codigo"),
                itemCatalogoId = rs.longONull("item_catalogo_id"),
            )
        }

    private fun mapearCabecera(
        rs: ResultSet,
        lineas: List<LineaCotizacion>,
    ) = Cotizacion(
        id = rs.getLong("id"),
        folio = rs.getLong("folio"),
        version = rs.getInt("version"),
        estado = EstadoCotizacion.valueOf(rs.getString("estado")),
        clienteId = rs.getLong("cliente_id"),
        cliente =
            DatosCliente(
                razonSocial = rs.getString("cliente_razon_social"),
                rut = rs.textoONull("cliente_rut"),
                giro = rs.textoONull("cliente_giro"),
                direccion = rs.textoONull("cliente_direccion"),
                contacto = rs.textoONull("cliente_contacto"),
                email = rs.textoONull("cliente_email"),
                telefono = rs.textoONull("cliente_telefono"),
            ),
        vendedor = rs.textoONull("vendedor"),
        fechaEmision = LocalDate.parse(rs.getString("fecha_emision")),
        fechaValidez = LocalDate.parse(rs.getString("fecha_validez")),
        moneda = Moneda.valueOf(rs.getString("moneda")),
        tasaIva = Porcentaje(rs.getInt("tasa_iva_pb")),
        lineas = lineas,
        descuentoGlobal =
            when (rs.getString("descuento_tipo")) {
                "PORCENTAJE" -> DescuentoGlobal.PorPorcentaje(Porcentaje(rs.getLong("descuento_valor").toInt()))
                "MONTO" -> DescuentoGlobal.PorMonto(rs.getLong("descuento_valor"))
                else -> DescuentoGlobal.Ninguno
            },
        formaPago = rs.textoONull("forma_pago"),
        plazoEntrega = rs.textoONull("plazo_entrega"),
        garantia = rs.textoONull("garantia"),
        observaciones = rs.textoONull("observaciones"),
        aceptacion =
            rs.textoONull("aceptacion_fecha")?.let {
                Aceptacion(
                    fecha = LocalDate.parse(it),
                    nombre = rs.getString("aceptacion_nombre"),
                    medio = MedioAceptacion.valueOf(rs.getString("aceptacion_medio")),
                    detalle = rs.textoONull("aceptacion_detalle"),
                )
            },
        motivoRechazo = rs.textoONull("motivo_rechazo"),
        enviadaEn = rs.longONull("enviada_en")?.let(Instant::ofEpochMilli),
        creadaEn = Instant.ofEpochMilli(rs.getLong("creada_en")),
        actualizadaEn = Instant.ofEpochMilli(rs.getLong("actualizada_en")),
    )

    /**
     * Cabecera sin líneas. Las líneas se cargan en una consulta aparte, nunca dentro del RowMapper: con SQLite hay una sola
     * conexión en el pool y una consulta anidada fuera de transacción quedaría esperando una conexión libre.
     */
    private val cabeceraMapper = RowMapper { rs, _ -> mapearCabecera(rs, emptyList()) }

    override fun guardar(cotizacion: Cotizacion): Cotizacion {
        val t = cotizacion.totales
        val (descuentoTipo, descuentoValor) =
            when (val d = cotizacion.descuentoGlobal) {
                DescuentoGlobal.Ninguno -> "NINGUNO" to 0L
                is DescuentoGlobal.PorPorcentaje -> "PORCENTAJE" to d.porcentaje.puntosBasicos.toLong()
                is DescuentoGlobal.PorMonto -> "MONTO" to d.monto
            }
        val params =
            mapOf(
                "folio" to cotizacion.folio,
                "version" to cotizacion.version,
                "estado" to cotizacion.estado.name,
                "clienteId" to cotizacion.clienteId,
                "cRazonSocial" to cotizacion.cliente.razonSocial,
                "cRut" to cotizacion.cliente.rut,
                "cGiro" to cotizacion.cliente.giro,
                "cDireccion" to cotizacion.cliente.direccion,
                "cContacto" to cotizacion.cliente.contacto,
                "cEmail" to cotizacion.cliente.email,
                "cTelefono" to cotizacion.cliente.telefono,
                "vendedor" to cotizacion.vendedor.normalizado(),
                "fechaEmision" to cotizacion.fechaEmision.toString(),
                "fechaValidez" to cotizacion.fechaValidez.toString(),
                "moneda" to cotizacion.moneda.name,
                "tasaIva" to cotizacion.tasaIva.puntosBasicos,
                "descuentoTipo" to descuentoTipo,
                "descuentoValor" to descuentoValor,
                "formaPago" to cotizacion.formaPago.normalizado(),
                "plazoEntrega" to cotizacion.plazoEntrega.normalizado(),
                "garantia" to cotizacion.garantia.normalizado(),
                "observaciones" to cotizacion.observaciones.normalizado(),
                "aFecha" to cotizacion.aceptacion?.fecha?.toString(),
                "aNombre" to cotizacion.aceptacion?.nombre,
                "aMedio" to cotizacion.aceptacion?.medio?.name,
                "aDetalle" to cotizacion.aceptacion?.detalle,
                "motivoRechazo" to cotizacion.motivoRechazo,
                "neto" to t.netoAfecto,
                "exento" to t.exento,
                "iva" to t.iva,
                "total" to t.total,
                "enviadaEn" to cotizacion.enviadaEn?.toEpochMilli(),
                "creadaEn" to cotizacion.creadaEn.toEpochMilli(),
                "actualizadaEn" to cotizacion.actualizadaEn.toEpochMilli(),
            )
        val id =
            if (cotizacion.id == null) {
                jdbc
                    .sql(
                        """
                        INSERT INTO cotizacion (folio, version, estado, cliente_id, cliente_razon_social, cliente_rut, cliente_giro,
                            cliente_direccion, cliente_contacto, cliente_email, cliente_telefono, vendedor, fecha_emision, fecha_validez,
                            moneda, tasa_iva_pb, descuento_tipo, descuento_valor, forma_pago, plazo_entrega, garantia, observaciones,
                            aceptacion_fecha, aceptacion_nombre, aceptacion_medio, aceptacion_detalle, motivo_rechazo,
                            neto, exento, iva, total, enviada_en, creada_en, actualizada_en)
                        VALUES (:folio, :version, :estado, :clienteId, :cRazonSocial, :cRut, :cGiro, :cDireccion, :cContacto, :cEmail,
                            :cTelefono, :vendedor, :fechaEmision, :fechaValidez, :moneda, :tasaIva, :descuentoTipo, :descuentoValor,
                            :formaPago, :plazoEntrega, :garantia, :observaciones, :aFecha, :aNombre, :aMedio, :aDetalle, :motivoRechazo,
                            :neto, :exento, :iva, :total, :enviadaEn, :creadaEn, :actualizadaEn)
                        RETURNING id
                        """.trimIndent(),
                    ).params(params)
                    .query(Long::class.java)
                    .single()
            } else {
                jdbc
                    .sql(
                        """
                        UPDATE cotizacion SET estado = :estado, cliente_id = :clienteId, cliente_razon_social = :cRazonSocial,
                            cliente_rut = :cRut, cliente_giro = :cGiro, cliente_direccion = :cDireccion, cliente_contacto = :cContacto,
                            cliente_email = :cEmail, cliente_telefono = :cTelefono, vendedor = :vendedor, fecha_emision = :fechaEmision,
                            fecha_validez = :fechaValidez, moneda = :moneda, tasa_iva_pb = :tasaIva, descuento_tipo = :descuentoTipo,
                            descuento_valor = :descuentoValor, forma_pago = :formaPago, plazo_entrega = :plazoEntrega,
                            garantia = :garantia, observaciones = :observaciones, aceptacion_fecha = :aFecha,
                            aceptacion_nombre = :aNombre, aceptacion_medio = :aMedio, aceptacion_detalle = :aDetalle,
                            motivo_rechazo = :motivoRechazo, neto = :neto, exento = :exento, iva = :iva, total = :total,
                            enviada_en = :enviadaEn, actualizada_en = :actualizadaEn
                        WHERE id = :id
                        """.trimIndent(),
                    ).params(params + ("id" to cotizacion.id))
                    .update()
                cotizacion.id!!
            }
        reemplazarLineas(id, cotizacion.lineas)
        return cotizacion.copy(id = id)
    }

    private fun reemplazarLineas(
        cotizacionId: Long,
        lineas: List<LineaCotizacion>,
    ) {
        jdbc.sql("DELETE FROM linea_cotizacion WHERE cotizacion_id = :id").param("id", cotizacionId).update()
        lineas.forEachIndexed { posicion, l ->
            jdbc
                .sql(
                    """
                    INSERT INTO linea_cotizacion (cotizacion_id, posicion, codigo, descripcion, unidad, cantidad_milesimas,
                        precio_unitario, descuento_pb, afecta_iva, item_catalogo_id)
                    VALUES (:cotizacionId, :posicion, :codigo, :descripcion, :unidad, :cantidad, :precio, :descuento, :afecta, :itemId)
                    """.trimIndent(),
                ).params(
                    mapOf(
                        "cotizacionId" to cotizacionId,
                        "posicion" to posicion,
                        "codigo" to l.codigo.normalizado(),
                        "descripcion" to l.descripcion.trim(),
                        "unidad" to l.unidad.normalizado(),
                        "cantidad" to l.cantidad.milesimas,
                        "precio" to l.precioUnitario,
                        "descuento" to l.descuento.puntosBasicos,
                        "afecta" to l.afectaIva,
                        "itemId" to l.itemCatalogoId,
                    ),
                ).update()
        }
    }

    private fun lineas(cotizacionId: Long): List<LineaCotizacion> =
        jdbc
            .sql("SELECT * FROM linea_cotizacion WHERE cotizacion_id = :id ORDER BY posicion")
            .param("id", cotizacionId)
            .query(lineaMapper)
            .list()

    override fun buscarPorId(id: Long): Cotizacion? =
        jdbc
            .sql("SELECT * FROM cotizacion WHERE id = :id")
            .param("id", id)
            .query(cabeceraMapper)
            .optional()
            .orElse(null)
            ?.let { it.copy(lineas = lineas(id)) }

    override fun eliminar(id: Long) {
        jdbc.sql("DELETE FROM cotizacion WHERE id = :id").param("id", id).update()
    }

    override fun versiones(folio: Long): List<Cotizacion> =
        jdbc
            .sql("SELECT id FROM cotizacion WHERE folio = :folio ORDER BY version")
            .param("folio", folio)
            .query(Long::class.java)
            .list()
            .mapNotNull { id -> id?.let(::buscarPorId) }

    override fun buscar(
        filtro: FiltroCotizaciones,
        paginacion: Paginacion,
    ): Pagina<Cotizacion> {
        val (donde, params) = condiciones(filtro)
        val total =
            jdbc
                .sql("SELECT COUNT(*) FROM cotizacion c $donde")
                .params(params)
                .query(Long::class.java)
                .single()
        val items =
            jdbc
                .sql("SELECT * FROM cotizacion c $donde ORDER BY c.folio DESC, c.version DESC LIMIT :limite OFFSET :desde")
                .params(params + mapOf("limite" to paginacion.tamano, "desde" to paginacion.offset))
                .query(cabeceraMapper)
                .list()
                .map { it.copy(lineas = lineas(it.id!!)) }
        return Pagina(items, total, paginacion.pagina, paginacion.tamano)
    }

    private fun condiciones(filtro: FiltroCotizaciones): Pair<String, Map<String, Any>> {
        val partes = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        if (!filtro.todasLasVersiones) {
            partes += "c.version = (SELECT MAX(v.version) FROM cotizacion v WHERE v.folio = c.folio)"
        }
        filtro.estado?.let {
            partes += "c.estado = :estado"
            params["estado"] = it.name
        }
        filtro.clienteId?.let {
            partes += "c.cliente_id = :clienteId"
            params["clienteId"] = it
        }
        filtro.vendedor.normalizado()?.let {
            partes += "LOWER(c.vendedor) = :vendedor"
            params["vendedor"] = it.lowercase()
        }
        filtro.texto.normalizado()?.let { texto ->
            val folio = texto.removePrefix("N°").trim().toLongOrNull()
            partes +=
                if (folio != null) {
                    params["folio"] = folio
                    "(c.folio = :folio OR LOWER(c.cliente_razon_social) LIKE :patron ESCAPE '\\')"
                } else {
                    "(LOWER(c.cliente_razon_social) LIKE :patron ESCAPE '\\' OR LOWER(COALESCE(c.vendedor, '')) LIKE :patron ESCAPE '\\')"
                }
            params["patron"] = patronContiene(texto)
        }
        return (if (partes.isEmpty()) "" else "WHERE " + partes.joinToString(" AND ")) to params
    }

    override fun enviadasConValidezAnteriorA(fecha: LocalDate): List<Cotizacion> =
        jdbc
            .sql("SELECT id FROM cotizacion WHERE estado = 'ENVIADA' AND fecha_validez < :fecha")
            .param("fecha", fecha.toString())
            .query(Long::class.java)
            .list()
            .mapNotNull { id -> id?.let(::buscarPorId) }

    override fun contarPorCliente(clienteId: Long): Long =
        jdbc
            .sql("SELECT COUNT(*) FROM cotizacion WHERE cliente_id = :id")
            .param("id", clienteId)
            .query(Long::class.java)
            .single()

    override fun anonimizarCliente(clienteId: Long) {
        jdbc
            .sql(
                """
                UPDATE cotizacion SET cliente_razon_social = :nombre, cliente_rut = NULL, cliente_giro = NULL, cliente_direccion = NULL,
                       cliente_contacto = NULL, cliente_email = NULL, cliente_telefono = NULL,
                       aceptacion_nombre = CASE WHEN aceptacion_nombre IS NULL THEN NULL ELSE :nombre END
                WHERE cliente_id = :id
                """.trimIndent(),
            ).param("nombre", Cliente.NOMBRE_ANONIMIZADO)
            .param("id", clienteId)
            .update()
    }

    override fun registrarEvento(evento: EventoCotizacion) {
        jdbc
            .sql(
                """
                INSERT INTO evento_cotizacion (cotizacion_id, desde, hacia, ocurrido_en, detalle)
                VALUES (:cotizacionId, :desde, :hacia, :ocurridoEn, :detalle)
                """.trimIndent(),
            ).params(
                mapOf(
                    "cotizacionId" to evento.cotizacionId,
                    "desde" to evento.desde?.name,
                    "hacia" to evento.hacia.name,
                    "ocurridoEn" to evento.ocurridoEn.toEpochMilli(),
                    "detalle" to evento.detalle,
                ),
            ).update()
    }

    override fun historial(cotizacionId: Long): List<EventoCotizacion> =
        jdbc
            .sql("SELECT * FROM evento_cotizacion WHERE cotizacion_id = :id ORDER BY ocurrido_en, id")
            .param("id", cotizacionId)
            .query { rs, _ ->
                EventoCotizacion(
                    cotizacionId = rs.getLong("cotizacion_id"),
                    desde = rs.textoONull("desde")?.let(EstadoCotizacion::valueOf),
                    hacia = EstadoCotizacion.valueOf(rs.getString("hacia")),
                    ocurridoEn = Instant.ofEpochMilli(rs.getLong("ocurrido_en")),
                    detalle = rs.textoONull("detalle"),
                )
            }.list()

    override fun resumenPorEstado(): List<ResumenEstado> =
        jdbc
            .sql(
                """
                SELECT c.estado, COUNT(*) AS cantidad, COALESCE(SUM(c.total), 0) AS total FROM cotizacion c
                WHERE c.version = (SELECT MAX(v.version) FROM cotizacion v WHERE v.folio = c.folio)
                GROUP BY c.estado
                """.trimIndent(),
            ).query {
                rs,
                _,
                ->
                ResumenEstado(EstadoCotizacion.valueOf(rs.getString("estado")), rs.getLong("cantidad"), rs.getLong("total"))
            }.list()

    fun contarFolios(): Long =
        jdbc
            .sql("SELECT COUNT(DISTINCT folio) FROM cotizacion")
            .query(Long::class.java)
            .single()
}
