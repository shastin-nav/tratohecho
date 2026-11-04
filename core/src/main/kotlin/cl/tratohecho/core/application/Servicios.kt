package cl.tratohecho.core.application

import cl.tratohecho.core.domain.Aceptacion
import cl.tratohecho.core.domain.Cliente
import cl.tratohecho.core.domain.ContenidoCotizacion
import cl.tratohecho.core.domain.Cotizacion
import cl.tratohecho.core.domain.DescuentoGlobal
import cl.tratohecho.core.domain.Empresa
import cl.tratohecho.core.domain.EstadoCotizacion
import cl.tratohecho.core.domain.EventoCotizacion
import cl.tratohecho.core.domain.ItemCatalogo
import cl.tratohecho.core.domain.NoEncontradoException
import cl.tratohecho.core.domain.ReglaDeNegocioException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class ClienteServicio(
    private val clientes: ClienteRepositorio,
    private val cotizaciones: CotizacionRepositorio,
    private val tx: Transacciones,
) {
    fun crear(cliente: Cliente): Cliente =
        tx.ejecutar {
            exigirRutUnico(cliente)
            clientes.guardar(cliente.copy(id = null))
        }

    fun actualizar(
        id: Long,
        cliente: Cliente,
    ): Cliente =
        tx.ejecutar {
            obtener(id)
            exigirRutUnico(cliente.copy(id = id))
            clientes.guardar(cliente.copy(id = id))
        }

    fun obtener(id: Long): Cliente = clientes.buscarPorId(id) ?: throw NoEncontradoException("Cliente", id)

    fun buscar(
        texto: String?,
        paginacion: Paginacion,
    ) = clientes.buscar(texto, paginacion)

    /**
     * Derecho de supresión (Ley 21.719): si el cliente no tiene cotizaciones se borra; si las tiene, se anonimizan sus datos
     * personales (también en las copias guardadas en las cotizaciones) y se conservan los montos para la historia comercial.
     */
    fun eliminar(id: Long): ResultadoEliminacion =
        tx.ejecutar {
            obtener(id)
            if (cotizaciones.contarPorCliente(id) == 0L) {
                clientes.eliminar(id)
                ResultadoEliminacion.ELIMINADO
            } else {
                clientes.guardar(Cliente(id = id, razonSocial = Cliente.NOMBRE_ANONIMIZADO))
                cotizaciones.anonimizarCliente(id)
                ResultadoEliminacion.ANONIMIZADO
            }
        }

    private fun exigirRutUnico(cliente: Cliente) {
        val rut = cliente.rut ?: return
        val existente = clientes.buscarPorRut(rut.normalizado())
        if (existente != null && existente.id != cliente.id) {
            throw ReglaDeNegocioException("rut_duplicado", "Ya existe un cliente con RUT ${rut.formateado()}: ${existente.razonSocial}")
        }
    }

    enum class ResultadoEliminacion { ELIMINADO, ANONIMIZADO }
}

class CatalogoServicio(
    private val catalogo: CatalogoRepositorio,
    private val tx: Transacciones,
) {
    fun crear(item: ItemCatalogo): ItemCatalogo =
        tx.ejecutar {
            exigirCodigoUnico(item.codigo, null)
            catalogo.guardar(item.copy(id = null))
        }

    fun actualizar(
        id: Long,
        item: ItemCatalogo,
    ): ItemCatalogo =
        tx.ejecutar {
            obtener(id)
            exigirCodigoUnico(item.codigo, id)
            catalogo.guardar(item.copy(id = id))
        }

    fun obtener(id: Long): ItemCatalogo = catalogo.buscarPorId(id) ?: throw NoEncontradoException("Ítem de catálogo", id)

    fun buscar(
        texto: String?,
        soloActivos: Boolean,
        paginacion: Paginacion,
    ) = catalogo.buscar(texto, soloActivos, paginacion)

    private fun exigirCodigoUnico(
        codigo: String?,
        excluirId: Long?,
    ) {
        if (!codigo.isNullOrBlank() && catalogo.existeCodigo(codigo, excluirId)) {
            throw ReglaDeNegocioException("codigo_duplicado", "Ya existe un ítem con el código $codigo")
        }
    }
}

