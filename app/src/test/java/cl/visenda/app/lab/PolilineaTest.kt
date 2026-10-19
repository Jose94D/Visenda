package cl.visenda.app.lab

import org.junit.Assert.assertEquals
import org.junit.Test

class PolilineaTest {
    @Test
    fun decodificaEjemploDeLaDocumentacionDeGoogle() {
        val puntos = decodificarPolilinea("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
        val esperado = listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453)
        assertEquals(esperado.size, puntos.size)
        puntos.zip(esperado).forEach { (p, e) ->
            assertEquals(e.first, p.first, 1e-9)
            assertEquals(e.second, p.second, 1e-9)
        }
    }
}
