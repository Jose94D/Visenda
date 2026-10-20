package cl.visenda.app.ui.pantallas

import org.junit.Assert.assertEquals
import org.junit.Test

class TextoDiasTest {
    @Test
    fun resumeRachasDeTresOMasDias() {
        assertEquals("Lun a Vie", textoDias(listOf(1, 2, 3, 4, 5)))
        assertEquals("Lun · Mié · Vie", textoDias(listOf(5, 1, 3)))
        assertEquals("Mar · Jue", textoDias(listOf(2, 4)))
        assertEquals("Lun · Mar", textoDias(listOf(1, 2)))
        assertEquals("Lun a Mié · Sáb", textoDias(listOf(1, 2, 3, 6)))
    }
}
