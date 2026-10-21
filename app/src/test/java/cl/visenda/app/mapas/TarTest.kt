package cl.visenda.app.mapas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class TarTest {
    @get:Rule val carpeta = TemporaryFolder()

    /** Arma un .tar ustar a mano: (nombre, tipo, contenido). */
    private fun tar(vararg entradas: Triple<String, Char, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        entradas.forEach { (nombre, tipo, datos) ->
            val h = ByteArray(512)
            nombre.toByteArray().copyInto(h, 0)
            "%011o\u0000".format(datos.size).toByteArray().copyInto(h, 124)
            h[156] = tipo.code.toByte()
            "ustar\u000000".toByteArray().copyInto(h, 257)
            out.write(h)
            out.write(datos)
            out.write(ByteArray(((512 - datos.size % 512) % 512)))
        }
        out.write(ByteArray(1024))
        return out.toByteArray()
    }

    @Test
    fun extraeArchivosYSaltaCarpetasYCabecerasPax() {
        val datos = ByteArray(1000) { it.toByte() }
        val paquete = tar(
            Triple("2/000/", '5', ByteArray(0)),
            Triple("PaxHeader", 'x', "30 path=2/000/295/625.gph\n".toByteArray()),
            Triple("2/000/295/625.gph", '0', datos),
            Triple("0/001/106.gph", '0', "hola".toByteArray()),
        )
        val destino = carpeta.newFolder()
        assertEquals(listOf("2/000/295/625.gph", "0/001/106.gph"), extraerTar(ByteArrayInputStream(paquete), destino))
        assertTrue(datos.contentEquals(destino.resolve("2/000/295/625.gph").readBytes()))
        assertEquals("hola", destino.resolve("0/001/106.gph").readText())
    }

    @Test
    fun rechazaNombresQueSalenDelDestino() {
        val destino = carpeta.newFolder("teselas")
        listOf("../fuera.txt", "/etc/fuera.txt", "2/../../fuera.txt").forEach { nombre ->
            val error = runCatching { extraerTar(ByteArrayInputStream(tar(Triple(nombre, '0', "x".toByteArray()))), destino) }.exceptionOrNull()
            assertTrue(nombre, error is IOException)
        }
        assertFalse(carpeta.root.resolve("fuera.txt").exists())
    }

    @Test
    fun paqueteCortadoEsError() {
        val paquete = tar(Triple("a.gph", '0', ByteArray(2000)))
        val error = runCatching { extraerTar(ByteArrayInputStream(paquete.copyOf(900)), carpeta.newFolder()) }.exceptionOrNull()
        assertTrue(error is IOException)
    }
}
