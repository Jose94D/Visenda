package cl.visenda.app.dominio

import cl.visenda.app.data.DatosDemo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class ExportarTest {
    private val hoy = LocalDate.of(2026, 10, 21)
    private val d = DatosDemo.datos(hoy)

    @Test
    fun incluyeTodoLoDelClienteYNadaDeOtros() {
        val c = d.clientes.first { d.visitas.any { v -> v.clienteId == it.id } && d.atenciones.count { a -> a.clienteId == it.id } > 0 }
        val texto = d.exportarCliente(c.id, LocalDateTime.of(2026, 10, 21, 16, 0), ZoneOffset.UTC)
        assertTrue(texto.contains("Nombre: ${c.nombre}"))
        assertTrue(texto.contains("RUT: ${c.rut}"))
        d.cartera.filter { it.clienteId == c.id }.forEach { cart -> assertTrue(texto.contains(d.empresas.first { it.id == cart.empresaId }.nombre)) }
        d.campos.filter { it.clienteId == c.id }.forEach { assertTrue(it.campo, texto.contains("${it.campo}: ${it.valor}")) }
        d.atenciones.filter { it.clienteId == c.id }.forEach { a -> assertTrue(texto.contains(d.lugares.first { it.id == a.lugarId }.direccion)) }
        val visitas = d.visitas.count { it.clienteId == c.id }
        assertTrue(texto.substringAfter("VISITAS REGISTRADAS").lines().count { it.startsWith("- ") } == visitas)
        d.clientes.filter { it.id != c.id }.forEach { otro -> assertFalse(otro.nombre, texto.contains(otro.nombre)) }
    }
}
