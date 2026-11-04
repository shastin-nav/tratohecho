package cl.tratohecho.app.config

import cl.tratohecho.app.seguridad.FiltroAccesoLocal
import cl.tratohecho.app.seguridad.FiltroCabeceras
import cl.tratohecho.app.seguridad.FiltroLimiteSolicitudes
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

@Configuration(proxyBeanMethods = false)
class ConfiguracionWeb : WebMvcConfigurer {
    /** Sirve la interfaz React compilada; las rutas del enrutador del navegador vuelven a index.html. */
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(
                object : PathResourceResolver() {
                    override fun getResource(
                        resourcePath: String,
                        location: Resource,
                    ): Resource? {
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) return null
                        val solicitado = location.createRelative(resourcePath)
                        if (solicitado.exists() && solicitado.isReadable) return solicitado
                        val indice = ClassPathResource("/static/index.html")
                        return if (!resourcePath.contains('.') && indice.exists()) indice else null
                    }
                },
            )
    }

    @Bean
    fun filtroCabeceras() =
        FilterRegistrationBean(FiltroCabeceras()).apply {
            order = Ordered.HIGHEST_PRECEDENCE
        }

    @Bean
    @ConditionalOnProperty("tratohecho.acceso-local.habilitado", havingValue = "true")
    fun filtroAccesoLocal(propiedades: PropiedadesTratohecho): FilterRegistrationBean<FiltroAccesoLocal> {
        val token =
            propiedades.accesoLocal.token.ifBlank {
                val generado = ByteArray(24).also(SecureRandom()::nextBytes).let(Base64.getUrlEncoder().withoutPadding()::encodeToString)
                LoggerFactory.getLogger(javaClass).warn("Sin token del lanzador; abre la app con ?t={}", generado)
                generado
            }
        return FilterRegistrationBean(FiltroAccesoLocal(token)).apply { order = Ordered.HIGHEST_PRECEDENCE + 1 }
    }

    @Bean
    @Profile("demo")
    fun filtroLimite(
        propiedades: PropiedadesTratohecho,
        reloj: Clock,
    ) = FilterRegistrationBean(FiltroLimiteSolicitudes(propiedades.demo.solicitudesPorMinuto, reloj)).apply {
        order = Ordered.HIGHEST_PRECEDENCE + 2
    }
}
