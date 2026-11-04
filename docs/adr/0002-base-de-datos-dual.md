# 0002. SQLite en el escritorio, PostgreSQL en la demo, con SQL portable

**Estado:** aceptada

## Decisión
- Persistencia con `JdbcClient` y SQL escrito a mano (sin JPA), y migraciones Flyway **únicas** con dos marcadores:
  `${id}` (clave autoincremental) y `${blob}` (binario), que cada perfil define.
- Fechas de calendario como texto ISO-8601 (`YYYY-MM-DD`) e instantes como milisegundos epoch (`BIGINT`).
- Inserciones con `INSERT … RETURNING id`, soportado por SQLite ≥ 3.35 y PostgreSQL.
- Pool de **una** conexión para SQLite (un solo escritor): evita `SQLITE_BUSY` y permite restaurar respaldos en caliente.
  Por eso los `RowMapper` nunca hacen consultas anidadas (con una sola conexión, fuera de una transacción, se bloquearían).

## Verificación
La clase abstracta `EscenarioCotizacion` contiene la suite de integración y se ejecuta dos veces: `EscenarioSqliteTest`
y `EscenarioPostgresTest` (Testcontainers, PostgreSQL 17).

## Alternativas descartadas
H2 en modo archivo (menos robusto y menos estándar para respaldos); JPA/Hibernate (el dialecto de SQLite es comunitario
y agrega complejidad sin beneficio para un modelo pequeño).
