package cl.visenda.app.ui.pantallas

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.data.Horario
import cl.visenda.app.dominio.Estado
import cl.visenda.app.dominio.estadosDe
import cl.visenda.app.dominio.fechaDeVisita
import cl.visenda.app.dominio.hora
import cl.visenda.app.dominio.horariosDelDia
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.AvisarFocoDeDialogo
import cl.visenda.app.ui.Avatar
import cl.visenda.app.ui.BarraProgreso
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Etiqueta
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.Pantalla
import cl.visenda.app.ui.Tarjeta
import cl.visenda.app.ui.TituloSeccion
import java.time.format.DateTimeFormatter

private val DIAS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

/** Agrupa días con el mismo horario: "Lun · Mié · Vie" → "09:00–13:00". */
fun resumenHorarios(horarios: List<Horario>): List<Pair<String, String>> =
    horarios.groupBy { it.diaSemana }
        .mapValues { (_, h) -> h.sortedBy { it.abre }.joinToString(" · ") { "${hora(it.abre)}–${hora(it.cierra)}" } }
        .entries.groupBy({ it.value }, { it.key })
        .map { (tramos, dias) -> dias.sorted() to tramos }
        .sortedBy { (dias, _) -> dias.first() }
        .map { (dias, tramos) -> textoDias(dias) to tramos }

/** [1,2,3,4,5] → "Lun a Vie"; [1,3,5] → "Lun · Mié · Vie"; tres o más días seguidos se resumen como rango. */
fun textoDias(dias: List<Int>): String {
    val rachas = mutableListOf<MutableList<Int>>()
    dias.sorted().forEach { d -> if (rachas.lastOrNull()?.last() == d - 1) rachas.last() += d else rachas += mutableListOf(d) }
    return rachas.flatMap { r -> if (r.size >= 3) listOf("${DIAS[r.first() - 1]} a ${DIAS[r.last() - 1]}") else r.map { DIAS[it - 1] } }
        .joinToString(" · ")
}

