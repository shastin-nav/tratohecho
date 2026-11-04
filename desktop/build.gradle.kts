import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":app"))
    implementation("org.springframework.boot:spring-boot")
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("tratohecho.jar")
    mainClass.set("cl.tratohecho.desktop.LanzadorKt")
}

tasks.named<Jar>("jar") { enabled = false }

// ---- Instaladores nativos con jpackage (se ejecuta en cada sistema operativo; ver .github/workflows/release.yml) ----
val so = System.getProperty("os.name").lowercase()
val tipoInstalador =
    (findProperty("tipoInstalador") as String?) ?: when {
        so.contains("win") -> "msi"
        so.contains("mac") -> "dmg"
        else -> "deb"
    }
// jpackage exige versiones numéricas (1.2.3); "0.1.0-rc1" → "0.1.0"
val versionNumerica = project.version.toString().substringBefore('-')

val prepararJpackage by tasks.registering(Sync::class) {
    from(tasks.named("bootJar"))
    into(layout.buildDirectory.dir("jpackage/entrada"))
}

tasks.register<Exec>("empaquetar") {
    group = "tratohecho"
    description = "Genera el instalador nativo del sistema operativo actual (msi, dmg, deb o app-image)."
    dependsOn(prepararJpackage)
    val salida = layout.buildDirectory.dir("instaladores")
    doFirst { delete(salida) }
    val empaquetado = layout.projectDirectory.dir("empaquetado")
    val argumentos =
        mutableListOf(
            "jpackage",
            "--type",
            tipoInstalador,
            "--name",
            "Tratohecho",
            "--app-version",
            versionNumerica,
            "--vendor",
            "Tratohecho",
            "--description",
            "Cotizaciones profesionales para pymes. Cotiza, envía y cierra.",
            "--copyright",
            "© Tratohecho",
            "--input",
            layout.buildDirectory
                .dir("jpackage/entrada")
                .get()
                .asFile.path,
            "--main-jar",
            "tratohecho.jar",
            "--dest",
            salida.get().asFile.path,
            "--java-options",
            "-Xmx512m -Dfile.encoding=UTF-8 -Duser.language=es -Duser.country=CL",
            "--add-modules",
            listOf(
                "java.base",
                "java.desktop",
                "java.instrument",
                "java.logging",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.prefs",
                "java.scripting",
                "java.security.jgss",
                "java.sql",
                "java.xml",
                "jdk.crypto.ec",
                "jdk.unsupported",
                "jdk.zipfs",
                "jdk.localedata",
                "jdk.charsets",
                "jdk.management",
            ).joinToString(","),
        )
    when {
        so.contains("win") ->
            argumentos +=
                listOf(
                    "--icon",
                    empaquetado.file("icono.ico").asFile.path,
                    "--win-menu",
                    "--win-menu-group",
                    "Tratohecho",
                    "--win-shortcut",
                    "--win-dir-chooser",
                    "--win-per-user-install",
                    "--win-upgrade-uuid",
                    "6f1c0e2a-5b7d-4c1e-9a3f-2d8b7e4c9a10",
                )
        so.contains("mac") -> {
            argumentos += listOf("--icon", empaquetado.file("icono.icns").asFile.path, "--mac-package-identifier", "cl.tratohecho.app")
            // Firma con certificado Developer ID si está configurado (la notarización ocurre después, en CI).
            System.getenv("MAC_FIRMA_IDENTIDAD")?.takeIf { it.isNotBlank() }?.let {
                argumentos += listOf("--mac-sign", "--mac-signing-key-user-name", it)
            }
        }
        else ->
            argumentos +=
                listOf("--icon", empaquetado.file("icono.png").asFile.path, "--linux-shortcut", "--linux-menu-group", "Office")
                    .takeIf { tipoInstalador != "app-image" }
                    ?: listOf("--icon", empaquetado.file("icono.png").asFile.path)
    }
    commandLine(argumentos)
}
