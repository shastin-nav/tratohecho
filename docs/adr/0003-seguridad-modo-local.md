# 0003. Seguridad del servidor local

**Estado:** aceptada

## Problema
Un servidor HTTP en `localhost` es alcanzable desde cualquier página web que el usuario tenga abierta (CSRF hacia localhost,
DNS rebinding) y desde otros usuarios del mismo equipo.

## Decisión
1. Escucha solo en `127.0.0.1`, en un puerto libre aleatorio.
2. El lanzador genera un token aleatorio de 256 bits por sesión y abre `/?t=<token>`. El filtro lo canjea por una cookie
   `HttpOnly; SameSite=Strict` y redirige para quitar el token de la URL y del historial.
3. `/api/**` exige la cookie (comparación en tiempo constante).
4. Solo se aceptan encabezados `Host` `127.0.0.1`, `localhost` o `[::1]` → bloquea DNS rebinding.
5. En peticiones que modifican datos, el `Origin` (si viene) debe ser local.
6. CSP `default-src 'self'` sin `unsafe-eval` (zod se configura en modo `jitless`).
7. La URL de la sesión, para reabrir desde una segunda instancia, se guarda en un archivo con permisos `600`.

## Verificación
`AccesoLocalTest` (401 sin cookie, 403 con host u origen ajeno, cookie con los atributos correctos) y la prueba E2E
"sin el token del lanzador la app no da acceso".
