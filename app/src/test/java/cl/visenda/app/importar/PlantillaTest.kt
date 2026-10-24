package cl.visenda.app.importar

import cl.visenda.app.data.Empresa
import cl.visenda.app.dominio.Datos
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class PlantillaTest {
    @Test
    fun laPlantillaSeLeeYSeImportaSinRevisarNada() {
        val bytes = ByteArrayOutputStream().also { escribirXlsx(PLANTILLA_KARDEX, it) }.toByteArray()
        val filas = LectorXlsx.leer(ByteArrayInputStream(bytes))
        assertEquals(PLANTILLA_KARDEX.map { f -> f.dropLastWhile { it.isEmpty() } }, filas.map { f -> f.dropLastWhile { it.isEmpty() } })

        // Visenda reconoce todas las columnas por su título y entiende todos los horarios.
        val roles = proponerMapeo(filas[0])
        assertEquals(
            listOf(Rol.CODIGO, Rol.NOMBRE, Rol.RUT, Rol.ESPECIALIDAD, Rol.CATEGORIA, Rol.LUGAR, Rol.DIRECCION, Rol.COMUNA, Rol.HORARIO, Rol.CAMPO, Rol.CAMPO),
            roles,
        )
        val c = prepararImportacion(Datos(), filas, roles, Empresa(0, "Ejemplo"), LocalDate.of(2026, 10, 23))
        assertEquals(2, c.clientes.size) // la Dra. Muñoz tiene dos consultas, pero es un solo cliente
        assertEquals(3, c.lugares.size)
        assertEquals(emptyList<String>(), c.resumen.porRevisar)
    }

    @Test
    fun escapaTextoConCaracteresEspeciales() {
        val bytes = ByteArrayOutputStream().also { escribirXlsx(listOf(listOf("A & B <c> \"d\"", "", "x")), it) }.toByteArray()
        assertEquals(listOf(listOf("A & B <c> \"d\"", "", "x")), LectorXlsx.leer(ByteArrayInputStream(bytes)))
    }
}
