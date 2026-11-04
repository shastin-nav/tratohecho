package cl.tratohecho.core.application

import cl.tratohecho.core.domain.Cliente
import cl.tratohecho.core.domain.Cotizacion
import cl.tratohecho.core.domain.Empresa
import cl.tratohecho.core.domain.EstadoCotizacion
import cl.tratohecho.core.domain.EventoCotizacion
import cl.tratohecho.core.domain.ItemCatalogo
import java.time.LocalDate

data class Pagina<T>(
    val items: List<T>,
    val total: Long,
    val pagina: Int,
    val tamano: Int,
)

data class Paginacion(
    val pagina: Int = 0,
    val tamano: Int = 20,
) {
    init {
        require(pagina >= 0) { "pagina debe ser >= 0" }
        require(tamano in 1..200) { "tamano debe estar entre 1 y 200" }
    }

    val offset: Long get() = pagina.toLong() * tamano
}

interface ClienteRepositorio {
    fun guardar(cliente: Cliente): Cliente

    fun buscarPorId(id: Long): Cliente?

    fun buscarPorRut(rutNormalizado: String): Cliente?

    fun buscar(
        texto: String?,
        paginacion: Paginacion,
    ): Pagina<Cliente>

    fun eliminar(id: Long)
}

interface CatalogoRepositorio {
    fun guardar(item: ItemCatalogo): ItemCatalogo

    fun buscarPorId(id: Long): ItemCatalogo?

    fun buscar(
        texto: String?,
        soloActivos: Boolean,
        paginacion: Paginacion,
    ): Pagina<ItemCatalogo>

    fun existeCodigo(
        codigo: String,
        excluirId: Long?,
    ): Boolean
}

interface EmpresaRepositorio {
    fun obtener(): Empresa

    fun guardar(empresa: Empresa): Empresa

    fun obtenerLogo(): ByteArray?

    fun guardarLogo(
        contenido: ByteArray?,
        tipo: String?,
    )

    /** Reserva atómicamente el siguiente folio correlativo. */
    fun reservarFolio(): Long
}

data class FiltroCotizaciones(
    val texto: String? = null,
    val estado: EstadoCotizacion? = null,
    val clienteId: Long? = null,
    val vendedor: String? = null,
    /** Si es false, solo la última versión de cada folio. */
    val todasLasVersiones: Boolean = false,
)

data class ResumenEstado(
    val estado: EstadoCotizacion,
    val cantidad: Long,
    val total: Long,
)

interface CotizacionRepositorio {
    fun guardar(cotizacion: Cotizacion): Cotizacion

    fun buscarPorId(id: Long): Cotizacion?

    fun eliminar(id: Long)

    fun versiones(folio: Long): List<Cotizacion>

    fun buscar(
        filtro: FiltroCotizaciones,
        paginacion: Paginacion,
    ): Pagina<Cotizacion>

    fun enviadasConValidezAnteriorA(fecha: LocalDate): List<Cotizacion>

    fun contarPorCliente(clienteId: Long): Long

    fun anonimizarCliente(clienteId: Long)

    fun registrarEvento(evento: EventoCotizacion)

    fun historial(cotizacionId: Long): List<EventoCotizacion>

    fun resumenPorEstado(): List<ResumenEstado>
}

/** Límite de uso de la versión de prueba (sin licencia). */
interface PoliticaDeUso {
    fun verificarPuedeCrearCotizacion(totalCreadas: Long)

    fun contarCotizacionesCreadas(): Long = 0
}

/** Unidad de trabajo transaccional; la implementa la capa de infraestructura. */
interface Transacciones {
    fun <T> ejecutar(bloque: () -> T): T
}
