package cl.visenda.app.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.mapas.EstadoRegion
import cl.visenda.app.mapas.Region
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.BarraProgreso
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.Tarjeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun mb(bytes: Long) = if (bytes >= 1_000_000_000) "%.1f GB".format(bytes / 1e9) else "${bytes / 1_000_000} MB"

/** Ajustes > Mapas sin conexión: las 16 regiones, con descarga que se reanuda y opción "Solo con Wi-Fi". */
@Composable
fun Regiones(estado: AppEstado, nav: Navegador, modifier: Modifier) {
    val g = estado.mapas
    val scope = rememberCoroutineScope()
    var indice by remember { mutableStateOf(g.indiceGuardado()) }
    var error by remember { mutableStateOf<String?>(null) }
    var soloWifi by remember { mutableStateOf(g.soloWifi) }
    var estados by remember { mutableStateOf(mapOf<String, EstadoRegion>()) }
    var espacio by remember { mutableStateOf(0L) }
    var aBorrar by remember { mutableStateOf<Region?>(null) }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { g.actualizarIndice() } }
            .onSuccess { indice = it; error = null }
            .onFailure { error = it.message }
    }
    // Mientras la pantalla está abierta: refresca el avance cada segundo e instala lo que terminó de bajar.
    LaunchedEffect(indice) {
        val i = indice ?: return@LaunchedEffect
        while (true) {
            estados = withContext(Dispatchers.IO) { i.regiones.associate { it.clave to g.estado(it) } }
            espacio = withContext(Dispatchers.IO) { g.espacioUsado() }
            if (estados.values.any { it == EstadoRegion.Instalando }) {
                if (withContext(Dispatchers.IO) { g.instalarTerminadas(i) }) estado.mapasCambiaron()
                continue
            }
            delay(1000)
        }
    }

    Column(modifier) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }) { Icon(Iconos.Atras, "Volver") }
            Column {
                Text("Mapas sin conexión", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("En uso: ${mb(espacio)}" + (indice?.let { " · datos del ${it.fecha}" } ?: ""), color = Colores.Secundario, fontSize = 13.sp)
            }
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Tarjeta {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Solo con Wi-Fi", fontSize = 15.sp)
                            Text("Las descargas esperan a que estés en Wi-Fi", color = Colores.Secundario, fontSize = 13.sp)
                        }
                        Switch(checked = soloWifi, onCheckedChange = { soloWifi = it; g.soloWifi = it })
                    }
                }
            }
            item {
                Text(
                    "Descarga las regiones donde trabajas. Sin ellas no se pueden calcular rutas ni ubicar direcciones. Datos © OpenStreetMap.",
                    color = Colores.Secundario, fontSize = 13.sp,
                )
            }
            error?.let { item { Text(it, color = Colores.Naranja, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) } }
            items(indice?.regiones.orEmpty(), key = { it.clave }) { r ->
                val e = estados[r.clave] ?: EstadoRegion.Disponible
                Tarjeta(relleno = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(r.nombre, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                when (e) {
                                    EstadoRegion.Disponible -> mb(r.bytes)
                                    is EstadoRegion.Descargando -> if (e.esperandoWifi) "Esperando Wi-Fi…" else "Descargando ${(e.fraccion * 100).toInt()} % de ${mb(r.bytes)}"
                                    EstadoRegion.Instalando -> "Instalando…"
                                    EstadoRegion.Instalada -> "Descargada · ${mb(r.bytes)}"
                                    is EstadoRegion.Error -> e.mensaje
                                },
                                color = if (e is EstadoRegion.Error) Colores.Naranja else Colores.Secundario, fontSize = 13.sp,
                            )
                        }
                        when (e) {
                            EstadoRegion.Disponible, is EstadoRegion.Error ->
                                Button(onClick = { scope.launch(Dispatchers.IO) { g.descargar(r) } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(44.dp)) {
                                    Text(if (e is EstadoRegion.Error) "Reintentar" else "Descargar")
                                }
                            is EstadoRegion.Descargando ->
                                OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { g.cancelar(r) } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(44.dp)) { Text("Cancelar") }
                            EstadoRegion.Instalada ->
                                TextButton(onClick = { aBorrar = r }, modifier = Modifier.height(44.dp)) { Text("Borrar", color = Colores.Naranja) }
                            EstadoRegion.Instalando -> Unit
                        }
                    }
                    if (e is EstadoRegion.Descargando) BarraProgreso(e.fraccion, "Avance de la descarga de ${r.nombre}", Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
        }
    }

    aBorrar?.let { r ->
        AlertDialog(
            onDismissRequest = { aBorrar = null },
            title = { Text("¿Borrar ${r.nombre}?") },
            text = { Text("Se liberan ${mb(r.bytes)}. Los clientes de esa región no entran a las rutas hasta volver a descargarla.") },
            confirmButton = {
                TextButton(onClick = {
                    aBorrar = null
                    scope.launch {
                        withContext(Dispatchers.IO) { g.borrar(r) }
                        estado.mapasCambiaron()
                    }
                }) { Text("Borrar", color = Colores.Naranja) }
            },
            dismissButton = { TextButton(onClick = { aBorrar = null }) { Text("Cancelar") } },
        )
    }
}
