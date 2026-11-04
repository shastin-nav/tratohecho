package cl.tratohecho.core.domain

/**
 * RUT chileno validado con el algoritmo de módulo 11.
 * Acepta entradas como "12.345.678-5", "12345678-5" o "123456785".
 */
class Rut private constructor(
    val cuerpo: Int,
    val dv: Char,
) {
    /** Formato de presentación: 12.345.678-5 */
    fun formateado(): String {
        val conPuntos =
            cuerpo
                .toString()
                .reversed()
                .chunked(3)
                .joinToString(".")
                .reversed()
        return "$conPuntos-$dv"
    }

    /** Formato de almacenamiento: 12345678-5 */
    fun normalizado(): String = "$cuerpo-$dv"

    override fun toString(): String = formateado()

    override fun equals(other: Any?): Boolean = other is Rut && other.cuerpo == cuerpo && other.dv == dv

    override fun hashCode(): Int = cuerpo * 31 + dv.code

    companion object {
        private const val CUERPO_MAXIMO = 99_999_999

        fun parse(texto: String): Rut = parseOrNull(texto) ?: throw DatoInvalidoException("rut", "El RUT \"$texto\" no es válido")

        fun parseOrNull(texto: String): Rut? {
            val limpio =
                texto
                    .trim()
                    .replace(".", "")
                    .replace("-", "")
                    .replace(" ", "")
                    .uppercase()
            if (limpio.length < 2) return null
            val cuerpoTexto = limpio.dropLast(1)
            val dv = limpio.last()
            if (!cuerpoTexto.all { it.isDigit() }) return null
            val cuerpo = cuerpoTexto.toIntOrNull() ?: return null
            if (cuerpo !in 1..CUERPO_MAXIMO) return null
            if (calcularDv(cuerpo) != dv) return null
            return Rut(cuerpo, dv)
        }

        fun esValido(texto: String): Boolean = parseOrNull(texto) != null

        fun calcularDv(cuerpo: Int): Char {
            require(cuerpo > 0) { "El cuerpo del RUT debe ser positivo" }
            var suma = 0
            var factor = 2
            var resto = cuerpo
            while (resto > 0) {
                suma += (resto % 10) * factor
                resto /= 10
                factor = if (factor == 7) 2 else factor + 1
            }
            return when (val dv = 11 - (suma % 11)) {
                11 -> '0'
                10 -> 'K'
                else -> ('0' + dv)
            }
        }

        fun de(cuerpo: Int): Rut = Rut(cuerpo, calcularDv(cuerpo))
    }
}
