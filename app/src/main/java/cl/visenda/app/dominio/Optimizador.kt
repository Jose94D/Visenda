package cl.visenda.app.dominio

/** Un lugar donde se puede ver al cliente ese día. lugar: índice en la matriz de viaje (0 = punto de salida). */
data class Opcion(val lugar: Int, val tramos: List<Pair<Int, Int>>)

data class Pedido(val clienteId: Long, val enRiesgo: Boolean, val opciones: List<Opcion>)

/** Una visita del plan. inicio = cuando empieza a atender (llegada + espera); cierra = fin del tramo usado. */
data class Paso(val clienteId: Long, val lugar: Int, val llegada: Int, val inicio: Int, val cierra: Int)

enum class Motivo { SIN_CAMINO, HORARIO, FIN_DE_JORNADA, NO_CABE }

data class Plan(val pasos: List<Paso>, val fuera: Map<Long, Motivo>, val termina: Int)

const val EXACTO_HASTA = 15
const val SIN_VIAJE = Int.MAX_VALUE

/**
 * RN-07: elige a quién visitar, en qué lugar y en qué orden.
 * Objetivo: más clientes en riesgo, luego más clientes, luego terminar antes y, a igual término, manejar menos.
 * Hasta 15 clientes es exacto (programación dinámica sobre subconjuntos); más que eso, inserción voraz.
 * viaje[i][j]: minutos de manejo del lugar i al j, o SIN_VIAJE si no hay camino.
 */
fun optimizar(pedidos: List<Pedido>, viaje: Array<IntArray>, salida: Int, finJornada: Int, duracion: Int): Plan {
    val r = Ruteo(pedidos, viaje, salida, finJornada, duracion)
    val orden = if (pedidos.size <= EXACTO_HASTA) r.exacto() else r.insercion()
    val pasos = r.simular(orden)!!
    val dentro = pedidos.indices.filter { p -> orden.any { it.first == p } }.toSet()
    return Plan(
        pasos = pasos,
        fuera = pedidos.indices.filter { it !in dentro }.associate { pedidos[it].clienteId to r.motivo(pedidos[it]) },
        termina = pasos.lastOrNull()?.let { it.inicio + duracion } ?: salida,
    )
}

private class Ruteo(val pedidos: List<Pedido>, val viaje: Array<IntArray>, val salida: Int, val finJornada: Int, val duracion: Int) {

    /** Primer tramo donde la visita termina antes del cierre y del fin de jornada: (inicio, cierre). */
    fun atender(op: Opcion, llegada: Int, limite: Int = finJornada): Pair<Int, Int>? =
        op.tramos.firstNotNullOfOrNull { (abre, cierra) -> maxOf(llegada, abre).takeIf { it + duracion <= minOf(cierra, limite) }?.let { it to cierra } }

    /** Recorre la secuencia (pedido, opción); null si alguna visita no cabe. */
    fun simular(orden: List<Pair<Int, Opcion>>): List<Paso>? {
        var reloj = salida
        var desde = 0
        return orden.map { (p, op) ->
            val v = viaje[desde][op.lugar]
            if (v == SIN_VIAJE) return null
            val (inicio, cierra) = atender(op, reloj + v) ?: return null
            Paso(pedidos[p].clienteId, op.lugar, reloj + v, inicio, cierra).also { reloj = inicio + duracion; desde = op.lugar }
        }
    }

    fun exacto(): List<Pair<Int, Opcion>> {
        val ops = pedidos.flatMapIndexed { p, pe -> pe.opciones.map { p to it } }
        val m = ops.size
        if (m == 0) return emptyList()
        val de = IntArray(m) { ops[it].first }
        val lugar = IntArray(m) { ops[it].second.lugar }
        val fin = IntArray((1 shl pedidos.size) * m) { Int.MAX_VALUE }
        val manejo = IntArray(fin.size)
        val previo = IntArray(fin.size) { -1 }

        for (o in 0 until m) {
            val v = viaje[0][lugar[o]]
            if (v == SIN_VIAJE) continue
            atender(ops[o].second, salida + v)?.let { fin[(1 shl de[o]) * m + o] = it.first + duracion; manejo[(1 shl de[o]) * m + o] = v }
        }
        // Basta guardar el término más temprano por (visitados, último): llegar antes nunca empeora lo que sigue.
        // shortcut: el desempate por manejo se decide por estado, no es exacto; usar etiquetas de Pareto si deja rutas largas.
        for (mask in 1 until (1 shl pedidos.size)) for (o in 0 until m) {
            val f = fin[mask * m + o]
            if (f == Int.MAX_VALUE) continue
            for (o2 in 0 until m) {
                if (mask and (1 shl de[o2]) != 0) continue
                val v = viaje[lugar[o]][lugar[o2]]
                if (v == SIN_VIAJE) continue
                val inicio = atender(ops[o2].second, f + v)?.first ?: continue
                val i = (mask or (1 shl de[o2])) * m + o2
                val km = manejo[mask * m + o] + v
                if (inicio + duracion < fin[i] || (inicio + duracion == fin[i] && km < manejo[i])) {
                    fin[i] = inicio + duracion; manejo[i] = km; previo[i] = o
                }
            }
        }

        val riesgo = pedidos.indices.filter { pedidos[it].enRiesgo }.sumOf { 1 shl it }
        var mejor = -1
        var mejorClave = listOf(-1, -1, 0, 0)
        for (i in fin.indices) {
            if (fin[i] == Int.MAX_VALUE) continue
            val mask = i / m
            val clave = listOf(Integer.bitCount(mask and riesgo), Integer.bitCount(mask), -fin[i], -manejo[i])
            if (compareValuesBy(clave, mejorClave, { it[0] }, { it[1] }, { it[2] }, { it[3] }) > 0) { mejor = i; mejorClave = clave }
        }
        val orden = mutableListOf<Pair<Int, Opcion>>()
        var i = mejor
        while (i >= 0) {
            val o = i % m
            orden.add(0, ops[o])
            val anterior = previo[i]
            i = if (anterior < 0) -1 else ((i / m) and (1 shl de[o]).inv()) * m + anterior
        }
        return orden
    }

    // shortcut: inserción voraz sin mejora local; agregar 2-opt / or-opt si las rutas de más de 15 clientes salen largas.
    fun insercion(): List<Pair<Int, Opcion>> {
        var orden = listOf<Pair<Int, Opcion>>()
        pedidos.indices.sortedByDescending { pedidos[it].enRiesgo }.forEach { p ->
            orden = (0..orden.size).flatMap { pos -> pedidos[p].opciones.map { op -> orden.toMutableList().apply { add(pos, p to op) } } }
                .mapNotNull { o -> simular(o)?.let { o to it.last().inicio } }
                .minByOrNull { it.second }?.first ?: orden
        }
        return orden
    }

    /** Por qué un cliente quedó fuera, mirando si se podría visitar solo, saliendo directo hacia él. */
    fun motivo(pe: Pedido): Motivo {
        val directas = pe.opciones.filter { viaje[0][it.lugar] != SIN_VIAJE }
        return when {
            directas.isEmpty() -> Motivo.SIN_CAMINO
            directas.none { atender(it, salida + viaje[0][it.lugar], limite = Int.MAX_VALUE) != null } -> Motivo.HORARIO
            directas.none { atender(it, salida + viaje[0][it.lugar]) != null } -> Motivo.FIN_DE_JORNADA
            else -> Motivo.NO_CABE
        }
    }
}
