package cl.tratohecho.app.config

import cl.tratohecho.core.application.CatalogoRepositorio
import cl.tratohecho.core.application.CatalogoServicio
import cl.tratohecho.core.application.ClienteRepositorio
import cl.tratohecho.core.application.ClienteServicio
import cl.tratohecho.core.application.CotizacionRepositorio
import cl.tratohecho.core.application.CotizacionServicio
import cl.tratohecho.core.application.EmpresaRepositorio
import cl.tratohecho.core.application.EmpresaServicio
import cl.tratohecho.core.application.PoliticaDeUso
import cl.tratohecho.core.application.Transacciones
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.ZoneId

/** Conecta los casos de uso del módulo `core` (Kotlin puro) con la infraestructura de Spring. */
@Configuration(proxyBeanMethods = false)
class ConfiguracionAplicacion {
    @Bean
    fun reloj(): Clock = Clock.system(ZoneId.of("America/Santiago"))

    @Bean
    fun transacciones(txManager: PlatformTransactionManager): Transacciones {
        val plantilla = TransactionTemplate(txManager)
        return object : Transacciones {
            override fun <T> ejecutar(bloque: () -> T): T {
                @Suppress("UNCHECKED_CAST")
                return plantilla.execute { bloque() } as T
            }
        }
    }

    @Bean
    fun clienteServicio(
        clientes: ClienteRepositorio,
        cotizaciones: CotizacionRepositorio,
        tx: Transacciones,
    ) = ClienteServicio(clientes, cotizaciones, tx)

    @Bean
    fun catalogoServicio(
        catalogo: CatalogoRepositorio,
        tx: Transacciones,
    ) = CatalogoServicio(catalogo, tx)

    @Bean
    fun empresaServicio(
        empresas: EmpresaRepositorio,
        tx: Transacciones,
    ) = EmpresaServicio(empresas, tx)

    @Bean
    fun cotizacionServicio(
        cotizaciones: CotizacionRepositorio,
        clientes: ClienteRepositorio,
        empresas: EmpresaRepositorio,
        tx: Transacciones,
        reloj: Clock,
        politica: PoliticaDeUso,
    ) = CotizacionServicio(cotizaciones, clientes, empresas, tx, reloj, politica)
}
