package cl.tratohecho.app

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class ArquitecturaTest {
    private val clases = ClassFileImporter().importPackages("cl.tratohecho")

    @Test
    fun `el dominio no depende de frameworks ni de la infraestructura`() {
        noClasses()
            .that()
            .resideInAPackage("cl.tratohecho.core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "java.sql..", "javax.sql..", "tools.jackson..", "cl.tratohecho.app..")
            .check(clases)
    }

    @Test
    fun `la persistencia no conoce la capa web`() {
        noClasses()
            .that()
            .resideInAPackage("cl.tratohecho.app.persistencia..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("cl.tratohecho.app.web..")
            .check(clases)
    }
}
