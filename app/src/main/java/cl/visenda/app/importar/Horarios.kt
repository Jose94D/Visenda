package cl.visenda.app.importar

/** Un tramo leído del kardex: día 1 = lunes … 7 = domingo, horas en minutos desde las 00:00. */
data class Tramo(val dia: Int, val abre: Int, val cierra: Int)

private val DIA = Regex("""^(lunes|lun|lu|l|martes|mar|ma|m|miercoles|mie|mi|x|jueves|jue|ju|j|viernes|vie|vi|v|sabado|sab|sa|s|domingo|dom|do|d)\.?$""")
private val INICIALES = mapOf('l' to 1, 'm' to 2, 'x' to 3, 'j' to 4, 'v' to 5, 's' to 6, 'd' to 7)
private val HORA = """(\d{1,2})(?:[:.](\d{2}))?\s*(?:hrs?\.?|h)?"""
private val RANGO_HORAS = Regex("""$HORA\s*(?:-|–|\ba\b|\bal\b|\bhasta\b)\s*$HORA""")

// Un nuevo grupo de días empieza tras una hora: "Lun-Vie 9-13 Sáb 10-12", "… 9-13, Sáb 10-12".
private val CORTE = Regex("""(?<=\d)\s*(?:hrs?\.?|h)?\s*[,;/]?\s*(?=(?:lu|ma|mi|ju|vi|sa|do)[a-z]*\.?\s|[lmxjvsd]\.?\s)""")

/**
 * Interpreta un horario del kardex (Anexo A de la especificación). Devuelve null si no lo entiende o si el
 * resultado viola RN-08 (abre < cierra, sin cruzar medianoche, sin tramos solapados): queda "por revisar".
 */
fun interpretarHorario(texto: String): List<Tramo>? {
    val limpio = normalizar(texto).replace(Regex("""\b(de|desde|horario)\b"""), " ").replace(Regex("""\s+"""), " ").trim()
    if (limpio.isEmpty()) return null
    val tramos = limpio.split(Regex("""\s*[;\n|]\s*""")).flatMap { it.split(CORTE) }.filter { it.isNotBlank() }.flatMap { grupo ->
        val corte = grupo.indexOfFirst { it.isDigit() }
        if (corte <= 0) return null
        val dias = dias(grupo.substring(0, corte)) ?: return null
        val horas = horas(grupo.substring(corte)) ?: return null
        dias.flatMap { d -> horas.map { (a, c) -> Tramo(d, a, c) } }
    }
    val solapados = tramos.groupBy { it.dia }.values.any { delDia ->
        delDia.sortedBy { it.abre }.zipWithNext().any { (a, b) -> b.abre < a.cierra }
    }
    return tramos.takeIf { it.isNotEmpty() && !solapados }?.sortedWith(compareBy({ it.dia }, { it.abre }))
}

/** "lun a vie", "l-v", "lun, mie y vie" → [1..5], [1..5], [1,3,5]. */
private fun dias(texto: String): List<Int>? {
    val partes = texto.replace(Regex("""\s+(?:a|al)\s+|\s*-\s*"""), "-").split(Regex("""\s*,\s*|\s+y\s+|\s+""")).filter { it.isNotBlank() }
    if (partes.isEmpty()) return null
    return partes.flatMap { p ->
        val extremos = p.split("-").map { dia(it) ?: return null }
        when (extremos.size) {
            1 -> extremos
            2 -> if (extremos[0] <= extremos[1]) (extremos[0]..extremos[1]).toList() else return null
            else -> return null
        }
    }.distinct()
}

private fun dia(token: String): Int? {
    val t = token.trim()
    if (!DIA.matches(t)) return null
    return if (t.trimEnd('.').length == 1) INICIALES[t[0]] else when (t.take(2)) {
        "lu" -> 1; "ma" -> 2; "mi" -> 3; "ju" -> 4; "vi" -> 5; "sa" -> 6; else -> 7
    }
}

/** "9:00-13:00 / 15 a 19" → [(540, 780), (900, 1140)]. Todo el texto debe ser horas y separadores. */
private fun horas(texto: String): List<Pair<Int, Int>>? {
    val encontrados = RANGO_HORAS.findAll(texto).toList()
    val sobra = RANGO_HORAS.replace(texto, "").replace(Regex("""\b(y|hrs?|h)\b|[\s/,.]"""), "")
    if (encontrados.isEmpty() || sobra.isNotEmpty()) return null
    return encontrados.map { m ->
        val (h1, m1, h2, m2) = m.destructured
        val abre = h1.toInt() * 60 + (m1.toIntOrNull() ?: 0)
        val cierra = h2.toInt() * 60 + (m2.toIntOrNull() ?: 0)
        if ((m1.toIntOrNull() ?: 0) > 59 || (m2.toIntOrNull() ?: 0) > 59 || abre >= cierra || cierra > 24 * 60) return null
        abre to cierra
    }
}
