package cl.visenda.app.dominio

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Ajustes > Planificación. Horas en minutos desde las 00:00.
 * colacion = 0: sin colación. volver: la ruta termina en el punto de partida (sus km cuentan al elegir).
 */
data class Reglas(
    val minutosVisita: Int = 15,
    val diasAviso: Int = 5,
    val salida: Int = 8 * 60 + 30,
    val finJornada: Int = 18 * 60,
    val colacion: Int = 45,
    val colacionDesde: Int = 13 * 60,
    val colacionHasta: Int = 15 * 60,
    val sabados: Boolean = false,
    val volver: Boolean = true,
)

val MINUTOS_VISITA = 5..180
val DIAS_AVISO = 1..20

/** Problemas que impiden guardar los ajustes; vacío si se pueden guardar. */
fun validarReglas(r: Reglas): List<String> {
    val errores = mutableListOf<String>()
    if (r.minutosVisita !in MINUTOS_VISITA) errores += "El tiempo por visita debe estar entre ${MINUTOS_VISITA.first} y ${MINUTOS_VISITA.last} minutos."
    if (r.diasAviso !in DIAS_AVISO) errores += "El aviso de riesgo debe estar entre ${DIAS_AVISO.first} y ${DIAS_AVISO.last} días."
    if (r.salida + r.minutosVisita > r.finJornada) errores += "El fin de jornada debe ser después de la salida y dejar tiempo para una visita."
    if (r.colacion < 0) errores += "La colación no puede ser negativa."
    if (r.colacion > 0 && r.colacionDesde + r.colacion > r.colacionHasta) errores += "La colación no cabe entre ${hora(r.colacionDesde)} y ${hora(r.colacionHasta)}."
    return errores
}

/** "8:30", "08.30" o "830" (el teclado numérico no siempre tiene dos puntos) → minutos; null si no es una hora válida. */
fun leerHora(texto: String): Int? =
    Regex("""(\d{1,2})\D?(\d{2})""").matchEntire(texto.trim())?.destructured
        ?.let { (h, m) -> (h.toInt() to m.toInt()).takeIf { (h, m) -> h < 24 && m < 60 }?.let { (h, m) -> h * 60 + m } }

/**
 * Feriados de Chile con su traslado ya aplicado (Ley 19.668, Ley 20.299 y el día de los Pueblos Indígenas según el solsticio).
 * shortcut: tabla fija hasta 2027; agregar cada año (o leerla junto con los mapas) antes de que se acabe.
 */
val FERIADOS: Set<LocalDate> = listOf(
    "2026-01-01", "2026-04-03", "2026-04-04", "2026-05-01", "2026-05-21", "2026-06-21", "2026-06-29", "2026-07-16",
    "2026-08-15", "2026-09-18", "2026-09-19", "2026-10-12", "2026-10-31", "2026-11-01", "2026-12-08", "2026-12-25",
    "2027-01-01", "2027-03-26", "2027-03-27", "2027-05-01", "2027-05-21", "2027-06-21", "2027-06-28", "2027-07-16",
    "2027-08-15", "2027-09-18", "2027-09-19", "2027-10-11", "2027-10-31", "2027-11-01", "2027-12-08", "2027-12-25",
).map(LocalDate::parse).toSet()

/** Día en que el visitador trabaja: lunes a viernes (y sábado si lo eligió), sin feriados. */
fun Reglas.esHabil(dia: LocalDate) =
    dia !in FERIADOS && dia.dayOfWeek != DayOfWeek.SUNDAY && (sabados || dia.dayOfWeek != DayOfWeek.SATURDAY)

/** Días hábiles entre hoy y el fin del ciclo, incluido hoy. */
fun Reglas.diasHabilesRestantes(hoy: LocalDate, fin: LocalDate): Int =
    generateSequence(hoy) { it.plusDays(1) }.takeWhile { !it.isAfter(fin) }.count { esHabil(it) }

/** El primer día hábil después de [dia]. */
fun Reglas.habilSiguiente(dia: LocalDate): LocalDate = generateSequence(dia.plusDays(1)) { it.plusDays(1) }.first { esHabil(it) }
