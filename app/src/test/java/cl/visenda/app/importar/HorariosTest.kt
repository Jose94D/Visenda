package cl.visenda.app.importar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HorariosTest {
    private fun h(hora: Int, min: Int = 0) = hora * 60 + min
    private fun semana(dias: IntRange, vararg tramos: Pair<Int, Int>) = dias.flatMap { d -> tramos.map { Tramo(d, it.first, it.second) } }

    @Test
    fun formatosDelAnexoA() {
        assertEquals(semana(1..5, h(9) to h(13)), interpretarHorario("Lun-Vie 9:00-13:00"))
        assertEquals(semana(1..5, h(15) to h(19)), interpretarHorario("L a V 15 a 19"))
        assertEquals(
            listOf(Tramo(1, h(9), h(13)), Tramo(1, h(15), h(19)), Tramo(3, h(9), h(13)), Tramo(3, h(15), h(19))),
            interpretarHorario("Lun, Mié 9-13 / 15-19"),
        )
        assertEquals(listOf(Tramo(2, h(8, 30), h(12, 30))), interpretarHorario("Martes 08:30 - 12:30"))
    }

    @Test
    fun variantesComunes() {
        assertEquals(semana(1..5, h(9) to h(13)), interpretarHorario("Lunes a Viernes de 9:00 a 13:00 hrs"))
        assertEquals(semana(1..5, h(9) to h(13)) + listOf(Tramo(6, h(10), h(12))), interpretarHorario("Lun-Vie 9-13; Sáb 10-12"))
        assertEquals(semana(1..5, h(9) to h(13)) + listOf(Tramo(6, h(10), h(12))), interpretarHorario("Lun-Vie 9-13, Sáb 10-12"))
        assertEquals(listOf(Tramo(1, h(9), h(13)), Tramo(3, h(9), h(13)), Tramo(5, h(9), h(13))), interpretarHorario("lun, mie y vie 9 a 13"))
        assertEquals(semana(1..5, h(9) to h(13), h(15) to h(19)), interpretarHorario("L-V 9-13 y 15-19"))
    }

    @Test
    fun loQueNoEntiendeQuedaPorRevisar() {
        assertNull(interpretarHorario(""))
        assertNull(interpretarHorario("Previa hora"))
        assertNull(interpretarHorario("Lun-Vie mañana"))
        assertNull(interpretarHorario("9-13"))                 // sin días
        assertNull(interpretarHorario("Lun-Vie 9 a 1"))        // 13:00 escrito como 1: abre después de cerrar
        assertNull(interpretarHorario("Lun 22-02"))            // cruza la medianoche
        assertNull(interpretarHorario("Lun 9-13 / 12-15"))     // tramos solapados
        assertNull(interpretarHorario("Lun-Vie 9-13 consultar")) // texto que sobra
        assertNull(interpretarHorario("Vie-Lun 9-13"))         // rango al revés
    }
}
