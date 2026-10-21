package cl.visenda.app.respaldo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RespaldoTest {
    // Pocas iteraciones para que la prueba sea rápida; la app usa 600.000.
    private val pocas = 1_000
    private val base = "SQLite format 3\u0000".toByteArray() + ByteArray(5000) { (it % 251).toByte() }
    private val manifiesto = Manifiesto("0.1.0", 1, 1_761_000_000_000, 514, listOf("biobio", "nuble"))
    private fun error(bloque: () -> Unit) = runCatching(bloque).exceptionOrNull()

    @Test
    fun idaYVuelta() {
        val archivo = cifrarRespaldo(manifiesto, base, "clave-segura".toCharArray(), pocas)
        val (m, b) = descifrarRespaldo(archivo, "clave-segura".toCharArray())
        assertEquals(manifiesto, m)
        assertTrue(base.contentEquals(b))
        // Ni la base ni los nombres de regiones quedan legibles en el archivo.
        assertFalse(String(archivo, Charsets.ISO_8859_1).contains("SQLite") || String(archivo, Charsets.ISO_8859_1).contains("biobio"))
    }

    @Test
    fun contrasenaIncorrectaOArchivoAlterado() {
        val archivo = cifrarRespaldo(manifiesto, base, "clave-segura".toCharArray(), pocas)
        assertTrue(error { descifrarRespaldo(archivo, "otra-clave".toCharArray()) } is RespaldoInvalido)
        // Un byte cambiado en el contenido o en la cabecera (aquí, el iv).
        listOf(archivo.size - 1, 8 + 16 + 4).forEach { i ->
            val alterado = archivo.copyOf().also { it[i] = (it[i] + 1).toByte() }
            assertTrue("byte $i", error { descifrarRespaldo(alterado, "clave-segura".toCharArray()) } is RespaldoInvalido)
        }
    }

    @Test
    fun rechazaLoQueNoEsRespaldoYContrasenasCortas() {
        assertTrue(error { descifrarRespaldo("hola".toByteArray(), "clave-segura".toCharArray()) } is RespaldoInvalido)
        assertTrue(error { descifrarRespaldo(ByteArray(200), "clave-segura".toCharArray()) } is RespaldoInvalido)
        assertTrue(error { cifrarRespaldo(manifiesto, base, "corta".toCharArray(), pocas) } is IllegalArgumentException)
    }

    @Test
    fun sinRegionesTambienFunciona() {
        val sin = manifiesto.copy(regiones = emptyList())
        assertEquals(sin, descifrarRespaldo(cifrarRespaldo(sin, base, "clave-segura".toCharArray(), pocas), "clave-segura".toCharArray()).first)
    }
}
