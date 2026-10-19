package cl.visenda.app.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimpiarDireccionTest {
    @Test
    fun quitaLoQueIdentificaElLocal() {
        assertEquals("Lincoyán 470, Concepción", limpiarDireccion("Lincoyán 470, Local 4, Concepción"))
        assertEquals("Barros Arana 1050, Concepción", limpiarDireccion("Barros Arana 1050, Of. 21, Concepción"))
        assertEquals("Av. Paicaví 120, Concepción", limpiarDireccion("Av. Paicaví 120, Oficina 304, Concepción"))
        assertEquals("Castellón 450, Concepción", limpiarDireccion("Castellón 450, Piso 3, Concepción"))
        assertEquals("Los Carrera 301, Concepción", limpiarDireccion("Los Carrera 301 Consulta 5-B, Concepción"))
    }

    @Test
    fun noTocaNombresDeCalles() {
        assertEquals("O'Higgins 940, Concepción", limpiarDireccion("O'Higgins 940, Depto 52, Concepción"))
        assertEquals("Av. Los Carrera 301, Concepción", limpiarDireccion("Av. Los Carrera 301, Concepción"))
        assertEquals("Torremolinos 12, Coronel", limpiarDireccion("Torremolinos 12, Coronel"))
    }

    @Test
    fun comparaLaComunaSinTildesNiMayusculas() {
        assertEquals("chillan", comunaEsperada("Arauco 645, Chillán"))
        assertTrue(mismaComuna("Arauco 645, Chillán", "Chillan", null))
        assertTrue(mismaComuna("Serrano 527, Tomé", null, "Tomé"))
        assertFalse(mismaComuna("Arauco 645, Chillán", "Concepción", "Concepción"))
    }

    @Test
    fun zona() {
        assertTrue(dentroDeZona(-36.827, -73.050)) // Concepción
        assertFalse(dentroDeZona(-33.45, -70.66)) // Santiago
    }
}
