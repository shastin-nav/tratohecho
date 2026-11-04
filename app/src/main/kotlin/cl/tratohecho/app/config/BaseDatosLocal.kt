package cl.tratohecho.app.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

/** SQLite en la carpeta de datos del usuario. */
@Configuration(proxyBeanMethods = false)
@Profile("local")
class BaseDatosLocal {
    @Bean
    fun dataSource(propiedades: PropiedadesTratohecho): DataSource {
        val directorio = propiedades.datos.directorio
        Files.createDirectories(directorio)
        return HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = urlSqlite(directorio.resolve(ARCHIVO))
                driverClassName = "org.sqlite.JDBC"
                // SQLite admite un solo escritor: una conexión evita SQLITE_BUSY y permite restaurar respaldos en caliente.
                maximumPoolSize = 1
                poolName = "sqlite"
            },
        )
    }

    companion object {
        const val ARCHIVO = "tratohecho.db"

        fun urlSqlite(archivo: Path) = "jdbc:sqlite:${archivo.toAbsolutePath()}?foreign_keys=on&journal_mode=wal&busy_timeout=5000"
    }
}
