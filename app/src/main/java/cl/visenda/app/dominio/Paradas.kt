package cl.visenda.app.dominio

/** Un cliente a visitar en un lugar, dentro de su tramo de horario (minutos desde las 00:00). */
data class Visitable(val clienteId: Long, val lugarId: Long, val abre: Int, val cierra: Int)

/** Clientes que se visitan juntos: mismo lugar y horarios que se cruzan lo suficiente para verlos a todos. */
data class Grupo(val lugarId: Long, val clientes: List<Long>, val abre: Int, val cierra: Int)

/** Cada cliente: su primer lugar ese día y el primer tramo que aún no cierra al salir. */
fun Datos.visitables(clientes: List<Long>, dia: java.time.DayOfWeek, salida: Int, minutosPorVisita: Int): List<Visitable> =
    clientes.mapNotNull { id ->
        horariosDelDia(id, dia).firstNotNullOfOrNull { (lugar, tramos) ->
            tramos.firstOrNull { it.cierra > salida + minutosPorVisita }?.let { Visitable(id, lugar.id, it.abre, it.cierra) }
        }
    }

/**
 * Junta en una parada a los clientes del mismo lugar solo si sus horarios se cruzan con margen para la visita.
 * Los grupos salen ordenados por hora de apertura.
 * shortcut: orden por apertura en vez de optimizar la ruta; el optimizador real llega en el hito 3.
 */
fun agruparParadas(visitables: List<Visitable>, minutosPorVisita: Int): List<Grupo> {
    val grupos = mutableListOf<Grupo>()
    visitables.sortedBy { it.abre }.forEach { v ->
        val i = grupos.indexOfFirst { g ->
            g.lugarId == v.lugarId &&
                maxOf(g.abre, v.abre) + minutosPorVisita * (g.clientes.size + 1) <= minOf(g.cierra, v.cierra)
        }
        if (i < 0) grupos += Grupo(v.lugarId, listOf(v.clienteId), v.abre, v.cierra)
        else grupos[i] = grupos[i].let { g -> g.copy(clientes = g.clientes + v.clienteId, abre = maxOf(g.abre, v.abre), cierra = minOf(g.cierra, v.cierra)) }
    }
    return grupos.sortedBy { it.abre }
}
