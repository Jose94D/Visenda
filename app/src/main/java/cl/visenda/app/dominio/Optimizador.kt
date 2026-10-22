package cl.visenda.app.dominio

/** Un lugar donde se puede ver al cliente ese día. lugar: índice en las matrices de viaje (0 = punto de partida). */
data class Opcion(val lugar: Int, val tramos: List<Pair<Int, Int>>)

/** premio: cuánto vale visitarlo, en km que se justifica manejar por él (ver premio() en Urgencia.kt). */
data class Pedido(val clienteId: Long, val premio: Double, val opciones: List<Opcion>)

/** Una visita del plan. inicio = cuando empieza a atender (llegada + espera); cierra = fin del tramo usado. */
data class Paso(val clienteId: Long, val lugar: Int, val llegada: Int, val inicio: Int, val cierra: Int)

/** La colación empieza a la hora [inicio], después de [pasosAntes] visitas. Si no aparece, se toma después de la última. */
data class Colacion(val inicio: Int, val pasosAntes: Int)

/** Lo que el usuario fija en Ajustes > Planificación, en minutos desde las 00:00. */
data class Jornada(
    val salida: Int, val fin: Int, val duracion: Int,
    val colacion: Int = 0, val colacionDesde: Int = 0, val colacionHasta: Int = 0, val volver: Boolean = false,
)

enum class Motivo { SIN_CAMINO, HORARIO, FIN_DE_JORNADA, NO_CABE }

/** termina: fin de la última visita. vuelta: llegada al punto de partida, si se pidió volver. km incluye la vuelta. */
data class Plan(
    val pasos: List<Paso>, val fuera: Map<Long, Motivo>, val termina: Int,
    val colacion: Colacion?, val km: Double, val espera: Int, val vuelta: Int?,
)

const val EXACTO_HASTA = 12

/** Veces que la heurística saca visitas al azar y vuelve a mejorar, y cuánto peor (en km) acepta para seguir explorando. */
const val PERTURBACIONES = 30
const val TOLERANCIA = 5.0

/** Rutas parciales que conserva la búsqueda en haz en cada nivel. */
const val HAZ = 1500
const val SIN_VIAJE = Int.MAX_VALUE

/** Esperar a que abra un cliente es tiempo perdido: cada minuto pesa como estos km. Media hora ≈ 4,5 km. */
const val KM_POR_MINUTO_DE_ESPERA = 0.15

/**
 * RN-07: elige a quién visitar, en qué lugar y en qué orden.
 * Objetivo: la suma de los premios de los visitados menos los km manejados y la espera (en km equivalentes).
 * Hasta 12 clientes es exacto (programación dinámica sobre subconjuntos); sobre eso, inserción y mejora local (2-opt, or-opt).
 * viaje[i][j]: minutos de manejo del lugar i al j, o SIN_VIAJE si no hay camino. km[i][j]: la distancia del mismo tramo.
 */
fun optimizar(pedidos: List<Pedido>, viaje: Array<IntArray>, km: Array<DoubleArray>, jornada: Jornada): Plan {
    val r = Ruteo(pedidos, viaje, km, jornada)
    val orden = if (pedidos.size <= EXACTO_HASTA) r.exacto() else r.heuristica()
    val rec = r.recorrer(orden)!!
    val dentro = orden.map { it.first }.toSet()
    return Plan(
        pasos = rec.pasos,
        fuera = pedidos.indices.filter { it !in dentro }.associate { pedidos[it].clienteId to r.motivo(pedidos[it]) },
        termina = rec.reloj, colacion = rec.colacion, km = rec.km, espera = rec.espera, vuelta = rec.vuelta,
    )
}

