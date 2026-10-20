package cl.visenda.app.dominio

import cl.visenda.app.data.Atencion
import cl.visenda.app.data.Cartera
import cl.visenda.app.data.Categoria
import cl.visenda.app.data.Ciclo
import cl.visenda.app.data.Cliente
import cl.visenda.app.data.ClienteCampo
import cl.visenda.app.data.Empresa
import cl.visenda.app.data.Horario
import cl.visenda.app.data.Lugar
import cl.visenda.app.data.Visita
import cl.visenda.app.data.VisitaEmpresa
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Foto de toda la base en memoria. Un kardex son cientos de filas: cabe sin problema y simplifica los cálculos. */
data class Datos(
    val empresas: List<Empresa> = emptyList(),
    val ciclos: List<Ciclo> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val clientes: List<Cliente> = emptyList(),
    val cartera: List<Cartera> = emptyList(),
    val campos: List<ClienteCampo> = emptyList(),
    val lugares: List<Lugar> = emptyList(),
    val atenciones: List<Atencion> = emptyList(),
    val horarios: List<Horario> = emptyList(),
    val visitas: List<Visita> = emptyList(),
    val visitaEmpresa: List<VisitaEmpresa> = emptyList(),
)

/** Ordenados de peor a mejor: la lista de clientes muestra el peor estado entre empresas (RN-04). */
enum class Estado { EN_RIESGO, PENDIENTE, COMPLETO, SIN_CICLO }

data class EstadoEnEmpresa(val empresa: Empresa, val hechas: Int, val requeridas: Int, val estado: Estado) {
    val faltan get() = (requeridas - hechas).coerceAtLeast(0)
}

data class AvanceEmpresa(val empresa: Empresa, val ciclo: Ciclo?, val hechas: Int, val requeridas: Int, val diasRestantes: Int, val enRiesgo: Int)

fun LocalDate.epoch() = toEpochDay()
fun fechaDeVisita(visita: Visita, zona: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(visita.fechaHora).atZone(zona).toLocalDate()

/** RN-02: el ciclo de la empresa que contiene hoy. */
fun Datos.cicloVigente(empresaId: Long, hoy: LocalDate): Ciclo? =
    ciclos.firstOrNull { it.empresaId == empresaId && hoy.epoch() in it.inicio..it.fin }

/** RN-01: manual > categoría > defecto de la empresa. */
fun Datos.frecuenciaRequerida(c: Cartera): Int =
    c.frecuenciaManual
        ?: categorias.firstOrNull { it.id == c.categoriaId }?.frecuencia
        ?: empresas.first { it.id == c.empresaId }.frecuenciaDefecto

/** RN-03 */
fun Datos.visitasContadas(clienteId: Long, empresaId: Long, ciclo: Ciclo): Int {
    val deLaEmpresa = visitaEmpresa.filter { it.empresaId == empresaId }.map { it.visitaId }.toSet()
    return visitas.count { it.clienteId == clienteId && it.id in deLaEmpresa && fechaDeVisita(it).epoch() in ciclo.inicio..ciclo.fin }
}

/** Días lunes a viernes entre hoy y el fin del ciclo, incluido hoy. */
fun diasHabilesRestantes(hoy: LocalDate, fin: LocalDate): Int =
    generateSequence(hoy) { it.plusDays(1) }.takeWhile { !it.isAfter(fin) }
        .count { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }

/** Días entre hoy y el fin en que el cliente atiende en alguna oficina (horarios por revisar no cuentan). */
fun Datos.diasQueAtiendeRestantes(clienteId: Long, hoy: LocalDate, fin: LocalDate): Int {
    val dias = diasQueAtiende(clienteId)
    return generateSequence(hoy) { it.plusDays(1) }.takeWhile { !it.isAfter(fin) }.count { it.dayOfWeek.value in dias }
}

fun Datos.diasQueAtiende(clienteId: Long): Set<Int> {
    val suyas = atenciones.filter { it.clienteId == clienteId }.map { it.id }.toSet()
    return horarios.filter { it.atencionId in suyas && !it.porRevisar }.map { it.diaSemana }.toSet()
}

/** RN-04 */
fun Datos.estadoEn(c: Cartera, hoy: LocalDate, diasAviso: Int = 5): EstadoEnEmpresa {
    val empresa = empresas.first { it.id == c.empresaId }
    val requeridas = frecuenciaRequerida(c)
    val ciclo = cicloVigente(c.empresaId, hoy) ?: return EstadoEnEmpresa(empresa, 0, requeridas, Estado.SIN_CICLO)
    val hechas = visitasContadas(c.clienteId, c.empresaId, ciclo)
    val faltan = requeridas - hechas
    val fin = LocalDate.ofEpochDay(ciclo.fin)
    val estado = when {
        faltan <= 0 -> Estado.COMPLETO
        diasHabilesRestantes(hoy, fin) <= diasAviso || diasQueAtiendeRestantes(c.clienteId, hoy, fin) < faltan -> Estado.EN_RIESGO
        else -> Estado.PENDIENTE
    }
    return EstadoEnEmpresa(empresa, hechas, requeridas, estado)
}

fun Datos.estadosDe(clienteId: Long, hoy: LocalDate) = cartera.filter { it.clienteId == clienteId }.map { estadoEn(it, hoy) }

fun Datos.peorEstado(clienteId: Long, hoy: LocalDate): Estado = estadosDe(clienteId, hoy).minOfOrNull { it.estado } ?: Estado.SIN_CICLO

fun Datos.avance(empresa: Empresa, hoy: LocalDate): AvanceEmpresa {
    val estados = cartera.filter { it.empresaId == empresa.id }.map { estadoEn(it, hoy) }
    val ciclo = cicloVigente(empresa.id, hoy)
    return AvanceEmpresa(
        empresa = empresa,
        ciclo = ciclo,
        hechas = estados.sumOf { minOf(it.hechas, it.requeridas) },
        requeridas = estados.sumOf { it.requeridas },
        diasRestantes = ciclo?.let { (it.fin - hoy.epoch() + 1).toInt() } ?: 0,
        enRiesgo = estados.count { it.estado == Estado.EN_RIESGO },
    )
}

/** Tramos de hoy de un cliente, por lugar. */
fun Datos.horariosDelDia(clienteId: Long, dia: DayOfWeek): List<Pair<Lugar, List<Horario>>> =
    atenciones.filter { it.clienteId == clienteId }.mapNotNull { a ->
        val tramos = horarios.filter { it.atencionId == a.id && it.diaSemana == dia.value && !it.porRevisar }.sortedBy { it.abre }
        val lugar = lugares.firstOrNull { it.id == a.lugarId }
        if (tramos.isEmpty() || lugar == null) null else lugar to tramos
    }

fun hora(minutos: Int) = "%02d:%02d".format(minutos / 60, minutos % 60)
