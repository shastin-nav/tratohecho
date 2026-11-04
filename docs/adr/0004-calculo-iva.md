# 0004. Montos en pesos enteros e IVA sobre el total

**Estado:** aceptada

## Contexto
El peso chileno no tiene decimales. El criterio del SII (dictamen 3257 de 2009) calcula el IVA sobre el neto total y
redondea al final, no línea a línea. Las cantidades pueden ser fraccionarias (2,5 m²).

## Decisión
- Montos como `Long` en pesos; cantidades con hasta 3 decimales guardadas como milésimas; porcentajes en puntos básicos.
- Línea: `redondear(cantidad × precio) − redondear(bruto × % descuento)`.
- El descuento global se reparte entre lo afecto y lo exento en proporción a sus subtotales.
- `IVA = redondear(neto afecto × tasa)`, una sola vez. Redondeo `HALF_UP` al peso.
- El servidor es la única fuente de verdad: el editor llama a `POST /api/cotizaciones/calcular` en vez de recalcular en JavaScript.

## Verificación
Casos con montos conocidos en `CalculadoraCotizacionTest` (incluido uno donde el IVA línea a línea daría $57 y sobre el
total da $58) y pruebas basadas en propiedades (total = neto + exento + IVA; descuentos dentro de rango).
