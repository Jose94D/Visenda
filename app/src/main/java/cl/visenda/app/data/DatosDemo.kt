package cl.visenda.app.data

import cl.visenda.app.dominio.Datos
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Datos inventados para el prototipo del Hito 2 (nombres y teléfonos ficticios). Las fechas se calculan
 * desde hoy, así el ciclo siempre está vigente y siempre hay clientes en cada estado.
 */
object DatosDemo {
    private val LV = (1..5).toList()

    fun datos(hoy: LocalDate): Datos {
        val inicioMes = hoy.withDayOfMonth(1)
        val finMes = hoy.withDayOfMonth(hoy.lengthOfMonth())
        val empresas = listOf(Empresa(1, "Laboratorio Andes", 1), Empresa(2, "Pharma Sur", 1))
        val ciclos = listOf(
            Ciclo(1, 1, "Ciclo ${mes(hoy)}", inicioMes.toEpochDay(), finMes.toEpochDay()),
            Ciclo(2, 2, "Ciclo ${mes(hoy)}", inicioMes.toEpochDay(), minOf(inicioMes.plusDays(23), finMes).toEpochDay()),
        )
        val categorias = listOf(Categoria(1, 1, "A", 2), Categoria(2, 1, "B", 1), Categoria(3, 1, "C", 1), Categoria(4, 2, "A", 1), Categoria(5, 2, "B", 1))
        val lugares = listOf(
            Lugar(1, "Centro Médico Lincoyán", "Lincoyán 470, Local 4, Concepción", -36.8262, -73.0478),
            Lugar(2, "Edificio Médico Paicaví", "Av. Paicaví 120, Of. 304, Concepción", -36.8203, -73.0446),
            Lugar(3, "Consulta Barros Arana", "Barros Arana 1050, Of. 21, Concepción", -36.8279, -73.0457),
            Lugar(4, "Centro Médico San Pedro", "Av. Pedro Aguirre Cerda 1500, San Pedro de la Paz", -36.8430, -73.1090, corregidoManual = true),
            Lugar(5, "Centro Médico Talcahuano", "Colón 3230, Local 12, Talcahuano", -36.7249, -73.1168),
            Lugar(6, "Consulta Chiguayante", "Av. Manuel Rodríguez 1080, Chiguayante", -36.9250, -73.0280),
            Lugar(7, "Torre Médica Collao", "Av. Collao 1202, Concepción", -36.8140, -73.0230),
            Lugar(8, "Centro Médico Chillán", "Arauco 645, Chillán", -36.6066, -72.1034),
        )
        // (id, nombre, especialidad, [(lugar, días, abre, cierra)…], cartera [(empresa, categoría, frecuencia manual)])
        data class C(val id: Long, val nombre: String, val esp: String, val atiende: List<List<Any>>, val cartera: List<Triple<Long, Long?, Int?>>)
        val clientes = listOf(
            C(1, "Dra. Valentina Rojas", "Cardiología", listOf(listOf(2L, listOf(1, 3, 4, 5), 540, 780), listOf(4L, listOf(2, 4), 900, 1140)), listOf(Triple(1L, 1L, null), Triple(2L, 5L, null))),
            C(2, "Dr. Andrés Muñoz", "Traumatología", listOf(listOf(3L, listOf(3), 540, 750)), listOf(Triple(1L, 1L, 5))), // solo miércoles, 5 visitas: en riesgo
            C(3, "Dr. Felipe Soto", "Pediatría", listOf(listOf(1L, LV, 600, 840)), listOf(Triple(1L, 2L, null))),
            C(4, "Dra. Josefina Pérez", "Dermatología", listOf(listOf(1L, LV, 660, 810)), listOf(Triple(1L, 3L, null), Triple(2L, 4L, null))),
            C(5, "Dr. Tomás Fuentes", "Medicina general", listOf(listOf(5L, LV, 960, 1140)), listOf(Triple(1L, 2L, null))),
            C(6, "Dra. Carolina Lagos", "Ginecología", listOf(listOf(8L, listOf(5), 540, 780)), listOf(Triple(1L, 1L, 4))), // solo viernes en Chillán
            C(7, "Dr. Matías Castro", "Neurología", listOf(listOf(7L, LV, 510, 780)), listOf(Triple(1L, 2L, null))),
            C(8, "Dra. Fernanda Núñez", "Oftalmología", listOf(listOf(2L, LV, 900, 1140)), listOf(Triple(2L, 4L, null))),
            C(9, "Dr. Ignacio Peña", "Cardiología", listOf(listOf(6L, listOf(1, 2, 3, 4), 540, 780)), listOf(Triple(1L, 1L, null))),
            C(10, "Dra. Camila Ibáñez", "Pediatría", listOf(listOf(1L, LV, 900, 1080), listOf(6L, listOf(6), 600, 780)), listOf(Triple(1L, 2L, null), Triple(2L, 5L, null))),
            C(11, "Dr. Sebastián Vera", "Urología", listOf(listOf(3L, LV, 540, 720)), listOf(Triple(2L, 4L, null))),
            C(12, "Dra. Antonia Riquelme", "Endocrinología", listOf(listOf(7L, listOf(2, 4), 840, 1080)), listOf(Triple(1L, 3L, null))),
            C(13, "Dr. Joaquín Sepúlveda", "Medicina interna", listOf(listOf(4L, LV, 540, 1020)), listOf(Triple(1L, 2L, null))),
            C(14, "Dra. Isidora Contreras", "Psiquiatría", listOf(listOf(5L, listOf(1, 3, 5), 540, 780)), listOf(Triple(2L, 5L, null))),
        )
        var atencionId = 0L
        var horarioId = 0L
        val atenciones = mutableListOf<Atencion>()
        val horarios = mutableListOf<Horario>()
        clientes.forEach { c ->
            c.atiende.forEach { (lugar, dias, abre, cierra) ->
                val a = Atencion(++atencionId, c.id, lugar as Long)
                atenciones += a
                @Suppress("UNCHECKED_CAST")
                (dias as List<Int>).forEach { horarios += Horario(++horarioId, a.id, it, abre as Int, cierra as Int) }
            }
        }
        val dd = DateTimeFormatter.ofPattern("dd-MM")
        val campos = clientes.flatMap { c ->
            val empresa = c.cartera.first().first
            listOf(
                ClienteCampo(c.id, empresa, "Teléfono", "+56 9 0000 %04d".format(c.id)),
                ClienteCampo(c.id, empresa, "Mejor horario", if (c.id % 2 == 0L) "Antes de las 10:00" else "Después de almuerzo"),
            ) + when (c.id) {
                1L -> listOf(ClienteCampo(1, 1, "Cumpleaños", hoy.plusDays(2).format(dd)), ClienteCampo(1, 1, "Secretaria", "Marcela"))
                7L -> listOf(ClienteCampo(7, 1, "Cumpleaños", hoy.plusDays(5).format(dd)))
                else -> listOf(ClienteCampo(c.id, empresa, "Cumpleaños", hoy.plusDays(40L + c.id * 9).format(dd)))
            }
        }
        // Visitas ya hechas este ciclo: (cliente, lugar, días atrás, empresas)
        val hechas = listOf(
            listOf(1L, 2L, 6L, listOf(1L, 2L)), listOf(3L, 1L, 4L, listOf(1L)), listOf(7L, 7L, 3L, listOf(1L)),
            listOf(8L, 2L, 2L, listOf(2L)), listOf(13L, 4L, 1L, listOf(1L)), listOf(11L, 3L, 5L, listOf(2L)),
        )
        val visitas = mutableListOf<Visita>()
        val visitaEmpresa = mutableListOf<VisitaEmpresa>()
        hechas.forEachIndexed { i, (cliente, lugar, diasAtras, emps) ->
            val fecha = maxOf(inicioMes, hoy.minusDays(diasAtras as Long))
            val v = Visita(i + 1L, cliente as Long, lugar as Long, fecha.atTime(10, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), "Entregadas muestras")
            visitas += v
            @Suppress("UNCHECKED_CAST")
            (emps as List<Long>).forEach { visitaEmpresa += VisitaEmpresa(v.id, it) }
        }
        return Datos(
            empresas = empresas,
            ciclos = ciclos,
            categorias = categorias,
            clientes = clientes.map { Cliente(it.id, "00.000.%03d-%d".format(it.id, it.id % 10), it.nombre, it.esp) },
            cartera = clientes.flatMap { c -> c.cartera.map { (e, cat, manual) -> Cartera(c.id, e, cat, manual, "K%04d".format(c.id)) } },
            campos = campos,
            lugares = lugares,
            atenciones = atenciones,
            horarios = horarios,
            visitas = visitas,
            visitaEmpresa = visitaEmpresa,
        )
    }

    private fun mes(d: LocalDate) =
        d.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("es-CL"))
}
