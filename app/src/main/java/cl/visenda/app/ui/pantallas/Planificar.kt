package cl.visenda.app.ui.pantallas

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.dominio.Estado
import cl.visenda.app.dominio.cicloVigente
import cl.visenda.app.dominio.diasQueAtiendeRestantes
import cl.visenda.app.dominio.estadosDe
import cl.visenda.app.dominio.esHabil
import cl.visenda.app.dominio.habilSiguiente
import cl.visenda.app.dominio.hora
import cl.visenda.app.dominio.horariosDelDia
import cl.visenda.app.dominio.agruparParadas
import cl.visenda.app.dominio.visitables
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Etiqueta
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.Pantalla
import cl.visenda.app.ui.Tarjeta
import cl.visenda.app.ui.TituloSeccion
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val PERMISOS_UBICACION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
fun Planificar(estado: AppEstado, nav: Navegador, modifier: Modifier) {
    val d = estado.datos
    val r = estado.reglas
    val hoy = estado.hoy
    val ahora = LocalTime.now().let { it.hour * 60 + it.minute }
    // Terminada la jornada (o en un día no hábil) se planifica el día hábil siguiente: es cuando el visitador arma su ruta.
    val fecha = if (ahora < r.finJornada && r.esHabil(hoy)) hoy else r.habilSiguiente(hoy)
    val salida = if (fecha == hoy) maxOf(r.salida, ahora) else r.salida
    val nombreDia = when (fecha) {
        hoy -> "el día"
        hoy.plusDays(1) -> "mañana"
        else -> "el " + fecha.format(DateTimeFormatter.ofPattern("EEEE d", ES))
    }

    // RN-06: pendientes con horario ese día que termina después de la salida. Orden: los sugeridos en el orden de la ruta;
    // después en riesgo, menos días disponibles y más visitas faltantes.
    data class Candidato(val id: Long, val nombre: String, val esp: String?, val estado: Estado, val faltan: Int, val diasDisponibles: Int, val horario: String, val oficinas: Int)
    val candidatos = d.clientes.mapNotNull { c ->
        val estados = d.estadosDe(c.id, hoy).filter { it.estado == Estado.EN_RIESGO || it.estado == Estado.PENDIENTE }
        val atiende = d.horariosDelDia(c.id, fecha.dayOfWeek).filter { (l, t) -> l.lat != null && t.any { it.cierra > salida } }
        if ((estados.isEmpty() && c.id !in estado.seleccion) || atiende.isEmpty()) return@mapNotNull null
        val finCiclo = estados.mapNotNull { d.cicloVigente(it.empresa.id, hoy)?.fin }.minOrNull()?.let(LocalDate::ofEpochDay) ?: hoy
        Candidato(
            c.id, c.nombre, c.especialidad, estados.minOfOrNull { it.estado } ?: Estado.COMPLETO, estados.sumOf { it.faltan },
            d.diasQueAtiendeRestantes(c.id, fecha, finCiclo),
            atiende.first().second.joinToString(" · ") { "${hora(it.abre)}–${hora(it.cierra)}" },
            d.atenciones.count { it.clienteId == c.id },
        )
    }.sortedWith(
        compareBy<Candidato> { estado.seleccion.indexOf(it.id).takeIf { i -> i >= 0 && it.id in estado.sugerencias } ?: Int.MAX_VALUE }
            .thenBy { it.estado }.thenBy { it.diasDisponibles }.thenByDescending { it.faltan },
    )

    // La ubicación se pide solo si no se eligió un punto en el mapa; si el usuario la niega, el error lo invita a elegirlo.
    val sugerir: () -> Unit = { estado.sugerir(fecha, salida, candidatos.map { it.id }) }
    val permisoSugerir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { sugerir() }
    val permisoRuta = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        estado.calcularRuta(fecha, salida) { nav.pestana(Pantalla.Ruta) }
    }
    val pedirSugerencias: () -> Unit = { if (estado.partidaElegida != null) sugerir() else permisoSugerir.launch(PERMISOS_UBICACION) }

    LaunchedEffect(Unit) {
        if (estado.seleccion.isEmpty() && candidatos.isNotEmpty()) pedirSugerencias()
    }
    val elegidos = candidatos.filter { it.id in estado.seleccion }
    val grupos = agruparParadas(d.visitables(elegidos.map { it.id }, fecha.dayOfWeek, salida, r.minutosVisita), r.minutosVisita)
    val paradas = grupos.size
    val compartidos = grupos.filter { it.clientes.size > 1 }.sumOf { it.clientes.size }

    Column(modifier) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }) { Icon(Iconos.Atras, "Volver") }
            Column {
                Text("Planificar $nombreDia", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Salida ${hora(salida)} · fin de jornada ${hora(r.finJornada)}" + if (r.colacion > 0) " · colación ${r.colacion} min" else "",
                    color = Colores.Secundario, fontSize = 13.sp,
                )
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Tarjeta(relleno = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Iconos.Lugar, null, tint = Colores.Azul, modifier = Modifier.size(20.dp))
                        Text(if (estado.partidaElegida == null) "Parte desde tu ubicación" else "Parte desde el punto elegido", fontSize = 15.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { nav.ir(Pantalla.ElegirPartida) }) { Text("Elegir en el mapa") }
                        if (estado.partidaElegida != null) TextButton(onClick = { estado.partidaElegida = null }) { Text("Usar mi ubicación") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Volver al punto de partida", fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Switch(checked = r.volver, onCheckedChange = { estado.guardarReglas(r.copy(volver = it)) })
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TituloSeccion(if (fecha == hoy) "Sugeridos para hoy" else "Sugeridos")
                    if (estado.sugiriendo) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else TextButton(onClick = pedirSugerencias, enabled = candidatos.isNotEmpty()) { Text("Sugerir de nuevo") }
                }
                Text(
                    "Según urgencia del ciclo, horarios, km y esperas. ${elegidos.size} de ${candidatos.size} candidatos elegidos.",
                    color = Colores.Secundario, fontSize = 13.sp,
                )
            }
            if (candidatos.isEmpty()) item {
                Text("No hay clientes pendientes que atiendan ${if (fecha == hoy) "hoy después de las ${hora(salida)}" else nombreDia}.", color = Colores.Secundario)
            }
            items(candidatos, key = { it.id }) { c ->
                val marcado = c.id in estado.seleccion
                Tarjeta(
                    Modifier.clickable(role = Role.Checkbox) {
                        estado.seleccion = if (marcado) estado.seleccion - c.id else estado.seleccion + c.id
                    },
                    relleno = 12.dp,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = marcado, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Colores.Azul), modifier = Modifier.padding(top = 2.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(c.nombre, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                when (c.estado) {
                                    Estado.EN_RIESGO -> Etiqueta("En riesgo · faltan ${c.faltan}", Estado.EN_RIESGO)
                                    Estado.PENDIENTE -> Etiqueta("Faltan ${c.faltan}", Estado.PENDIENTE)
                                    else -> Etiqueta("Agregado", Estado.COMPLETO)
                                }
                            }
                            Text(listOfNotNull(c.esp, if (c.oficinas > 1) "${c.oficinas} oficinas" else null).joinToString(" · "), color = Colores.Secundario, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Iconos.Reloj, null, tint = Colores.Secundario, modifier = Modifier.size(14.dp))
                                Text(c.horario, fontSize = 13.sp)
                            }
                            estado.sugerencias[c.id]?.let { Text("Sugerido: $it", color = Colores.AzulTexto, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = Colores.Borde)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("${elegidos.size} clientes · $paradas paradas", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                estado.errorRuta?.let { Text(it, color = Colores.Naranja, fontSize = 12.sp) }
                    ?: if (compartidos > 0) Text("$compartidos se visitan en el mismo lugar", color = Colores.Secundario, fontSize = 13.sp) else Unit
            }
            Button(
                onClick = {
                    if (estado.partidaElegida != null) estado.calcularRuta(fecha, salida) { nav.pestana(Pantalla.Ruta) }
                    else permisoRuta.launch(PERMISOS_UBICACION)
                },
                enabled = elegidos.isNotEmpty() && !estado.sugiriendo, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(52.dp),
            ) {
                Text("Calcular ruta", fontWeight = FontWeight.SemiBold)
                Icon(Iconos.Flecha, null, modifier = Modifier.padding(start = 6.dp).size(18.dp), tint = Color.White)
            }
        }
    }
}