/** Cuántos km suma cada visita del plan: los del plan menos los del plan sin ella (null si sin ella la ruta no funciona). */
fun desvios(pedidos: List<Pedido>, viaje: Array<IntArray>, km: Array<DoubleArray>, jornada: Jornada, plan: Plan): Map<Long, Double?> {
    val r = Ruteo(pedidos, viaje, km, jornada)
    val orden = plan.pasos.map { p -> pedidos.indexOfFirst { it.clienteId == p.clienteId }.let { i -> i to pedidos[i].opciones.first { it.lugar == p.lugar } } }
    return orden.indices.associate { i -> pedidos[orden[i].first].clienteId to r.recorrer(orden.filterIndexed { k, _ -> k != i })?.let { plan.km - it.km } }
}

/** Un tramo de la ruta: ir desde donde se está hasta la próxima visita, almorzando antes o durante la espera si corresponde. */
internal class Avance(val llegada: Int, val inicio: Int, val cierra: Int, val colacion: Int?, val espera: Int, val km: Double)

internal class Recorrido(
    val pasos: List<Paso>, val colacion: Colacion?, val reloj: Int, val km: Double, val espera: Int, val vuelta: Int?, val valor: Double,
)

internal class Ruteo(val pedidos: List<Pedido>, val viaje: Array<IntArray>, val km: Array<DoubleArray>, val j: Jornada) {

    /** La colación solo se exige si todavía cabe en su horario el día que se planifica. */
    private val conColacion = j.colacion > 0 && maxOf(j.salida, j.colacionDesde) + j.colacion <= j.colacionHasta

    /** Primer tramo donde la visita termina antes del cierre y del fin de jornada: (inicio, cierre). */
    fun atender(op: Opcion, llegada: Int, limite: Int = j.fin): Pair<Int, Int>? =
        op.tramos.firstNotNullOfOrNull { (abre, cierra) -> maxOf(llegada, abre).takeIf { it + j.duracion <= minOf(cierra, limite) }?.let { it to cierra } }

    /**
     * Ir de [desde] (terminando a la hora [reloj]) a la visita [op]. La colación, si falta, se toma:
     * 1) durante la espera a que abra, si cabe entera; 2) después, si aún cabrá al terminar esta visita; 3) si no, ahora mismo.
     * La espera antes de la primera visita no cuenta: basta con salir más tarde.
     */
    private fun avanzar(reloj: Int, desde: Int, almorzo: Boolean, op: Opcion): Avance? {
        val v = viaje[desde][op.lugar]
        if (v == SIN_VIAJE) return null
        val d = km[desde][op.lugar]
        val cuenta = if (desde == 0) 0 else 1
        val llegada = reloj + v
        val (inicio, cierra) = atender(op, llegada) ?: return null
        if (almorzo || !conColacion) return Avance(llegada, inicio, cierra, null, cuenta * (inicio - llegada), d)
        val enEspera = maxOf(llegada, j.colacionDesde)
        if (enEspera + j.colacion <= minOf(inicio, j.colacionHasta)) return Avance(llegada, inicio, cierra, enEspera, cuenta * (inicio - llegada - j.colacion), d)
        if (maxOf(inicio + j.duracion, j.colacionDesde) + j.colacion <= j.colacionHasta) return Avance(llegada, inicio, cierra, null, cuenta * (inicio - llegada), d)
        val ahora = maxOf(reloj, j.colacionDesde)
        if (ahora + j.colacion > j.colacionHasta) return null
        val llegada2 = ahora + j.colacion + v
        val (inicio2, cierra2) = atender(op, llegada2) ?: return null
        return Avance(llegada2, inicio2, cierra2, ahora, cuenta * (ahora - reloj + inicio2 - llegada2), d)
    }

