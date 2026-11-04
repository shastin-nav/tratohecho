package cl.tratohecho.app.persistencia

import java.sql.ResultSet

internal fun ResultSet.longONull(columna: String): Long? = getLong(columna).takeUnless { wasNull() }

internal fun ResultSet.textoONull(columna: String): String? = getString(columna)?.takeIf { it.isNotEmpty() }

/** Escapa comodines de LIKE y arma el patrón "contiene" sin distinguir mayúsculas. */
internal fun patronContiene(texto: String): String =
    "%" +
        texto
            .trim()
            .lowercase()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_") + "%"

internal fun String?.normalizado(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
