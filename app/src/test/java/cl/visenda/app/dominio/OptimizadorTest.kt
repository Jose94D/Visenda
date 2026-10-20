package cl.visenda.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class OptimizadorTest {
    private fun h(hora: Int, min: Int = 0) = hora * 60 + min
    private val todoElDia = listOf(h(8) to h(20))

    /** Lugares en una recta: posición i, 10 minutos entre vecinos. El 0 es la salida. */
    private fun recta(n: Int) = Array(n + 1) { i -> IntArray(n + 1) { j -> abs(i - j) * 10 } }

    private fun pedido(id: Long, lugar: Int, tramos: List<Pair<Int, Int>> = todoElDia, riesgo: Boolean = false) =
        Pedido(id, riesgo, listOf(Opcion(lugar, tramos)))

    @Test
    fun ordenaPorCercaniaYNoPorElOrdenDeEntrada() {
        val plan = optimizar(listOf(pedido(3, 3), pedido(1, 1), pedido(2, 2)), recta(3), h(9), h(18), 15)
        assertEquals(listOf(1L, 2L, 3L), plan.pasos.map { it.clienteId })
        assertEquals(h(9) + 30 + 45, plan.termina)
    }

    @Test
    fun respetaLaHoraDeCierre() {
        // El 3 cierra temprano: conviene ir primero aunque esté lejos.
        val plan = optimizar(listOf(pedido(1, 1), pedido(3, 3, listOf(h(8) to h(9, 50)))), recta(3), h(9), h(18), 15)
        assertEquals(listOf(3L, 1L), plan.pasos.map { it.clienteId })
        plan.pasos.forEach { assertTrue(it.inicio + 15 <= it.cierra) }
    }

    @Test
    fun aIgualHoraDeTerminoManejaMenos() {
        // X en 1, Y en 2, Z en 3 abre a las 12:00. X→Y→Z y Y→X→Z terminan 12:15, pero el segundo maneja 30 en vez de 50.
        val viaje = arrayOf(intArrayOf(0, 10, 10, 30), intArrayOf(10, 0, 10, 10), intArrayOf(10, 10, 0, 30), intArrayOf(30, 10, 30, 0))
        val plan = optimizar(listOf(pedido(1, 1), pedido(2, 2), pedido(3, 3, listOf(h(12) to h(18)))), viaje, h(9), h(18), 15)
        assertEquals(listOf(2L, 1L, 3L), plan.pasos.map { it.clienteId })
    }

    @Test
    fun siNoCabenTodosPrefiereAlEnRiesgo() {
        val tramo = listOf(h(9) to h(9, 50)) // cualquiera de los dos cabe, pero no ambos
        val plan = optimizar(listOf(pedido(1, 1, tramo), pedido(2, 3, tramo, riesgo = true)), recta(3), h(9), h(18), 15)
        assertEquals(listOf(2L), plan.pasos.map { it.clienteId })
        assertEquals(mapOf(1L to Motivo.NO_CABE), plan.fuera)
    }

    @Test
    fun explicaPorQueQuedanFuera() {
        val viaje = recta(3).also { it[0][3] = SIN_VIAJE }
        val plan = optimizar(
            listOf(pedido(1, 1, listOf(h(8) to h(9))), pedido(2, 2, listOf(h(18) to h(20))), pedido(3, 3)),
            viaje, h(9), h(18), 15,
        )
        assertEquals(Motivo.HORARIO, plan.fuera[1])
        assertEquals(Motivo.FIN_DE_JORNADA, plan.fuera[2])
        assertEquals(Motivo.SIN_CAMINO, plan.fuera[3])
    }

    @Test
    fun eligeElLugarQueConvieneYUsaElSegundoTramo() {
        val dos = Pedido(1, false, listOf(Opcion(3, todoElDia), Opcion(1, listOf(h(8) to h(9), h(15) to h(18)))))
        val plan = optimizar(listOf(dos), recta(3), h(10), h(18), 15)
        // Lugar 1 abre recién a las 15:00 y el 3 está a 30 min: termina antes en el 3.
        assertEquals(3, plan.pasos.single().lugar)
        val tarde = optimizar(listOf(dos.copy(opciones = dos.opciones.drop(1))), recta(3), h(10), h(18), 15)
        assertEquals(h(15), tarde.pasos.single().inicio)
    }

    @Test
    fun clientesDelMismoLugarSeAtiendenSeguidos() {
        val plan = optimizar(listOf(pedido(1, 2), pedido(2, 1), pedido(3, 2)), recta(2), h(9), h(18), 15)
        val lugares = plan.pasos.map { it.lugar }
        assertEquals(listOf(1, 2, 2), lugares)
        assertEquals(plan.pasos[1].inicio + 15, plan.pasos[2].inicio)
    }

    @Test
    fun exactoCoincideConFuerzaBruta() {
        val azar = Random(7)
        repeat(200) {
            val (pedidos, viaje) = caso(azar, n = azar.nextInt(1, 6))
            val plan = optimizar(pedidos, viaje, h(9), h(13), 15)
            valido(plan, pedidos, viaje, h(9), h(13))
            assertEquals(fuerzaBruta(pedidos, viaje, h(9), h(13)), clave(plan, pedidos))
        }
    }

    @Test
    fun heuristicaDaRutasValidas() {
        val azar = Random(11)
        repeat(20) {
            val (pedidos, viaje) = caso(azar, n = 25)
            valido(optimizar(pedidos, viaje, h(9), h(18), 15), pedidos, viaje, h(9), h(18))
        }
    }

    @Test
    fun quinceClientesConDosOficinasEnPocoTiempo() {
        val (pedidos, viaje) = caso(Random(3), n = 15, opciones = 2)
        val t = System.nanoTime()
        val plan = optimizar(pedidos, viaje, h(8, 30), h(18), 15)
        val ms = (System.nanoTime() - t) / 1_000_000
        valido(plan, pedidos, viaje, h(8, 30), h(18))
        assertTrue("tardó $ms ms", ms < 1500)
    }

    // --- ayudas ---

    private fun caso(azar: Random, n: Int, opciones: Int = 1 + azar.nextInt(2)): Pair<List<Pedido>, Array<IntArray>> {
        val lugares = n * opciones
        val xy = List(lugares + 1) { azar.nextInt(30) to azar.nextInt(30) }
        val viaje = Array(lugares + 1) { i -> IntArray(lugares + 1) { j -> abs(xy[i].first - xy[j].first) + abs(xy[i].second - xy[j].second) } }
        val pedidos = List(n) { p ->
            Pedido(p.toLong(), azar.nextInt(3) == 0, List(opciones) { k ->
                val abre = h(8) + azar.nextInt(8) * 30
                Opcion(1 + p * opciones + k, listOf(abre to abre + 30 + azar.nextInt(6) * 30))
            })
        }
        return pedidos to viaje
    }

    private fun clave(plan: Plan, pedidos: List<Pedido>) =
        Triple(plan.pasos.count { p -> pedidos.first { it.clienteId == p.clienteId }.enRiesgo }, plan.pasos.size, -plan.termina)

    /** Prueba todos los órdenes de todos los subconjuntos con todas las opciones. */
    private fun fuerzaBruta(pedidos: List<Pedido>, viaje: Array<IntArray>, salida: Int, fin: Int): Triple<Int, Int, Int> {
        var mejor = Triple(0, 0, -salida)
        fun explorar(reloj: Int, desde: Int, usados: Set<Int>, riesgo: Int) {
            val actual = Triple(riesgo, usados.size, -reloj)
            if (compareValuesBy(actual, mejor, { it.first }, { it.second }, { it.third }) > 0) mejor = actual
            pedidos.indices.filter { it !in usados }.forEach { p ->
                pedidos[p].opciones.forEach { op ->
                    val llegada = reloj + viaje[desde][op.lugar]
                    op.tramos.firstNotNullOfOrNull { (a, c) -> maxOf(llegada, a).takeIf { it + 15 <= minOf(c, fin) } }?.let {
                        explorar(it + 15, op.lugar, usados + p, riesgo + if (pedidos[p].enRiesgo) 1 else 0)
                    }
                }
            }
        }
        explorar(salida, 0, emptySet(), 0)
        return mejor
    }

    private fun valido(plan: Plan, pedidos: List<Pedido>, viaje: Array<IntArray>, salida: Int, fin: Int) {
        var reloj = salida
        var desde = 0
        plan.pasos.forEach { p ->
            val op = pedidos.first { it.clienteId == p.clienteId }.opciones.first { it.lugar == p.lugar }
            assertEquals(reloj + viaje[desde][p.lugar], p.llegada)
            assertTrue(p.inicio >= p.llegada)
            assertTrue(op.tramos.any { (a, c) -> p.inicio >= a && p.inicio + 15 <= minOf(c, fin) })
            reloj = p.inicio + 15
            desde = p.lugar
        }
        assertEquals(pedidos.size, plan.pasos.map { it.clienteId }.toSet().size + plan.fuera.size)
    }
}
