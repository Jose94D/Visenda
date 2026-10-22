package cl.visenda.app.dominio

import java.time.LocalDate
import kotlin.math.roundToInt

// Cuánto se justifica manejar por visitar a un cliente, en km. Un cliente sin apuro (1 visita, 10 días) vale ~6 km;
// uno en riesgo, 30 o más: desviarse 10 km se justifica por el segundo y no por el primero.
const val KM_POR_VISITA = 4.0
/** Se multiplica por la fracción de los días que le quedan atendiendo en que hay que visitarlo (1 = todos). */
const val KM_POR_URGENCIA = 16.0
const val KM_POR_RIESGO = 10.0

// Lo elegido a mano entra siempre que quepa, y entre ellos los en riesgo primero (RN-07): pesan más que cualquier desvío.
const val PREMIO_ELEGIDO = 1_000.0
const val PREMIO_ELEGIDO_EN_RIESGO = 1_000_000.0

/** faltan: visitas pendientes sumando empresas. dias: los que le quedan atendiendo en el ciclo más corto. */
data class Urgencia(val faltan: Int, val dias: Int, val enRiesgo: Boolean, val premio: Double)

/**
 * Qué tan urgente es visitar al cliente el día [fecha]. Por cada empresa con visitas pendientes suma
 * KM_POR_VISITA + KM_POR_URGENCIA × faltan / días que le quedan atendiendo (+ KM_POR_RIESGO si está en riesgo):
 * una visita que cuenta para dos empresas vale más. null si no tiene nada pendiente.
 */
fun Datos.urgencia(clienteId: Long, hoy: LocalDate, fecha: LocalDate): Urgencia? {
    val pendientes = estadosDe(clienteId, hoy).filter { it.estado == Estado.EN_RIESGO || it.estado == Estado.PENDIENTE }
    if (pendientes.isEmpty()) return null
    var premio = 0.0
    var dias = Int.MAX_VALUE
    pendientes.forEach { e ->
        val fin = LocalDate.ofEpochDay(cicloVigente(e.empresa.id, hoy)!!.fin)
        val d = diasQueAtiendeRestantes(clienteId, fecha, fin)
        premio += KM_POR_VISITA + KM_POR_URGENCIA * (if (d == 0) 1.0 else minOf(1.0, e.faltan.toDouble() / d)) +
            if (e.estado == Estado.EN_RIESGO) KM_POR_RIESGO else 0.0
        dias = minOf(dias, d)
    }
    return Urgencia(pendientes.sumOf { it.faltan }, dias, pendientes.any { it.estado == Estado.EN_RIESGO }, premio)
}

/** Por qué se sugiere: lo urgente primero y después cuánto alarga la ruta. desvio: km que suma a la ruta (null si no se sabe). */
fun explicar(u: Urgencia, desvio: Double?): String = listOfNotNull(
    when {
        u.enRiesgo -> "En riesgo · faltan ${u.faltan}"
        u.dias <= u.faltan + 1 -> "Le quedan ${u.dias} ${if (u.dias == 1) "día" else "días"} en que atiende · faltan ${u.faltan}"
        else -> "Faltan ${u.faltan}"
    },
    desvio?.let { if (it < 2) "de pasada" else "desvío de ${it.roundToInt()} km" },
).joinToString(" · ")
