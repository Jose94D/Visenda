package cl.visenda.app.dominio

import cl.visenda.app.data.Categoria
import cl.visenda.app.data.Ciclo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EmpresasTest {
    private fun d(mes: Int, dia: Int) = LocalDate.of(2026, mes, dia).toEpochDay()
    private val octubre = Ciclo(1, 1, "Ciclo octubre", d(10, 1), d(10, 31))
    private val noviembre = Ciclo(2, 1, "Ciclo noviembre", d(11, 1), d(11, 30))
    private val cats = listOf(Categoria(1, 1, "A", 2), Categoria(2, 1, "B", 1))

    @Test
    fun empresaValida() {
        assertEquals(emptyList<String>(), validarEmpresa("Lab Andes", listOf("Pharma Sur"), 1, listOf(octubre, noviembre), cats))
    }

    @Test
    fun detectaLosProblemas() {
        assertEquals(listOf("Falta el nombre de la empresa."), validarEmpresa(" ", emptyList(), 1, emptyList(), emptyList()))
        assertEquals(listOf("Ya hay otra empresa llamada así."), validarEmpresa("pharma sur", listOf("Pharma Sur"), 1, emptyList(), emptyList()))
        assertEquals(listOf("Ciclo octubre y Ciclo noviembre se solapan."),
            validarEmpresa("X", emptyList(), 1, listOf(noviembre, octubre.copy(fin = d(11, 1))), emptyList()))
        assertTrue(validarEmpresa("X", emptyList(), 1, listOf(octubre.copy(inicio = d(11, 5))), emptyList()).single().contains("termina antes"))
        assertEquals(listOf("Hay categorías con el mismo nombre."), validarEmpresa("X", emptyList(), 1, emptyList(), cats + Categoria(3, 1, "a", 1)))
        assertEquals(1, validarEmpresa("X", emptyList(), 0, emptyList(), emptyList()).size)
        assertEquals(1, validarEmpresa("X", emptyList(), 1, emptyList(), listOf(Categoria(1, 1, "A", 31))).size)
    }

    @Test
    fun cicloSiguienteEsElMesDespuesDelUltimo() {
        val hoy = LocalDate.of(2026, 10, 21)
        assertEquals(Ciclo(0, 1, "Ciclo octubre", d(10, 1), d(10, 31)), cicloSiguiente(emptyList(), 1, hoy))
        assertEquals(Ciclo(0, 1, "Ciclo diciembre", d(12, 1), d(12, 31)), cicloSiguiente(listOf(octubre, noviembre), 1, hoy))
    }
}