class EmpresaServicio(
    private val empresas: EmpresaRepositorio,
    private val tx: Transacciones,
) {
    fun obtener(): Empresa = empresas.obtener()

    fun actualizar(empresa: Empresa): Empresa = tx.ejecutar { empresas.guardar(empresa.copy(tieneLogo = empresas.obtener().tieneLogo)) }

    fun logo(): ByteArray? = empresas.obtenerLogo()

    fun cambiarLogo(
        contenido: ByteArray?,
        tipo: String?,
    ) {
        if (contenido != null) {
            if (contenido.size > LOGO_MAXIMO_BYTES) throw ReglaDeNegocioException("logo_muy_grande", "El logo debe pesar menos de 1 MB")
            if (tipo !in TIPOS_LOGO) throw ReglaDeNegocioException("logo_formato", "El logo debe ser PNG o JPG")
        }
        tx.ejecutar { empresas.guardarLogo(contenido, tipo) }
    }

    companion object {
        const val LOGO_MAXIMO_BYTES = 1_048_576
        val TIPOS_LOGO = setOf("image/png", "image/jpeg")
    }
}

class CotizacionServicio(
    private val cotizaciones: CotizacionRepositorio,
    private val clientes: ClienteRepositorio,
    private val empresas: EmpresaRepositorio,
    private val tx: Transacciones,
    private val reloj: Clock,
    private val politica: PoliticaDeUso,
) {
    private fun hoy(): LocalDate = LocalDate.now(reloj)

    private fun ahora(): Instant = Instant.now(reloj)

    fun obtener(id: Long): Cotizacion = cotizaciones.buscarPorId(id) ?: throw NoEncontradoException("Cotización", id)

    fun buscar(
        filtro: FiltroCotizaciones,
        paginacion: Paginacion,
    ) = cotizaciones.buscar(filtro, paginacion)

    fun versiones(folio: Long) = cotizaciones.versiones(folio)

    fun historial(id: Long) = cotizaciones.historial(id)

    fun resumen() = cotizaciones.resumenPorEstado()

    /** Valores por defecto que la UI usa para prellenar una cotización nueva. */
    fun plantilla(): ContenidoCotizacion {
        val empresa = empresas.obtener()
        return ContenidoCotizacion(
            vendedor = null,
            fechaEmision = hoy(),
            fechaValidez = hoy().plusDays(empresa.diasValidez.toLong()),
            lineas = emptyList(),
            descuentoGlobal = DescuentoGlobal.Ninguno,
            formaPago = empresa.formaPago,
            plazoEntrega = empresa.plazoEntrega,
            garantia = empresa.garantia,
            observaciones = null,
        )
    }

    fun crear(
        clienteId: Long,
        contenido: ContenidoCotizacion,
    ): Cotizacion =
        tx.ejecutar {
            politica.verificarPuedeCrearCotizacion(politica.contarCotizacionesCreadas())
            val cliente = clientes.buscarPorId(clienteId) ?: throw NoEncontradoException("Cliente", clienteId)
            val ahora = ahora()
            val borrador =
                Cotizacion(
                    folio = empresas.reservarFolio(),
                    clienteId = clienteId,
                    cliente = cliente.datosParaCotizacion(),
                    fechaEmision = contenido.fechaEmision,
                    fechaValidez = contenido.fechaValidez,
                    tasaIva = empresas.obtener().tasaIva,
                    creadaEn = ahora,
                    actualizadaEn = ahora,
                ).conContenido(cliente.datosParaCotizacion(), clienteId, contenido, ahora)
            val guardada = cotizaciones.guardar(borrador)
            cotizaciones.registrarEvento(EventoCotizacion(guardada.id!!, null, EstadoCotizacion.BORRADOR, ahora, "Creada"))
            guardada
        }

    fun actualizar(
        id: Long,
        clienteId: Long,
        contenido: ContenidoCotizacion,
    ): Cotizacion =
        tx.ejecutar {
            val actual = obtener(id)
            val cliente = clientes.buscarPorId(clienteId) ?: throw NoEncontradoException("Cliente", clienteId)
            cotizaciones.guardar(actual.conContenido(cliente.datosParaCotizacion(), clienteId, contenido, ahora()))
        }

    fun eliminarBorrador(id: Long) {
        tx.ejecutar {
            val actual = obtener(id)
            if (!actual.editable || actual.version > 1) {
                throw ReglaDeNegocioException("no_eliminable", "Solo se pueden eliminar borradores que nunca fueron enviados")
            }
            cotizaciones.eliminar(id)
        }
    }

    fun enviar(id: Long): Cotizacion = transicion(id, "Enviada") { it.enviar(hoy(), ahora()) }

    fun aceptar(
        id: Long,
        aceptacion: Aceptacion,
    ): Cotizacion =
        transicion(id, "Aceptada por ${aceptacion.nombre} (${aceptacion.medio.name.lowercase()})") {
            it.aceptar(aceptacion, hoy(), ahora())
        }

    fun rechazar(
        id: Long,
        motivo: String?,
    ): Cotizacion = transicion(id, motivo?.let { "Rechazada: $it" } ?: "Rechazada") { it.rechazar(motivo, ahora()) }

    fun nuevaVersion(id: Long): Cotizacion =
        tx.ejecutar {
            val actual = obtener(id)
            val persistida = if (actual.estadoAl(hoy()) == EstadoCotizacion.VENCIDA) vencer(actual) else actual
            val (reemplazada, nueva) = persistida.nuevaVersion(hoy(), empresas.obtener().diasValidez, ahora())
            cotizaciones.guardar(reemplazada)
            cotizaciones.registrarEvento(
                EventoCotizacion(reemplazada.id!!, persistida.estado, reemplazada.estado, ahora(), "Reemplazada por v${nueva.version}"),
            )
            val guardada = cotizaciones.guardar(nueva)
            cotizaciones.registrarEvento(
                EventoCotizacion(guardada.id!!, null, EstadoCotizacion.BORRADOR, ahora(), "Nueva versión de ${actual.numero}"),
            )
            guardada
        }

    fun duplicar(id: Long): Cotizacion =
        tx.ejecutar {
            politica.verificarPuedeCrearCotizacion(politica.contarCotizacionesCreadas())
            val original = obtener(id)
            val copia = original.duplicar(empresas.reservarFolio(), hoy(), empresas.obtener().diasValidez, ahora())
            val guardada = cotizaciones.guardar(copia)
            cotizaciones.registrarEvento(
                EventoCotizacion(guardada.id!!, null, EstadoCotizacion.BORRADOR, ahora(), "Duplicada de ${original.numero}"),
            )
            guardada
        }

    /** Persiste como VENCIDA toda cotización enviada cuya validez expiró. Devuelve cuántas se marcaron. */
    fun marcarVencidas(): Int =
        tx.ejecutar {
            cotizaciones.enviadasConValidezAnteriorA(hoy()).onEach { vencer(it) }.size
        }

    private fun vencer(cotizacion: Cotizacion): Cotizacion {
        val vencida = cotizacion.vencer(hoy(), ahora())
        cotizaciones.guardar(vencida)
        cotizaciones.registrarEvento(
            EventoCotizacion(cotizacion.id!!, cotizacion.estado, vencida.estado, ahora(), "Validez expiró el ${cotizacion.fechaValidez}"),
        )
        return vencida
    }

    private fun transicion(
        id: Long,
        detalle: String,
        cambio: (Cotizacion) -> Cotizacion,
    ): Cotizacion =
        tx.ejecutar {
            val actual = obtener(id)
            val nueva = cambio(actual)
            val guardada = cotizaciones.guardar(nueva)
            cotizaciones.registrarEvento(EventoCotizacion(id, actual.estado, nueva.estado, ahora(), detalle))
            guardada
        }
}
