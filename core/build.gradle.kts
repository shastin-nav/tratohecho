// Módulo de dominio: Kotlin puro, sin Spring ni dependencias de infraestructura.
dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("io.kotest:kotest-property:6.2.6")
}
