# 0006. Licencias perpetuas verificadas sin conexión

**Estado:** aceptada

## Decisión
Una licencia es `TH1.<contenido base64url>.<firma Ed25519 base64url>`. El contenido incluye id, titular, RUT, tipo
(`INDIVIDUAL`/`EMPRESA`) y fecha. La app trae embebida la **clave pública** y verifica la firma sin servidor.
La clave privada solo la tiene el vendedor (`./gradlew :app:emitirLicencia`).

Sin licencia, la app funciona en **modo de prueba**: 10 cotizaciones y marca de agua en los PDF. Esto además permite
evaluar antes de comprar, porque el derecho a retracto no aplica a software ya descargado o activado (Ley 19.496 y su
reglamento de exclusiones), lo que se informa en los términos de venta.

## Consecuencias
- No hay que mantener un servidor de activación. Una licencia puede copiarse entre equipos: es un riesgo aceptado para
  el segmento pyme. Si hiciera falta, se puede atar la licencia a un identificador del equipo en una versión futura.
