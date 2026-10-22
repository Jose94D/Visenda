package cl.visenda.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class OptimizadorTest {
    private fun h(hora: Int, min: Int = 0) = hora * 60 + min
    private val todoElDia = listOf(h(8) to h(20))

    /** Lugares en una recta: posición i, 10 minutos y 10 km entre vecinos. El 0 es la partida. */
    private fun recta(n: Int) = Array(n + 1) { i -> IntArray(n + 1) { j -> abs(i - j) * 10 } }
    private fun kmDe(viaje: Array<IntArray>) = Array(viaje.size) { i -> DoubleArray(viaje.size) { j -> if (viaje[i][j] == SIN_VIAJE) 0.0 else viaje[i][j].toDouble() } }

    private fun pedido(id: Long, lugar: Int, tramos: List<Pair<Int, Int>> = todoElDia, premio: Double = PREMIO_ELEGIDO) =
        Pedido(id, premio, listOf(Opcion(lugar, tramos)))

    private fun optimizar(pedidos: List<Pedido>, viaje: Array<IntArray>, j: Jornada) = optimizar(pedidos, viaje, kmDe(viaje), j)
    private fun jornada(salida: Int = h(9), fin: Int = h(18)) = Jornada(salida, fin, 15)

    @Test
    fun ordenaPorCercaniaYNoPorElOrdenDeEntrada() {
        val plan = optimizar(listOf(pedido(3, 3), pedido(1, 1), pedido(2, 2)), recta(3), jornada())
        assertEquals(listOf(1L, 2L, 3L), plan.pasos.map { it.clienteId })
        assertEquals(h(9) + 30 + 45, plan.termina)
        assertEquals(30.0, plan.km, 0.0)
    }

    @Test
    fun respetaLaHoraDeCierre() {
        // El 3 cierra temprano: conviene ir primero aunque esté lejos.
        val plan = optimizar(listOf(pedido(1, 1), pedido(3, 3, listOf(h(8) to h(9, 50)))), recta(3), jornada())
        assertEquals(listOf(3L, 1L), plan.pasos.map { it.clienteId })
        plan.pasos.forEach { assertTrue(it.inicio + 15 <= it.cierra) }
    }

    @Test
    fun aIgualCantidadManejaMenos() {
        // X en 1, Y en 2, Z en 3 abre a las 12:00. X→Y→Z maneja 50; Y→X→Z, 30.
        val viaje = arrayOf(intArrayOf(0, 10, 10, 30), intArrayOf(10, 0, 10, 10), intArrayOf(10, 10, 0, 30), intArrayOf(30, 10, 30, 0))
        val plan = optimizar(listOf(pedido(1, 1), pedido(2, 2), pedido(3, 3, listOf(h(12) to h(18)))), viaje, jornada())
        assertEquals(listOf(2L, 1L, 3L), plan.pasos.map { it.clienteId })
    }

    @Test
    fun siNoCabenTodosPrefiereAlEnRiesgo() {
        val tramo = listOf(h(9) to h(9, 50)) // cualquiera de los dos cabe, pero no ambos
        val plan = optimizar(listOf(pedido(1, 1, tramo), pedido(2, 3, tramo, premio = PREMIO_ELEGIDO_EN_RIESGO)), recta(3), jornada())
        assertEquals(listOf(2L), plan.pasos.map { it.clienteId })
        assertEquals(mapOf(1L to Motivo.NO_CABE), plan.fuera)
    }

    @Test
    fun explicaPorQueQuedanFuera() {
        val viaje = recta(3).also { it[0][3] = SIN_VIAJE }
        val plan = optimizar(listOf(pedido(1, 1, listOf(h(8) to h(9))), pedido(2, 2, listOf(h(18) to h(20))), pedido(3, 3)), viaje, jornada())
        assertEquals(Motivo.HORARIO, plan.fuera[1])
        assertEquals(Motivo.FIN_DE_JORNADA, plan.fuera[2])
        assertEquals(Motivo.SIN_CAMINO, plan.fuera[3])
    }

    @Test
    fun eligeElLugarQueConvieneYUsaElSegundoTramo() {
        val dos = Pedido(1, PREMIO_ELEGIDO, listOf(Opcion(3, todoElDia), Opcion(1, listOf(h(8) to h(9), h(15) to h(18)))))
        // El lugar 1 está a 10 km pero abre recién a las 15:00; el 3, a 30 km y abierto. La espera antes de la primera
        // visita no cuenta (basta salir más tarde), así que conviene el 1, en su segundo tramo.
        val plan = optimizar(listOf(dos), recta(3), jornada(salida = h(10)))
        assertEquals(1, plan.pasos.single().lugar)
        assertEquals(h(15), plan.pasos.single().inicio)
    }

    @Test
    fun clientesDelMismoLugarSeAtiendenSeguidos() {
        val plan = optimizar(listOf(pedido(1, 2), pedido(2, 1), pedido(3, 2)), recta(2), jornada())
        assertEquals(listOf(1, 2, 2), plan.pasos.map { it.lugar })
        assertEquals(plan.pasos[1].inicio + 15, plan.pasos[2].inicio)
    }

    @Test
    fun noSeDesviaPorUnClienteQueValeMenosQueElViaje() {
        // A 30 km: con premio 20 no conviene; con 40, sí.
        assertTrue(optimizar(listOf(pedido(1, 3, premio = 20.0)), recta(3), jornada()).pasos.isEmpty())
        assertEquals(1, optimizar(listOf(pedido(1, 3, premio = 40.0)), recta(3), jornada()).pasos.size)
        // Con vuelta son 60 km: ya no conviene con 40.
        assertTrue(optimizar(listOf(pedido(1, 3, premio = 40.0)), recta(3), jornada().copy(volver = true)).pasos.isEmpty())
    }

    @Test
    fun sumaDePasadaAlQueQuedaEnElCamino() {
        // El urgente está en 3; el de 2 vale poco, pero queda en el camino: no suma km.
        val plan = optimizar(listOf(pedido(1, 3, premio = 40.0), pedido(2, 2, premio = 2.0)), recta(3), jornada())
        assertEquals(listOf(2L, 1L), plan.pasos.map { it.clienteId })
        assertEquals(0.0, desvios(listOf(pedido(1, 3, premio = 40.0), pedido(2, 2, premio = 2.0)), recta(3), kmDe(recta(3)), jornada(), plan)[2L]!!, 1e-9)
    }

    @Test
    fun laEsperaPesa() {
        // El 1 cierra 10:00, así que va primero. Al 2 se le ve en el lugar 1, que abre a las 12:00 (2 h 35 de espera ≈ 23 km),
        // o en el lugar 2, 10 km más allá y abierto.
        val dos = Pedido(2, PREMIO_ELEGIDO, listOf(Opcion(1, listOf(h(12) to h(18))), Opcion(2, todoElDia)))
        val plan = optimizar(listOf(pedido(1, 1, listOf(h(9) to h(10))), dos), recta(2), jornada())
        assertEquals(2, plan.pasos.last().lugar)
    }

    @Test
    fun vueltaAlPuntoDePartida() {
        val plan = optimizar(listOf(pedido(1, 1), pedido(2, 2)), recta(2), jornada().copy(volver = true))
        assertEquals(plan.termina + 20, plan.vuelta)
        assertEquals(40.0, plan.km, 0.0)
        assertNull(optimizar(listOf(pedido(1, 1)), recta(1), jornada()).vuelta)
    }

    @Test
    fun laColacionOcupaSuLugar() {
        val siete = List(7) { pedido(it.toLong(), 1) }
        val sinColacion = jornada(salida = h(13), fin = h(15))
        assertEquals(7, optimizar(siete, recta(1), sinColacion).pasos.size)
        // 45 minutos entre 13:00 y 15:00: caben 4 visitas antes; la colación queda después de la última (14:10 a 14:55).
        val conColacion = sinColacion.copy(colacion = 45, colacionDesde = h(13), colacionHasta = h(15))
        val plan = optimizar(siete, recta(1), conColacion)
        assertEquals(4, plan.pasos.size)
        assertNull(plan.colacion)
    }

    @Test
    fun laColacionSeTomaMientrasEspera() {
        // El 1 cierra 12:30; termina 12:25 y el 2 abre a las 14:00: almuerza de 13:00 a 13:45 mientras espera.
        val j = jornada(salida = h(12)).copy(colacion = 45, colacionDesde = h(13), colacionHasta = h(15))
        val plan = optimizar(listOf(pedido(1, 1, listOf(h(8) to h(12, 30))), pedido(2, 1, listOf(h(14) to h(18)))), recta(1), j)
        assertEquals(listOf(1L, 2L), plan.pasos.map { it.clienteId })
        assertEquals(Colacion(h(13), 1), plan.colacion)
        assertEquals(h(14), plan.pasos[1].inicio)
        assertEquals(95 - 45, plan.espera) // de 12:25 a 14:00, menos la colación
    }

    @Test
    fun laColacionCortaLaMananaSiHaceFalta() {
        // Un cliente abre 13:30 a 15:00 y el siguiente cierra tarde: la colación va antes o entre medio, nunca se salta.
        val j = jornada(salida = h(12, 30)).copy(colacion = 60, colacionDesde = h(13), colacionHasta = h(14, 30))
        val plan = optimizar(listOf(pedido(1, 1, listOf(h(13, 30) to h(15))), pedido(2, 1)), recta(1), j)
        assertEquals(2, plan.pasos.size)
        val c = plan.colacion!!
        assertTrue(c.inicio >= h(13) && c.inicio + 60 <= h(14, 30))
    }

    @Test
    fun exactoCoincideConFuerzaBruta() {
        val azar = Random(7)
        repeat(150) {
            val (pedidos, viaje) = caso(azar, n = azar.nextInt(1, 6))
            val j = Jornada(h(9), h(13), 15, colacion = if (it % 2 == 0) 30 else 0, colacionDesde = h(11), colacionHasta = h(12, 30), volver = it % 3 == 0)
            val plan = optimizar(pedidos, viaje, j)
            valido(plan, pedidos, viaje, j)
            assertEquals(fuerzaBruta(pedidos, viaje, j), valor(plan, pedidos, viaje, j), 1e-6)
        }
    }

    @Test
    fun heuristicaCasiIgualAlExacto() {
        val azar = Random(5)
        val brechas = List(40) {
            val (pedidos, viaje) = caso(azar, n = 10, conPremios = true)
            val j = Jornada(h(8, 30), h(18), 15, 45, h(13), h(15), volver = true)
            val exacto = valor(optimizar(pedidos, viaje, j), pedidos, viaje, j)
            val heuristica = Ruteo(pedidos, viaje, kmDe(viaje), j).let { r -> r.valor(r.heuristica())!! }
            assertTrue(heuristica <= exacto + 1e-6)
            exacto - heuristica
        }
        // En km equivalentes: la heurística queda a pocos km del óptimo.
        assertTrue("brechas $brechas", brechas.average() < 1 && brechas.max() < 10)
    }

    @Test
    fun heuristicaDaRutasValidas() {
        val azar = Random(11)
        repeat(10) {
            val (pedidos, viaje) = caso(azar, n = 25, conPremios = true)
            val j = Jornada(h(9), h(18), 15, 45, h(13), h(15), volver = it % 2 == 0)
            valido(optimizar(pedidos, viaje, j), pedidos, viaje, j)
        }
    }

    @Test
    fun docePedidosConDosOficinasEnPocoTiempo() {
        val (pedidos, viaje) = caso(Random(3), n = EXACTO_HASTA, opciones = 2)
        val j = Jornada(h(8, 30), h(18), 15, 45, h(13), h(15), volver = true)
        val t = System.nanoTime()
        val plan = optimizar(pedidos, viaje, j)
        val ms = (System.nanoTime() - t) / 1_000_000
        valido(plan, pedidos, viaje, j)
        assertTrue("tardó $ms ms", ms < 2000)
    }

    @Test
    fun treintaCandidatosEnPocoTiempo() {
        val (pedidos, viaje) = caso(Random(9), n = 30, conPremios = true)
        val j = Jornada(h(8, 30), h(18), 15, 45, h(13), h(15), volver = true)
        val t = System.nanoTime()
        val plan = optimizar(pedidos, viaje, j)
        val ms = (System.nanoTime() - t) / 1_000_000
        valido(plan, pedidos, viaje, j)
        assertTrue("tardó $ms ms", ms < 3000)
    }

    // --- ayudas ---

    /** Lugares al azar en una grilla; con premios como los de urgencia() (de 4 a 40 km) o todos elegidos a mano. */
    private fun caso(azar: Random, n: Int, opciones: Int = 1 + azar.nextInt(2), conPremios: Boolean = false): Pair<List<Pedido>, Array<IntArray>> {
        val lugares = n * opciones
        val xy = List(lugares + 1) { azar.nextInt(30) to azar.nextInt(30) }
        val viaje = Array(lugares + 1) { i -> IntArray(lugares + 1) { j -> abs(xy[i].first - xy[j].first) + abs(xy[i].second - xy[j].second) } }
        val pedidos = List(n) { p ->
            val premio = if (conPremios) 4.0 + azar.nextDouble(36.0) else if (azar.nextInt(3) == 0) PREMIO_ELEGIDO_EN_RIESGO else PREMIO_ELEGIDO
            Pedido(p.toLong(), premio, List(opciones) { k ->
                val abre = h(8) + azar.nextInt(8) * 30
                Opcion(1 + p * opciones + k, listOf(abre to abre + 30 + azar.nextInt(6) * 30))
            })
        }
        return pedidos to viaje
    }

    private fun orden(plan: Plan, pedidos: List<Pedido>) = plan.pasos.map { s ->
        pedidos.indexOfFirst { it.clienteId == s.clienteId }.let { i -> i to pedidos[i].opciones.first { it.lugar == s.lugar } }
    }

    private fun valor(plan: Plan, pedidos: List<Pedido>, viaje: Array<IntArray>, j: Jornada) =
        Ruteo(pedidos, viaje, kmDe(viaje), j).valor(orden(plan, pedidos))!!

    /** Prueba todos los órdenes de todos los subconjuntos con todas las opciones. */
    private fun fuerzaBruta(pedidos: List<Pedido>, viaje: Array<IntArray>, j: Jornada): Double {
        val r = Ruteo(pedidos, viaje, kmDe(viaje), j)
        var mejor = r.valor(emptyList())!!
        fun explorar(orden: List<Pair<Int, Opcion>>) {
            r.valor(orden)?.let { mejor = maxOf(mejor, it) } ?: if (orden.isNotEmpty()) return else Unit
            pedidos.indices.filter { p -> orden.none { it.first == p } }.forEach { p -> pedidos[p].opciones.forEach { explorar(orden + (p to it)) } }
        }
        explorar(emptyList())
        return mejor
    }

    /** Cada visita dentro de su horario y de la jornada, sin solaparse, y la colación (si hay) entera en su ventana. */
    private fun valido(plan: Plan, pedidos: List<Pedido>, viaje: Array<IntArray>, j: Jornada) {
        var reloj = j.salida
        var desde = 0
        plan.pasos.forEachIndexed { i, p ->
            val op = pedidos.first { it.clienteId == p.clienteId }.opciones.first { it.lugar == p.lugar }
            val almuerzaAntes = plan.colacion?.pasosAntes == i
            if (almuerzaAntes) assertTrue(plan.colacion!!.inicio >= reloj)
            assertTrue(p.llegada >= reloj + viaje[desde][p.lugar])
            if (!almuerzaAntes) assertEquals(reloj + viaje[desde][p.lugar], p.llegada)
            assertTrue(p.inicio >= p.llegada)
            assertTrue(op.tramos.any { (a, c) -> p.inicio >= a && p.inicio + 15 <= minOf(c, j.fin) })
            reloj = p.inicio + 15
            desde = p.lugar
        }
        plan.colacion?.let { assertTrue(it.inicio >= j.colacionDesde && it.inicio + j.colacion <= j.colacionHasta) }
        assertEquals(pedidos.size, plan.pasos.map { it.clienteId }.toSet().size + plan.fuera.size)
    }
}
