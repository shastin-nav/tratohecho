# 0007. Protección de datos personales (Ley 21.719)

**Estado:** aceptada

La Ley 21.719 rige desde el 1 de diciembre de 2026 (hay un proyecto de ley que propone aplazarla a 2027). Con la
instalación local, **la pyme es la responsable** de los datos de sus clientes y el fabricante del software no los
trata, siempre que no tenga acceso remoto.

Decisiones de diseño:
- Sin telemetría ni envío de datos a terceros.
- **Acceso y portabilidad:** `GET /api/clientes/{id}/exportar` entrega en JSON los datos del cliente y sus cotizaciones.
- **Supresión:** si el cliente no tiene cotizaciones se borra; si las tiene, se anonimizan sus datos personales
  (también en las copias guardadas en cada cotización) y se conservan los montos para la historia comercial.
- **Demo pública:** solo datos ficticios, aviso visible de no ingresar datos reales y borrado cada hora.
