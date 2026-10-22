package cl.visenda.app.ui.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.dominio.Reglas
import cl.visenda.app.dominio.hora
import cl.visenda.app.dominio.leerHora
import cl.visenda.app.dominio.validarReglas
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.AvisarFocoDeDialogo
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.Tarjeta

private enum class Campo { VISITA, AVISO, JORNADA, COLACION }

/** Ajustes > Planificación: cada fila abre un diálogo; sábados es un interruptor. */
@Composable
fun AjustesPlanificacion(estado: AppEstado) {
    val r = estado.reglas
    var editando by remember { mutableStateOf<Campo?>(null) }
    Tarjeta(relleno = 0.dp) {
        Column {
            Fila("Tiempo por visita", "${r.minutosVisita} minutos") { editando = Campo.VISITA }
            HorizontalDivider(color = Colores.Separador)
            Fila("Aviso de riesgo", "${r.diasAviso} días hábiles antes del cierre") { editando = Campo.AVISO }
            HorizontalDivider(color = Colores.Separador)
            Fila("Jornada", "Salida ${hora(r.salida)} · fin ${hora(r.finJornada)}") { editando = Campo.JORNADA }
            HorizontalDivider(color = Colores.Separador)
            Fila("Colación", if (r.colacion == 0) "Sin colación" else "${r.colacion} min entre ${hora(r.colacionDesde)} y ${hora(r.colacionHasta)}") { editando = Campo.COLACION }
            HorizontalDivider(color = Colores.Separador)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Trabajo los sábados", fontSize = 15.sp)
                    Text("Cuentan como días hábiles del ciclo. Los feriados nunca cuentan.", color = Colores.Secundario, fontSize = 13.sp)
                }
                Switch(checked = r.sabados, onCheckedChange = { estado.guardarReglas(r.copy(sabados = it)) })
            }
        }
    }
    editando?.let { campo -> Dialogo(campo, r, alGuardar = { estado.guardarReglas(it); editando = null }, alCerrar = { editando = null }) }
}

@Composable
private fun Fila(titulo: String, detalle: String, alTocar: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = alTocar).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, fontSize = 15.sp)
            Text(detalle, color = Colores.Secundario, fontSize = 13.sp)
        }
        Icon(Iconos.Siguiente, null, tint = Colores.Secundario, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Dialogo(campo: Campo, r: Reglas, alGuardar: (Reglas) -> Unit, alCerrar: () -> Unit) {
    val (titulo, campos) = when (campo) {
        Campo.VISITA -> "Tiempo por visita" to listOf("Minutos" to "${r.minutosVisita}")
        Campo.AVISO -> "Aviso de riesgo" to listOf("Días hábiles antes del cierre del ciclo" to "${r.diasAviso}")
        Campo.JORNADA -> "Jornada" to listOf("Salida (08:30)" to hora(r.salida), "Fin de jornada (18:00)" to hora(r.finJornada))
        Campo.COLACION -> "Colación" to listOf("Minutos (0 = sin colación)" to "${r.colacion}", "Desde" to hora(r.colacionDesde), "Hasta" to hora(r.colacionHasta))
    }
    val textos = remember { campos.map { mutableStateOf(it.second) } }
    var errores by remember { mutableStateOf(emptyList<String>()) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { AvisarFocoDeDialogo(); Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (campo == Campo.COLACION) Text("Visenda la ubica dentro de ese horario donde menos estorba, de preferencia mientras esperas que abra un cliente.", color = Colores.Secundario, fontSize = 13.sp)
                campos.forEachIndexed { i, (etiqueta, _) ->
                    OutlinedTextField(
                        textos[i].value, { textos[i].value = it }, label = { Text(etiqueta) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                errores.forEach { Text(it, color = Colores.Naranja, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = textos.map { it.value.trim() }
                val nueva = when (campo) {
                    Campo.VISITA -> v[0].toIntOrNull()?.let { r.copy(minutosVisita = it) }
                    Campo.AVISO -> v[0].toIntOrNull()?.let { r.copy(diasAviso = it) }
                    Campo.JORNADA -> { val s = leerHora(v[0]); val f = leerHora(v[1]); if (s != null && f != null) r.copy(salida = s, finJornada = f) else null }
                    Campo.COLACION -> {
                        val m = v[0].toIntOrNull(); val d = leerHora(v[1]); val h = leerHora(v[2])
                        if (m != null && d != null && h != null) r.copy(colacion = m, colacionDesde = d, colacionHasta = h) else null
                    }
                }
                errores = if (nueva == null) listOf("Escribe números enteros y las horas como 13:00.") else validarReglas(nueva)
                if (nueva != null && errores.isEmpty()) alGuardar(nueva)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}