    /** Recorre la secuencia (pedido, opción); null si alguna visita no cabe o la colación no alcanza. */
    fun recorrer(orden: List<Pair<Int, Opcion>>): Recorrido? {
        var reloj = j.salida
        var desde = 0
        var colacion: Colacion? = null
        var kms = 0.0
        var espera = 0
        val pasos = orden.mapIndexed { i, (p, op) ->
            val a = avanzar(reloj, desde, colacion != null, op) ?: return null
            if (a.colacion != null) colacion = Colacion(a.colacion, i)
            kms += a.km; espera += a.espera
            Paso(pedidos[p].clienteId, op.lugar, a.llegada, a.inicio, a.cierra).also { reloj = a.inicio + j.duracion; desde = op.lugar }
        }
        val fin = cerrar(reloj, desde, colacion != null) ?: return null
        return Recorrido(
            pasos, colacion, reloj, kms + fin.first, espera, fin.second,
            orden.sumOf { pedidos[it.first].premio } - costo(kms + fin.first, espera, reloj),
        )
    }

    /** Lo que falta al terminar la última visita: (km de vuelta, hora de llegada); null si ya no alcanza la colación. */
    private fun cerrar(reloj: Int, desde: Int, almorzo: Boolean): Pair<Double, Int?>? {
        if (conColacion && !almorzo && maxOf(reloj, j.colacionDesde) + j.colacion > j.colacionHasta) return null
        if (!j.volver || desde == 0 || viaje[desde][0] == SIN_VIAJE) return 0.0 to null
        return km[desde][0] to reloj + viaje[desde][0]
    }

    /** Lo que se resta a los premios. El término pesa muy poco: solo desempata a favor de terminar antes. */
    private fun costo(kms: Double, espera: Int, termina: Int) = kms + KM_POR_MINUTO_DE_ESPERA * espera + 0.001 * termina

    fun valor(orden: List<Pair<Int, Opcion>>) = recorrer(orden)?.valor

    // --- Exacto ---

    /** Una forma de llegar a un estado (visitados, última opción). previo: de dónde vino, para reconstruir el orden. */
    private class Etiqueta(val reloj: Int, val almorzo: Boolean, val costo: Double, val op: Int, val previo: Etiqueta?)

    private fun domina(a: Etiqueta, b: Etiqueta) = a.reloj <= b.reloj && a.costo <= b.costo && (a.almorzo || !b.almorzo)

    fun exacto(): List<Pair<Int, Opcion>> {
        val ops = pedidos.flatMapIndexed { p, pe -> pe.opciones.map { p to it } }
        val m = ops.size
        if (m == 0) return emptyList()
        // Por estado se guardan las etiquetas que ninguna otra supera en hora, costo y colación (frente de Pareto).
        // shortcut: máximo 4 por estado, se descarta la más cara; subir el límite si el exacto pierde frente a la heurística.
        val estados = arrayOfNulls<MutableList<Etiqueta>>((1 shl pedidos.size) * m)
        fun agregar(i: Int, e: Etiqueta) {
            val lista = estados[i] ?: mutableListOf<Etiqueta>().also { estados[i] = it }
            if (lista.any { domina(it, e) }) return
            lista.removeAll { domina(e, it) }
            lista += e
            if (lista.size > 4) lista.remove(lista.maxBy { it.costo })
        }
        val c = KM_POR_MINUTO_DE_ESPERA
        for (o in 0 until m) {
            val a = avanzar(j.salida, 0, false, ops[o].second) ?: continue
            agregar((1 shl ops[o].first) * m + o, Etiqueta(a.inicio + j.duracion, a.colacion != null, a.km + c * a.espera, o, null))
        }
        for (mask in 1 until (1 shl pedidos.size)) for (o in 0 until m) {
            val lista = estados[mask * m + o] ?: continue
            for (e in lista) for (o2 in 0 until m) {
                if (mask and (1 shl ops[o2].first) != 0) continue
                val a = avanzar(e.reloj, ops[o].second.lugar, e.almorzo, ops[o2].second) ?: continue
                agregar((mask or (1 shl ops[o2].first)) * m + o2, Etiqueta(a.inicio + j.duracion, e.almorzo || a.colacion != null, e.costo + a.km + c * a.espera, o2, e))
            }
        }
        // El mejor final se elige con recorrer(), que suma la vuelta, revisa la colación y resta los premios.
        var mejor = emptyList<Pair<Int, Opcion>>()
        var mejorValor = valor(mejor) ?: Double.NEGATIVE_INFINITY
        for (lista in estados) for (e in lista.orEmpty()) {
            val orden = generateSequence(e) { it.previo }.map { ops[it.op] }.toList().asReversed()
            val v = valor(orden) ?: continue
            if (v > mejorValor) { mejor = orden; mejorValor = v }
        }
        return mejor
    }

