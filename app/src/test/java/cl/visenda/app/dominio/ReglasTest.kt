package cl.visenda.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReglasTest {
    private val diciembre = LocalDate.of(2026, 12, 1) to LocalDate.of(2026, 12, 31)

    @Test
    fun feriadosYSabados() {
        assertFalse(Reglas().esHabil(LocalDate.of(2026, 12, 8))) // Inmaculada Concepción, martes
        assertFalse(Reglas().esHabil(LocalDate.of(2026, 12, 5))) // sábado
        assertTrue(Reglas(sabados = true).esHabil(LocalDate.of(2026, 12, 5)))
        assertFalse(Reglas(sabados = true).esHabil(LocalDate.of(2026, 12, 6))) // domingo nunca
        // Diciembre 2026: 23 días de lunes a viernes, menos el 8 y el 25; con sábados, 4 más.
        assertEquals(21, Reglas().diasHabilesRestantes(diciembre.first, diciembre.second))
        assertEquals(25, Reglas(sabados = true).diasHabilesRestantes(diciembre.first, diciembre.second))
        // Del jueves 24: el 25 es feriado y viene el fin de semana.
        assertEquals(LocalDate.of(2026, 12, 28), Reglas().habilSiguiente(LocalDate.of(2026, 12, 24)))
    }

    @Test
    fun horas() {
        assertEquals(510, leerHora("8:30"))
        assertEquals(510, leerHora("08.30"))
        assertEquals(510, leerHora("830"))
        assertEquals(13 * 60, leerHora(" 1300 "))
        assertNull(leerHora("24:00"))
        assertNull(leerHora("8:75"))
        assertNull(leerHora("ocho"))
    }

    @Test
    fun validacion() {
        assertEquals(emptyList<String>(), validarReglas(Reglas()))
        assertEquals(1, validarReglas(Reglas(salida = 18 * 60, finJornada = 9 * 60)).size)
        assertEquals(1, validarReglas(Reglas(minutosVisita = 0)).size)
        assertEquals(1, validarReglas(Reglas(colacion = 90, colacionDesde = 13 * 60, colacionHasta = 14 * 60)).size)
        assertEquals(emptyList<String>(), validarReglas(Reglas(colacion = 0, colacionDesde = 13 * 60, colacionHasta = 13 * 60)))
    }
}
