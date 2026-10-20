package cl.visenda.app.dominio

import cl.visenda.app.data.Atencion
import cl.visenda.app.data.Cartera
import cl.visenda.app.data.Categoria
import cl.visenda.app.data.Ciclo
import cl.visenda.app.data.Cliente
import cl.visenda.app.data.Empresa
import cl.visenda.app.data.Horario
import cl.visenda.app.data.Visita
import cl.visenda.app.data.VisitaEmpresa
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CiclosTest {
    // Ciclo de octubre 2026. El jueves 15 quedan 12 días hábiles (15, 16, 19-23, 26-30).
    private val hoy = LocalDate.of(2026, 10, 15)
    private fun ms(d: LocalDate) = d.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val base = Datos(
        empresas = listOf(Empresa(1, "Andes", frecuenciaDefecto = 1)),
        ciclos = listOf(Ciclo(1, 1, "Octubre", LocalDate.of(2026, 10, 1).toEpochDay(), LocalDate.of(2026, 10, 31).toEpochDay())),
        categorias = listOf(Categoria(1, 1, "A", 2)),
        clientes = listOf(Cliente(1, null, "Rojas", null), Cliente(2, null, "Muñoz", null), Cliente(3, null, "Soto", null)),
        cartera = listOf(
            Cartera(1, 1, categoriaId = 1, frecuenciaManual = null, codigoKardex = null), // categoría A: 2 visitas
            Cartera(2, 1, categoriaId = null, frecuenciaManual = null, codigoKardex = null), // defecto: 1
            Cartera(3, 1, categoriaId = 1, frecuenciaManual = 3, codigoKardex = null), // manual: 3
        ),
        atenciones = listOf(Atencion(1, 1, 1), Atencion(2, 2, 1), Atencion(3, 3, 1)),
        horarios = listOf(
            Horario(1, 1, 1, 540, 780), Horario(2, 1, 3, 540, 780), // Rojas: lunes y miércoles
            Horario(3, 2, 2, 540, 780), // Muñoz: martes
            Horario(4, 3, 4, 540, 780), // Soto: jueves
        ),
        visitas = listOf(
            Visita(1, 1, 1, ms(LocalDate.of(2026, 10, 5)), null),
            Visita(2, 2, 1, ms(LocalDate.of(2026, 9, 28)), null), // ciclo anterior: no cuenta
        ),
        visitaEmpresa = listOf(VisitaEmpresa(1, 1), VisitaEmpresa(2, 1)),
    )

    @Test
    fun frecuenciaManualGanaACategoriaYCategoriaADefecto() {
        assertEquals(listOf(2, 1, 3), base.cartera.map { base.frecuenciaRequerida(it) })
    }

    @Test
    fun soloCuentanVisitasDelCiclo() {
        val ciclo = base.cicloVigente(1, hoy)!!
        assertEquals(1, base.visitasContadas(1, 1, ciclo))
        assertEquals(0, base.visitasContadas(2, 1, ciclo))
    }

    @Test
    fun diasHabiles() {
        assertEquals(12, diasHabilesRestantes(hoy, LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun estados() {
        // Rojas: 1 de 2, atiende lun y mié (quedan 4 días así): pendiente.
        assertEquals(Estado.PENDIENTE, base.estadoEn(base.cartera[0], hoy).estado)
        // Soto: 0 de 3, atiende solo jueves y quedan 3 jueves (15, 22, 29): justo alcanza, pendiente.
        assertEquals(Estado.PENDIENTE, base.estadoEn(base.cartera[2], hoy).estado)
        // El viernes 16 a Soto le quedan 2 jueves para 3 visitas: en riesgo.
        assertEquals(Estado.EN_RIESGO, base.estadoEn(base.cartera[2], LocalDate.of(2026, 10, 16)).estado)
        // Faltando 5 días hábiles, todo lo pendiente pasa a riesgo.
        assertEquals(Estado.EN_RIESGO, base.estadoEn(base.cartera[1], LocalDate.of(2026, 10, 26)).estado)
        // Sin ciclo vigente.
        assertEquals(Estado.SIN_CICLO, base.estadoEn(base.cartera[1], LocalDate.of(2026, 11, 2)).estado)
    }

    @Test
    fun completoCuandoSeHacenLasRequeridas() {
        val conOtra = base.copy(
            visitas = base.visitas + Visita(3, 2, 1, ms(LocalDate.of(2026, 10, 6)), null),
            visitaEmpresa = base.visitaEmpresa + VisitaEmpresa(3, 1),
        )
        assertEquals(Estado.COMPLETO, conOtra.estadoEn(conOtra.cartera[1], hoy).estado)
    }
}
