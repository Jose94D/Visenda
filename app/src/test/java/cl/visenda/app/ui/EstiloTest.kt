package cl.visenda.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EstiloTest {
    @Test
    fun unaFuenteYCapasPropiasPorRegion() {
        val dos = estilo(listOf(File("/m/biobio.pmtiles"), File("/m/nuble.pmtiles")), emptyList(), emptyList())
        val ids = Regex("\"id\": \"([^\"]+)\"").findAll(dos).map { it.groupValues[1] }.toList()
        assertEquals("ids repetidos: $ids", ids.size, ids.toSet().size)
        assertTrue(dos.contains("\"base0\"") && dos.contains("\"base1\"") && dos.contains("nuble.pmtiles"))
        // Las etiquetas de todas las regiones van sobre los fondos de todas.
        assertTrue(ids.indexOf("calles-1") < ids.indexOf("nombres-calles-0"))
        // Para validar el JSON fuera de la JVM (no hay lector JSON en las pruebas locales).
        File("build/estilos").apply { mkdirs() }.let { d ->
            listOf(0, 1, 2).forEach { n -> File(d, "estilo$n.json").writeText(estilo(List(n) { File("/m/r$it.pmtiles") }, listOf(1.0 to 2.0, 3.0 to 4.0), listOf(1.0 to 2.0))) }
        }
    }
}
