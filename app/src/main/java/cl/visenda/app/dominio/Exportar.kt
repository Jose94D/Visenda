package cl.visenda.app.dominio

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FECHA_HORA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
private val DIAS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

/**
 * CA-5.3 (derecho de acceso, Ley 21.719): todo lo que la app guarda de un cliente, en texto legible
 * para entregárselo a la persona que lo pide.
 */
fun Datos.exportarCliente(clienteId: Long, ahora: LocalDateTime, zona: ZoneId = ZoneId.systemDefault()): String {
    val c = clientes.first { it.id == clienteId }
    val t = StringBuilder()
    fun linea(s: String = "") = t.appendLine(s)
    linea("DATOS PERSONALES GUARDADOS EN VISENDA")
    linea("Generado el ${ahora.format(FECHA_HORA)}. Estos datos están solo en el teléfono del visitador.")
    linea()
    linea("Nombre: ${c.nombre}")
    c.rut?.let { linea("RUT: $it") }
    c.especialidad?.let { linea("Especialidad: $it") }

    linea(); linea("EMPRESAS")
    cartera.filter { it.clienteId == clienteId }.forEach { cart ->
        val e = empresas.first { it.id == cart.empresaId }
        linea("- ${e.nombre}" + listOfNotNull(
            cart.codigoKardex?.let { "código $it" },
            categorias.firstOrNull { it.id == cart.categoriaId }?.let { "categoría ${it.nombre}" },
            "${frecuenciaRequerida(cart)} visitas por ciclo",
        ).joinToString(", ", " (", ")"))
        campos.filter { it.clienteId == clienteId && it.empresaId == e.id }.forEach { linea("    ${it.campo}: ${it.valor}") }
    }

    linea(); linea("LUGARES Y HORARIOS DE ATENCIÓN")
    atenciones.filter { it.clienteId == clienteId }.forEach { a ->
        val l = lugares.first { it.id == a.lugarId }
        linea("- " + listOfNotNull(l.nombre, l.direccion).joinToString(", "))
        horarios.filter { it.atencionId == a.id }.sortedWith(compareBy({ it.diaSemana }, { it.abre })).forEach { h ->
            linea(if (h.porRevisar) "    Por revisar: ${h.textoOriginal}" else "    ${DIAS[h.diaSemana - 1]} ${hora(h.abre)}–${hora(h.cierra)}")
        }
    }

    linea(); linea("VISITAS REGISTRADAS")
    val suyas = visitas.filter { it.clienteId == clienteId }.sortedBy { it.fechaHora }
    if (suyas.isEmpty()) linea("Ninguna.")
    suyas.forEach { v ->
        val cuando = java.time.Instant.ofEpochMilli(v.fechaHora).atZone(zona).toLocalDateTime().format(FECHA_HORA)
        val donde = lugares.firstOrNull { it.id == v.lugarId }?.let { it.nombre ?: it.direccion }
        val para = visitaEmpresa.filter { it.visitaId == v.id }.mapNotNull { ve -> empresas.firstOrNull { it.id == ve.empresaId }?.nombre }
        linea("- " + listOfNotNull(cuando, donde, para.joinToString(" · ").ifEmpty { null }, v.notas).joinToString(" — "))
    }
    return t.toString()
}
