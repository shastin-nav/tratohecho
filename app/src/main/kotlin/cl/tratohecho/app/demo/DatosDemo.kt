package cl.tratohecho.app.demo

import cl.tratohecho.core.application.CatalogoServicio
import cl.tratohecho.core.application.ClienteServicio
import cl.tratohecho.core.application.CotizacionServicio
import cl.tratohecho.core.application.EmpresaServicio
import cl.tratohecho.core.application.Transacciones
import cl.tratohecho.core.domain.Aceptacion
import cl.tratohecho.core.domain.Cantidad
import cl.tratohecho.core.domain.Cliente
import cl.tratohecho.core.domain.DescuentoGlobal
import cl.tratohecho.core.domain.Empresa
import cl.tratohecho.core.domain.ItemCatalogo
import cl.tratohecho.core.domain.LineaCotizacion
import cl.tratohecho.core.domain.MedioAceptacion
import cl.tratohecho.core.domain.Porcentaje
import cl.tratohecho.core.domain.Rut
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/** Demo pública: borra todo y carga datos ficticios al iniciar y cada hora. Nunca se guardan datos reales. */
@Component
@Profile("demo")
class DatosDemo(
    private val jdbc: JdbcClient,
    private val tx: Transacciones,
    private val empresa: EmpresaServicio,
    private val clientes: ClienteServicio,
    private val catalogo: CatalogoServicio,
    private val cotizaciones: CotizacionServicio,
    private val reloj: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun alIniciar() = reiniciar()

    @Scheduled(cron = "\${tratohecho.demo.reinicio-cron}")
    fun reiniciar() {
        tx.ejecutar {
            listOf("evento_cotizacion", "linea_cotizacion", "cotizacion", "cliente", "item_catalogo", "licencia")
                .forEach { jdbc.sql("DELETE FROM $it").update() }
            jdbc.sql("UPDATE empresa SET logo = NULL, logo_tipo = NULL, siguiente_folio = 1001 WHERE id = 1").update()
            cargar()
        }
        log.info("Datos de la demo reiniciados")
    }

    private fun cargar() {
        empresa.actualizar(
            Empresa(
                razonSocial = "Climatización Austral SpA (empresa ficticia)",
                rut = Rut.de(77_123_456),
                giro = "Instalación y mantención de equipos de climatización",
                direccion = "Av. Siempre Viva 742",
                comuna = "Providencia",
                ciudad = "Santiago",
                telefono = "+56 2 2345 6789",
                email = "ventas@ejemplo.cl",
                sitioWeb = "www.ejemplo.cl",
                diasValidez = 15,
                formaPago = "50 % al aceptar, 50 % contra entrega. Transferencia bancaria.",
                plazoEntrega = "5 días hábiles desde la aceptación",
                garantia = "12 meses en mano de obra; equipos según garantía del fabricante",
                condiciones = "Precios sujetos a visita técnica. No incluye obras civiles ni permisos municipales.",
            ),
        )
        val items =
            listOf(
                ItemCatalogo(codigo = "SPL-09", nombre = "Equipo split muro 9.000 BTU inverter", unidad = "un", precioNeto = 289_900),
                ItemCatalogo(codigo = "SPL-12", nombre = "Equipo split muro 12.000 BTU inverter", unidad = "un", precioNeto = 349_900),
                ItemCatalogo(
                    codigo = "INS-STD",
                    nombre = "Instalación estándar (hasta 3 m de cañería)",
                    unidad = "un",
                    precioNeto = 120_000,
                ),
                ItemCatalogo(codigo = "CAN-M", nombre = "Cañería de cobre adicional", unidad = "m", precioNeto = 18_500),
                ItemCatalogo(codigo = "MAN-PRV", nombre = "Mantención preventiva split", unidad = "un", precioNeto = 35_000),
                ItemCatalogo(codigo = "VIS-TEC", nombre = "Visita técnica de diagnóstico", unidad = "un", precioNeto = 25_000),
                ItemCatalogo(
                    codigo = "CAP-MAN",
                    nombre = "Capacitación de uso (exenta)",
                    unidad = "h",
                    precioNeto = 20_000,
                    afectoIva = false,
                ),
            ).map(catalogo::crear)

        val c =
            listOf(
                Cliente(
                    rut = Rut.de(76_543_210),
                    razonSocial = "Panadería Las Delicias Ltda.",
                    giro = "Panadería",
                    contacto = "Rosa Muñoz",
                    email = "rosa@ejemplo.cl",
                    comuna = "Ñuñoa",
                    ciudad = "Santiago",
                ),
                Cliente(
                    rut = Rut.de(77_888_999),
                    razonSocial = "Clínica Dental Sonrisa SpA",
                    giro = "Servicios odontológicos",
                    contacto = "Dr. Pablo Soto",
                    email = "pablo@ejemplo.cl",
                    comuna = "Las Condes",
                    ciudad = "Santiago",
                ),
                Cliente(
                    rut = Rut.de(76_111_222),
                    razonSocial = "Oficinas Andinas S.A.",
                    giro = "Arriendo de oficinas",
                    contacto = "Camila Rojas",
                    telefono = "+56 9 8765 4321",
                    comuna = "Santiago Centro",
                    ciudad = "Santiago",
                ),
                Cliente(
                    razonSocial = "Juan Pérez (persona natural)",
                    contacto = "Juan Pérez",
                    telefono = "+56 9 1234 5678",
                    comuna = "Maipú",
                    ciudad = "Santiago",
                ),
            ).map(clientes::crear)

        val hoy = LocalDate.now(reloj)

        fun linea(
            i: Int,
            cantidad: String,
            descuento: String = "0",
        ) = items[i].let {
            LineaCotizacion(
                it.nombre,
                Cantidad.de(cantidad),
                it.precioNeto,
                it.unidad,
                Porcentaje.de(descuento),
                it.afectoIva,
                it.codigo,
                it.id,
            )
        }

        fun contenido(
            lineas: List<LineaCotizacion>,
            descuento: DescuentoGlobal = DescuentoGlobal.Ninguno,
            vendedor: String = "Andrea",
            dias: Long = 15,
        ) = cotizaciones.plantilla().copy(
            vendedor = vendedor,
            lineas = lineas,
            descuentoGlobal = descuento,
            fechaValidez = hoy.plusDays(dias),
        )

        val panaderia = cotizaciones.crear(c[0].id!!, contenido(listOf(linea(1, "2"), linea(2, "2"), linea(3, "4.5"))))
        cotizaciones.enviar(panaderia.id!!)
        cotizaciones.aceptar(panaderia.id!!, Aceptacion(hoy, "Rosa Muñoz", MedioAceptacion.CORREO, "Confirmó por correo"))

        val clinica =
            cotizaciones.crear(
                c[1].id!!,
                contenido(listOf(linea(0, "4", "5"), linea(2, "4"), linea(6, "2")), DescuentoGlobal.PorPorcentaje(Porcentaje.de("3"))),
            )
        cotizaciones.enviar(clinica.id!!)

        val oficinas = cotizaciones.crear(c[2].id!!, contenido(listOf(linea(4, "12")), vendedor = "Martín"))
        cotizaciones.enviar(oficinas.id!!)
        val v2 = cotizaciones.nuevaVersion(oficinas.id!!)
        cotizaciones.actualizar(v2.id!!, c[2].id!!, contenido(listOf(linea(4, "12", "10")), vendedor = "Martín"))

        val persona = cotizaciones.crear(c[3].id!!, contenido(listOf(linea(5, "1")), vendedor = "Martín"))
        cotizaciones.enviar(persona.id!!)
        cotizaciones.rechazar(persona.id!!, "Lo resolverá más adelante")

        cotizaciones.crear(c[0].id!!, contenido(listOf(linea(4, "3")), DescuentoGlobal.PorMonto(5_000)))
    }
}
