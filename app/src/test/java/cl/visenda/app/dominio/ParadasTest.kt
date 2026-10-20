package cl.visenda.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Test

class ParadasTest {
    private fun h(horas: Int, minutos: Int = 0) = horas * 60 + minutos

    @Test
    fun mismoLugarConHorariosQueNoSeCruzanSonDosParadas() {
        // El caso que encontramos probando: Pérez 11:00–13:30 y Ibáñez 15:00–18:00 en Lincoyán.
        val grupos = agruparParadas(
            listOf(Visitable(1, 10, h(15), h(18)), Visitable(2, 10, h(11), h(13, 30))),
            minutosPorVisita = 15,
        )
        assertEquals(listOf(listOf(2L), listOf(1L)), grupos.map { it.clientes })
    }

    @Test
    fun mismoLugarConHorariosQueSeCruzanEsUnaParada() {
        val grupos = agruparParadas(
            listOf(Visitable(1, 10, h(9), h(13)), Visitable(2, 10, h(10), h(12)), Visitable(3, 20, h(8), h(9))),
            minutosPorVisita = 15,
        )
        assertEquals(2, grupos.size)
        assertEquals(listOf(3L), grupos[0].clientes) // abre primero
        assertEquals(Grupo(10, listOf(1, 2), abre = h(10), cierra = h(12)), grupos[1])
    }

    @Test
    fun noJuntaSiNoAlcanzaElTiempoParaVerATodos() {
        // Se cruzan solo 20 minutos: no alcanza para dos visitas de 15.
        val grupos = agruparParadas(listOf(Visitable(1, 10, h(9), h(10, 20)), Visitable(2, 10, h(10), h(12))), minutosPorVisita = 15)
        assertEquals(2, grupos.size)
    }
}