@Composable
fun Perfil(estado: AppEstado, nav: Navegador, clienteId: Long, modifier: Modifier) {
    val d = estado.datos
    val cliente = d.clientes.firstOrNull { it.id == clienteId } ?: return
    val context = LocalContext.current
    val estados = d.estadosDe(clienteId, estado.hoy)
    val peor = estados.minOfOrNull { it.estado } ?: Estado.SIN_CICLO
    val telefono = d.campos.firstOrNull { it.clienteId == clienteId && it.campo.startsWith("Tel", ignoreCase = true) }?.valor
    val visitadoHoy = estado.visitadoHoy(clienteId)
    val enPlan = clienteId in estado.seleccion
    val scope = rememberCoroutineScope()
    var eliminar by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            aviso = runCatching { estado.exportarCliente(clienteId, uri) }.fold({ "Datos exportados." }, { "No se pudo exportar: ${it.message}" })
        }
    }
    if (eliminar) {
        var confirmacion by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { eliminar = false },
            title = { AvisarFocoDeDialogo(); Text("¿Eliminar a ${cliente.nombre}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se borran todos sus datos: empresas, campos del kardex, lugares, horarios y visitas. No se puede deshacer.")
                    Text("Escribe su nombre para confirmar:", fontSize = 13.sp)
                    OutlinedTextField(confirmacion, { confirmacion = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { eliminar = false; scope.launch { estado.eliminarCliente(clienteId); nav.volver() } },
                    enabled = confirmacion.trim() == cliente.nombre,
                ) { Text("Eliminar", color = Colores.Naranja) }
            },
            dismissButton = { TextButton(onClick = { eliminar = false }) { Text("Cancelar") } },
        )
    }

    LazyColumn(modifier, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            IconButton(onClick = { nav.volver() }, modifier = Modifier.padding(top = 4.dp)) { Icon(Iconos.Atras, "Volver") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(cliente.nombre, peor, tamano = 64.dp)
                Column {
                    Text(cliente.nombre, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(cliente.especialidad, cliente.rut?.let { "RUT $it" }).joinToString(" · "), color = Colores.Secundario, fontSize = 14.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Accion("Llamar", Iconos.Telefono, Modifier.weight(1f), habilitado = telefono != null) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${telefono.orEmpty().filter { it.isDigit() || it == '+' }}")))
                }
                Accion(if (visitadoHoy) "Visitado hoy" else "Marcar visita", Iconos.Listo, Modifier.weight(1f), principal = true, habilitado = !visitadoHoy) {
                    estado.marcarVisita(clienteId, d.horariosDelDia(clienteId, estado.hoy.dayOfWeek).firstOrNull()?.first?.id)
                }
                Accion(if (enPlan) "En el plan" else "Agregar a hoy", Iconos.Agregar, Modifier.weight(1f), habilitado = !enPlan) {
                    estado.seleccion = estado.seleccion + clienteId
                }
            }
        }
        if (estados.isNotEmpty()) item {
            Tarjeta {
                TituloSeccion("Este ciclo")
                estados.forEach { e ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(e.empresa.nombre, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (e.estado == Estado.EN_RIESGO) Etiqueta("En riesgo", Estado.EN_RIESGO)
                            Text("${e.hechas} de ${e.requeridas}", fontSize = 14.sp)
                        }
                    }
                    BarraProgreso(if (e.requeridas == 0) 1f else e.hechas.toFloat() / e.requeridas, "${e.hechas} de ${e.requeridas} visitas")
                }
            }
        }
        item { TituloSeccion("Dónde atiende") }
        d.atenciones.filter { it.clienteId == clienteId }.forEach { a ->
            val lugar = d.lugares.firstOrNull { it.id == a.lugarId } ?: return@forEach
            item {
                Tarjeta {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Iconos.Lugar, null, tint = Colores.Azul, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lugar.nombre ?: lugar.direccion, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(lugar.direccion, color = Colores.Secundario, fontSize = 13.sp)
                            when {
                                lugar.lat == null -> Column(Modifier.padding(top = 6.dp)) { Etiqueta("Sin ubicar: no entra a las rutas", Estado.EN_RIESGO) }
                                lugar.corregidoManual -> Column(Modifier.padding(top = 6.dp)) { Etiqueta("Ubicación ajustada a mano", Estado.COMPLETO) }
                            }
                            Text(
                                if (lugar.lat == null) "Ubicar en el mapa" else "Corregir ubicación",
                                color = Colores.AzulTexto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp).clickable { nav.ir(Pantalla.Ubicar(lugar.id)) }.padding(vertical = 6.dp),
                            )
                        }
                    }
                    val (porRevisar, legibles) = d.horarios.filter { it.atencionId == a.id }.partition { it.porRevisar }
                    resumenHorarios(legibles).forEach { (dias, tramos) ->
                        Row {
                            Text(dias, color = Colores.Secundario, fontSize = 13.sp, modifier = Modifier.width(120.dp))
                            Text(tramos, fontSize = 13.sp)
                        }
                    }
                    porRevisar.forEach {
                        Text("Horario por revisar: \"${it.textoOriginal}\"", color = Colores.Naranja, fontSize = 13.sp)
                    }
                }
            }
        }
        val campos = d.campos.filter { it.clienteId == clienteId }
        if (campos.isNotEmpty()) {
            item { TituloSeccion("Datos del kardex") }
            item {
                Tarjeta(relleno = 0.dp) {
                    Column {
                        campos.forEachIndexed { i, c ->
                            if (i > 0) HorizontalDivider(color = Colores.Separador)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(c.campo, color = Colores.Secundario, fontSize = 13.sp)
                                Text(c.valor, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
        item { TituloSeccion("Historial de visitas") }
        val visitas = d.visitas.filter { it.clienteId == clienteId }
        if (visitas.isEmpty()) item { Text("Sin visitas registradas.", color = Colores.Secundario, fontSize = 14.sp) }
        else item {
            Tarjeta(relleno = 0.dp) {
                Column {
                    visitas.forEachIndexed { i, v ->
                        if (i > 0) HorizontalDivider(color = Colores.Separador)
                        val empresas = d.visitaEmpresa.filter { it.visitaId == v.id }.mapNotNull { ve -> d.empresas.firstOrNull { it.id == ve.empresaId }?.nombre }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(fechaDeVisita(v).format(DateTimeFormatter.ofPattern("d 'de' MMMM", ES)), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                listOfNotNull(d.lugares.firstOrNull { it.id == v.lugarId }?.nombre, empresas.joinToString(" · "), v.notas).filter { it.isNotBlank() }.joinToString(" — "),
                                color = Colores.Secundario, fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
        item { TituloSeccion("Datos personales") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportar.launch("visenda-${cliente.nombre.filter { it.isLetterOrDigit() }}.txt") },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                ) { Text("Exportar datos de este cliente") }
                OutlinedButton(onClick = { eliminar = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Eliminar cliente", color = Colores.Naranja)
                }
                aviso?.let { Text(it, color = Colores.Secundario, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun Accion(texto: String, icono: ImageVector, modifier: Modifier, principal: Boolean = false, habilitado: Boolean = true, alTocar: () -> Unit) {
    val forma = RoundedCornerShape(14.dp)
    val contenido: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icono, null, modifier = Modifier.size(20.dp))
            Text(texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
    if (principal) {
        Button(onClick = alTocar, enabled = habilitado, shape = forma, modifier = modifier.height(64.dp), contentPadding = PaddingValues(4.dp)) { contenido() }
    } else {
        OutlinedButton(
            onClick = alTocar, enabled = habilitado, shape = forma, modifier = modifier.height(64.dp), contentPadding = PaddingValues(4.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Colores.Tinta),
        ) { contenido() }
    }
}
