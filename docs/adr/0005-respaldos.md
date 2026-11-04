# 0005. Respaldos de la base local

**Estado:** aceptada

- **Crear:** `VACUUM INTO` produce una copia consistente aunque la app esté en uso. Se empaqueta en un `.zip` junto a un
  manifiesto (`aplicacion`, `versionAplicacion`, `versionEsquema`, `creado`).
- **Restaurar:** se valida el manifiesto, que la versión del esquema no sea más nueva que la de la app, la cabecera
  SQLite y `PRAGMA integrity_check`; luego se respalda el estado actual y se usa `restore from` de sqlite-jdbc sobre
  la única conexión del pool (sin reiniciar). Por último, `flyway.migrate()` actualiza respaldos antiguos.
- **Automático:** un respaldo al iniciar, conservando los últimos 7.
- La descarga valida que el nombre no salga de la carpeta de respaldos (path traversal).