    // --- Heurística ---

    /**
     * Búsqueda local iterada: parte vacía y "llena" (todos los que quepan), mejora cada una hasta que ningún movimiento sirva,
     * y luego repite sacando al azar hasta un tercio de las visitas. Acepta resultados algo peores (hasta TOLERANCIA km) para
     * salir de óptimos locales, y guarda el mejor visto. Semilla fija: mismo resultado siempre.
     */
    fun heuristica(): List<Pair<Int, Opcion>> {
        val h = haz()
        var actual = listOf(mejorar(emptyList()), mejorar(lleno()), mejorar(haz())).maxBy { valor(it)!! }
        var actualValor = valor(actual)!!
        var mejor = actual
        var mejorValor = actualValor
        val azar = kotlin.random.Random(1)
        repeat(PERTURBACIONES) {
            if (actual.isEmpty()) return mejor
            val sacar = actual.indices.shuffled(azar).take(azar.nextInt(1, maxOf(2, actual.size / 3) + 1)).toSet()
            val candidato = mejorar(actual.filterIndexed { i, _ -> i !in sacar })
            val v = valor(candidato)!!
            if (v > actualValor - TOLERANCIA) { actual = candidato; actualValor = v }
            if (v > mejorValor + 1e-9) { mejor = candidato; mejorValor = v }
        }
        return mejor
    }

    /** Aplica el mejor movimiento mientras alguno suba el valor. orden debe ser factible. */
    private fun mejorar(inicial: List<Pair<Int, Opcion>>): List<Pair<Int, Opcion>> {
        var orden = inicial
        var valorActual = valor(orden) ?: return emptyList()
        while (true) {
            val (nuevo, v) = vecinos(orden).mapNotNull { o -> valor(o)?.let { o to it } }.maxByOrNull { it.second } ?: break
            if (v <= valorActual + 1e-9) break
            orden = nuevo; valorActual = v
        }
        return orden
    }

    /**
     * Búsqueda en haz: como el exacto, arma rutas visita por visita, pero en cada nivel conserva solo las HAZ mejores
     * (premios menos costo hasta ahí), sin repetir el mismo (visitados, último lugar). Encuentra bien cadenas de horarios angostos.
     */
    private fun haz(): List<Pair<Int, Opcion>> {
        if (pedidos.size > 63) return emptyList()
        val ops = pedidos.flatMapIndexed { p, pe -> pe.opciones.map { p to it } }
        val c = KM_POR_MINUTO_DE_ESPERA
        class Nodo(val visitados: Long, val op: Int, val reloj: Int, val almorzo: Boolean, val costo: Double, val premio: Double, val previo: Nodo?) {
            val puntaje get() = premio - costo - 0.001 * reloj
        }
        fun orden(n: Nodo) = generateSequence(n) { it.previo }.map { ops[it.op] }.toList().asReversed()
        var mejor = emptyList<Pair<Int, Opcion>>()
        var mejorValor = valor(mejor) ?: return mejor
        var mejorNodo: Nodo? = null
        var nivel = listOf<Nodo?>(null)
        while (nivel.isNotEmpty()) {
            val siguientes = HashMap<Pair<Long, Int>, Nodo>()
            for (n in nivel) for (o2 in ops.indices) {
                val (p, op) = ops[o2]
                if (n != null && n.visitados and (1L shl p) != 0L) continue
                val a = if (n == null) avanzar(j.salida, 0, false, op) else avanzar(n.reloj, ops[n.op].second.lugar, n.almorzo, op)
                a ?: continue
                val nuevo = Nodo(
                    (n?.visitados ?: 0L) or (1L shl p), o2, a.inicio + j.duracion, (n?.almorzo ?: false) || a.colacion != null,
                    (n?.costo ?: 0.0) + a.km + c * a.espera, (n?.premio ?: 0.0) + pedidos[p].premio, n,
                )
                val clave = nuevo.visitados to o2
                val otro = siguientes[clave]
                if (otro == null || nuevo.puntaje > otro.puntaje) siguientes[clave] = nuevo
            }
            nivel = siguientes.values.sortedByDescending { it.puntaje }.take(HAZ)
            // Mismo cálculo que recorrer(), pero con lo ya acumulado en el nodo: sin volver a simular la ruta.
            for (n in nivel) {
                val fin = cerrar(n.reloj, ops[n.op].second.lugar, n.almorzo) ?: continue
                val v = n.premio - n.costo - fin.first - 0.001 * n.reloj
                if (v > mejorValor) { mejorNodo = n; mejorValor = v }
            }
        }
        return mejorNodo?.let(::orden) ?: mejor
    }

