package cl.visenda.app.importar

import cl.visenda.app.data.DatosDemo
import cl.visenda.app.data.Empresa
import cl.visenda.app.data.Visita
import cl.visenda.app.dominio.Datos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class KardexTest {
    private val hoy = LocalDate.of(2026, 10, 20)
    private val encabezados = listOf("Código", "Nombre Médico", "RUT", "Especialidad", "Dirección", "Comuna", "Centro", "Horario", "Categoría", "Teléfono", "Fecha de nacimiento")
    private val roles = proponerMapeo(encabezados)
    private val kardex = listOf(
        encabezados,
        listOf("M1", "Dra. Ana Soto", "12.345.678-9", "Pediatría", "Lincoyán 470, Local 4", "Concepción", "Centro Lincoyán", "Lun-Vie 9-13", "A", "+56 9 1111 1111", "30000"),
        listOf("M1", "Dra. Ana Soto", "12.345.678-9", "Pediatría", "Av. Manuel Rodríguez 1080", "Chiguayante", "", "Sáb 10-12", "A", "", ""),
        listOf("M2", "Dr. Luis Paz", "9.876.543-2", "", "Lincoyán 470, Local 7", "Concepción", "", "Previa hora", "B", "", ""),
        listOf("", "", "", "", "", "", "", "", "", "", ""),
        listOf("M3", "", "", "", "Freire 1220", "Concepción", "", "", "", "", ""),
    )

    @Test
    fun proponeRolesPorEncabezado() {
        assertEquals(
            listOf(Rol.CODIGO, Rol.NOMBRE, Rol.RUT, Rol.ESPECIALIDAD, Rol.DIRECCION, Rol.COMUNA, Rol.LUGAR, Rol.HORARIO, Rol.CATEGORIA, Rol.CAMPO, Rol.CAMPO),
            roles,
        )
        assertEquals(
            listOf(Rol.CODIGO, Rol.RUT, Rol.NOMBRE, Rol.ESPECIALIDAD, Rol.DIRECCION, Rol.COMUNA, Rol.HORARIO, Rol.CATEGORIA, Rol.CAMPO),
            proponerMapeo(listOf("CODIGO", "RUT", "NOMBRE_MEDICO", "ESPECIALIDAD", "DIRECCION_CONSULTA", "COMUNA", "HORARIO", "CATEGORIA", "FECHA_NAC")),
        )
        val guardado = mapeoDeTexto(mapeoATexto(encabezados, roles.map { if (it == Rol.CAMPO) Rol.NO_IMPORTAR else it }))
        assertEquals(Rol.NO_IMPORTAR, proponerMapeo(encabezados, guardado)[9])
    }

    @Test
    fun agrupaFilasPorClienteYUneElMismoEdificio() {
        val c = prepararImportacion(Datos(), kardex, roles, Empresa(0, "Lab Nuevo"), hoy)
        assertEquals(listOf("Dra. Ana Soto", "Dr. Luis Paz"), c.clientes.map { it.nombre })
        // Local 4 y Local 7 del mismo número son un solo lugar (RN-10); la comuna se agrega a la dirección.
        assertEquals(listOf("Lincoyán 470, Local 4, Concepción", "Av. Manuel Rodríguez 1080, Chiguayante"), c.lugares.map { it.direccion })
        assertEquals("Centro Lincoyán", c.lugares[0].nombre)
        assertEquals(3, c.atenciones.size)
        assertEquals(1, c.resumen.filasSinNombre)
        assertEquals(listOf("Dr. Luis Paz: Previa hora"), c.resumen.porRevisar)
        assertTrue(c.horarios.single { it.porRevisar }.textoOriginal == "Previa hora")
        assertEquals(6, c.horarios.count { !it.porRevisar })
        assertEquals(listOf("A", "B"), c.categorias.map { it.nombre })
        assertEquals(listOf("Teléfono", "Fecha de nacimiento"), c.campos.map { it.campo })
        assertEquals("18-02-1982", c.campos.single { it.campo == "Fecha de nacimiento" }.valor)
        assertTrue(c.cicloNuevo != null)
        assertEquals(2, c.resumen.nuevos)
    }

    @Test
    fun reimportarNoDuplicaNiBorraVisitas() {
        val primera = prepararImportacion(Datos(), kardex, roles, Empresa(0, "Lab Nuevo"), hoy)
        val visita = Visita(1, primera.clientes[0].id, primera.lugares[0].id, 0, null)
        val base = aplicar(Datos(), primera).copy(visitas = listOf(visita))

        val segunda = prepararImportacion(base, kardex, roles, primera.empresa, hoy)
        assertEquals(0, segunda.resumen.nuevos)
        assertEquals(0, segunda.resumen.actualizados)
        assertEquals(2, segunda.resumen.sinCambios)
        assertTrue(segunda.lugares.isEmpty() && segunda.atenciones.isEmpty() && segunda.categorias.isEmpty())
        assertNull(segunda.cicloNuevo)
        val despues = aplicar(base, segunda)
        assertEquals(2, despues.clientes.size)
        assertEquals(base.horarios.map { it.copy(id = 0) }.toSet(), despues.horarios.map { it.copy(id = 0) }.toSet())
        assertEquals(listOf(visita), despues.visitas)
    }

    @Test
    fun identificaPorCodigoLuegoRutYAvisaDuplicadosYAusentes() {
        val base = aplicar(Datos(), prepararImportacion(Datos(), kardex, roles, Empresa(0, "Lab"), hoy))
        val empresa = base.empresas.single()
        val nuevo = listOf(
            encabezados,
            // Mismo código con otro nombre: es el mismo cliente, se actualiza el nombre.
            listOf("M1", "Dra. Ana María Soto", "", "", "", "", "", "", "A", "", ""),
            // Sin código pero con el RUT de Luis Paz escrito distinto.
            listOf("", "Dr. Luis Paz", "9876543-2", "", "", "", "", "", "B", "", ""),
            // Otro RUT con el mismo nombre que uno existente: cliente nuevo, con aviso.
            listOf("", "Dr. Luis Paz", "11.111.111-1", "", "", "", "", "", "", "", ""),
        )
        val c = prepararImportacion(base, nuevo, roles, empresa, hoy)
        assertEquals(1, c.resumen.nuevos)
        assertEquals(1, c.resumen.actualizados)
        assertEquals(1, c.resumen.sinCambios) // Luis Paz: mismo cliente por RUT, nada distinto
        assertEquals("Dra. Ana María Soto", c.clientes.first { it.id == base.clientes[0].id }.nombre)
        assertEquals(listOf("Dr. Luis Paz"), c.resumen.posiblesDuplicados)
        assertTrue(c.resumen.yaNoEstan.isEmpty())

        val soloAna = prepararImportacion(base, kardex.take(3), roles, empresa, hoy)
        assertEquals(listOf("Dr. Luis Paz"), soloAna.resumen.yaNoEstan.map { it.nombre })
    }

    @Test
    fun noImportarNoGuardaLaColumna() {
        val sinTelefono = roles.toMutableList().apply { this[9] = Rol.NO_IMPORTAR }
        val c = prepararImportacion(Datos(), kardex, sinTelefono, Empresa(0, "Lab"), hoy)
        assertTrue(c.campos.none { it.campo == "Teléfono" || "+56" in it.valor })
    }

    @Test
    fun quitarDatosDeEjemplo() {
        val demo = DatosDemo.datos(hoy)
        val limpio = demo.sinEmpresas(demo.empresas.map { it.id }.toSet())
        assertEquals(Datos(), limpio)
    }

    /** Lo mismo que hace VisendaDao.aplicar, en memoria. */
    private fun aplicar(d: Datos, c: Cambios): Datos {
        fun <T> reemplazar(viejos: List<T>, nuevos: List<T>, id: (T) -> Any) = viejos.filter { v -> nuevos.none { id(it) == id(v) } } + nuevos
        var siguienteHorario = (d.horarios.maxOfOrNull { it.id } ?: 0) + 1
        return d.copy(
            empresas = reemplazar(d.empresas, listOf(c.empresa)) { it.id },
            ciclos = d.ciclos + listOfNotNull(c.cicloNuevo),
            categorias = d.categorias + c.categorias,
            clientes = reemplazar(d.clientes, c.clientes) { it.id },
            cartera = reemplazar(d.cartera, c.cartera) { it.clienteId to it.empresaId },
            campos = d.campos.filterNot { it.empresaId == c.empresa.id && it.clienteId in c.camposDe } + c.campos,
            lugares = d.lugares + c.lugares,
            atenciones = d.atenciones + c.atenciones,
            horarios = d.horarios.filterNot { it.atencionId in c.horariosDe } + c.horarios.map { it.copy(id = siguienteHorario++) },
        )
    }
}
