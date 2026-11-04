plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":core"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")
    implementation("org.thymeleaf:thymeleaf")
    implementation("io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.108")

    runtimeOnly("org.xerial:sqlite-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.tngtech.archunit:archunit:1.4.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("tratohecho-servidor.jar")
}

// ---- Interfaz web (React): se compila con npm y se empaqueta en /static del jar ----
val frontend = rootProject.layout.projectDirectory.dir("frontend")
val npm = if (System.getProperty("os.name").lowercase().contains("win")) "npm.cmd" else "npm"

val npmCi by tasks.registering(Exec::class) {
    workingDir = frontend.asFile
    commandLine(npm, "ci", "--no-audit", "--no-fund")
    inputs.file(frontend.file("package-lock.json"))
    outputs.dir(frontend.dir("node_modules"))
}

val compilarFrontend by tasks.registering(Exec::class) {
    dependsOn(npmCi)
    workingDir = frontend.asFile
    commandLine(npm, "run", "build")
    inputs.dir(frontend.dir("src"))
    inputs.files(frontend.file("index.html"), frontend.file("vite.config.ts"), frontend.file("package-lock.json"))
    inputs.dir(frontend.dir("public"))
    outputs.dir(frontend.dir("dist"))
}

// -PsinFrontend: compila y prueba solo el backend (más rápido en CI y en desarrollo).
if (!project.hasProperty("sinFrontend")) {
    tasks.named<ProcessResources>("processResources") {
        dependsOn(compilarFrontend)
        from(frontend.dir("dist")) { into("static") }
    }
}

springBoot {
    buildInfo()
}

tasks.register<JavaExec>("emitirLicencia") {
    group = "tratohecho"
    description = "Herramienta del vendedor: genera claves o firma licencias (ver EmisorLicencias.kt)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("cl.tratohecho.app.licencia.EmisorLicenciasKt")
}
