package cl.visenda.app.dominio

import cl.visenda.app.data.Categoria
import cl.visenda.app.data.Ciclo
import cl.visenda.app.importar.normalizar
import java.time.LocalDate

val FRECUENCIAS = 1..30

/** Problemas que impiden guardar una empresa; vacío si se puede guardar. otrosNombres: las demás empresas. */
fun validarEmpresa(nombre: String, otrosNombres: List<String>, frecuenciaDefecto: Int, ciclos: List<Ciclo>, categorias: List<Categoria>): List<String> {
    val errores = mutableListOf<String>()
    if (nombre.isBlank()) errores += "Falta el nombre de la empresa."
    else if (otrosNombres.any { normalizar(it) == normalizar(nombre) }) errores += "Ya hay otra empresa llamada así."
    if (frecuenciaDefecto !in FRECUENCIAS) errores += "La frecuencia por defecto debe estar entre ${FRECUENCIAS.first} y ${FRECUENCIAS.last}."
    ciclos.filter { it.inicio > it.fin }.forEach { errores += "${it.nombre.ifBlank { "Un ciclo" }} termina antes de empezar." }
    if (ciclos.any { it.nombre.isBlank() }) errores += "Todos los ciclos necesitan un nombre."
    // RN-02: los ciclos de una empresa no se solapan.
    ciclos.sortedBy { it.inicio }.zipWithNext().filter { (a, b) -> b.inicio <= a.fin }.forEach { (a, b) -> errores += "${a.nombre} y ${b.nombre} se solapan." }
    if (categorias.any { it.nombre.isBlank() }) errores += "Todas las categorías necesitan un nombre."
    if (categorias.map { normalizar(it.nombre) }.let { it.size != it.toSet().size }) errores += "Hay categorías con el mismo nombre."
    if (categorias.any { it.frecuencia !in FRECUENCIAS }) errores += "Las visitas por categoría deben estar entre ${FRECUENCIAS.first} y ${FRECUENCIAS.last}."
    return errores
}

/** El ciclo que sigue al último: el mes calendario siguiente, o el actual si no hay ninguno. */
fun cicloSiguiente(ciclos: List<Ciclo>, empresaId: Long, hoy: LocalDate): Ciclo {
    val desde = ciclos.maxOfOrNull { it.fin }?.let { LocalDate.ofEpochDay(it).plusDays(1) } ?: hoy.withDayOfMonth(1)
    val fin = desde.withDayOfMonth(desde.lengthOfMonth())
    val mes = desde.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("es-CL"))
    return Ciclo(0, empresaId, "Ciclo $mes", desde.toEpochDay(), fin.toEpochDay())
}