    /** Inserta, de a uno, al que deja la ruta con mejor valor aunque reste, mientras alguno quepa. */
    private fun lleno(): List<Pair<Int, Opcion>> {
        var orden = emptyList<Pair<Int, Opcion>>()
        while (true) {
            val fuera = pedidos.indices.filter { p -> orden.none { it.first == p } }
            orden = fuera.asSequence().flatMap { p -> pedidos[p].opciones.asSequence().flatMap { op -> (0..orden.size).asSequence().map { pos -> orden.toMutableList().apply { add(pos, p to op) } } } }
                .mapNotNull { o -> valor(o)?.let { o to it } }.maxByOrNull { it.second }?.first ?: return orden
        }
    }

    /** Insertar, sacar o cambiar un cliente; cambiarle el lugar; mover tramos de 1 a 3 visitas (or-opt), invertirlos (2-opt) o intercambiar dos. */
    private fun vecinos(orden: List<Pair<Int, Opcion>>): Sequence<List<Pair<Int, Opcion>>> = sequence {
        val n = orden.size
        val fuera = pedidos.indices.filter { p -> orden.none { it.first == p } }
        for (p in fuera) for (op in pedidos[p].opciones) for (pos in 0..n) yield(orden.toMutableList().apply { add(pos, p to op) })
        for (i in 0 until n) {
            yield(orden.toMutableList().apply { removeAt(i) })
            for (p in fuera) for (op in pedidos[p].opciones) yield(orden.toMutableList().apply { set(i, p to op) })
            for (op in pedidos[orden[i].first].opciones) if (op != orden[i].second) yield(orden.toMutableList().apply { set(i, orden[i].first to op) })
        }
        for (largo in 1..3) for (i in 0..n - largo) {
            val tramo = orden.subList(i, i + largo)
            val resto = orden.take(i) + orden.drop(i + largo)
            for (pos in 0..resto.size) if (pos != i) yield(resto.take(pos) + tramo + resto.drop(pos))
        }
        for (i in 0 until n) for (k in i + 1 until n) {
            yield(orden.take(i) + orden.subList(i, k + 1).asReversed() + orden.drop(k + 1))
            yield(orden.toMutableList().apply { set(i, orden[k]); set(k, orden[i]) })
        }
    }

    /** Por qué un cliente quedó fuera, mirando si se podría visitar solo, saliendo directo hacia él. */
    fun motivo(pe: Pedido): Motivo {
        val directas = pe.opciones.filter { viaje[0][it.lugar] != SIN_VIAJE }
        return when {
            directas.isEmpty() -> Motivo.SIN_CAMINO
            directas.none { atender(it, j.salida + viaje[0][it.lugar], limite = Int.MAX_VALUE) != null } -> Motivo.HORARIO
            directas.none { atender(it, j.salida + viaje[0][it.lugar]) != null } -> Motivo.FIN_DE_JORNADA
            else -> Motivo.NO_CABE
        }
    }
}
