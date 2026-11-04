# 0001. Una sola aplicación web, entregada como instalable y como demo

**Estado:** aceptada · **Fecha:** 2026-11-04

## Contexto
El producto se vende con **pago único** a pymes chilenas. Un SaaS alojado por nosotros implica costos de hosting
indefinidos que un pago único no cubre. A la vez, como proyecto de portafolio, conviene tener una demo pública.

## Decisión
Construir una aplicación web (Spring Boot + React) y distribuirla de dos formas:
- **Instalada** en el computador del cliente: un lanzador levanta el servidor en `127.0.0.1` y abre el navegador. Datos en SQLite local.
- **Demo pública**: la misma aplicación en Docker con el perfil `demo` (PostgreSQL y datos ficticios que se reinician cada hora).

## Consecuencias
- Cero costo de infraestructura por cliente; los datos quedan bajo control del cliente (ventaja para la Ley 21.719).
- Un solo código para ambos modos; la diferencia vive en perfiles de Spring (`local`, `demo`).
- El soporte multiusuario en red local queda abierto para v1.1 (un PC hace de servidor).
- Se descartó Compose Desktop: no permite una demo web y separa la interfaz de lo que se muestra en el portafolio.
