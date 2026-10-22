package cl.visenda.app.ui.pantallas

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.dominio.Motivo
import cl.visenda.app.dominio.hora
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.MapaOffline
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.Pantalla
import cl.visenda.app.ui.Tarjeta
import cl.visenda.app.ui.TituloSeccion

@Composable
fun Ruta(estado: AppEstado, nav: Navegador, modifier: Modifier) {
    val ruta = estado.ruta
    val context = LocalContext.current
    if (ruta == null) {
        Column(modifier.padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Iconos.Ruta, null, tint = Colores.Azul, modifier = Modifier.size(48.dp))
            Text("Aún no hay ruta para hoy", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text("Elige a qué clientes visitar y la app calcula el recorrido.", color = Colores.Secundario, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
            Button(onClick = { nav.ir(Pantalla.Planificar) }, shape = RoundedCornerShape(14.dp)) { Text("Planificar el día") }
        }
        return
    }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(0.42f)) {
            MapaOffline(estado.archivosMapa, ruta.trazado, ruta.paradas.map { it.lugar.lat!! to it.lugar.lng!! }, Modifier.fillMaxSize())
        }
        LazyColumn(Modifier.weight(0.58f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    if (ruta.fecha == estado.hoy) "Ruta de hoy"
                    else "Ruta del " + ruta.fecha.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d", ES)),
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${ruta.paradas.size} paradas · ${"%.1f".format(ruta.km)} km · termina ${hora(ruta.termina)}" +
                        (ruta.vuelta?.let { " · de vuelta ${hora(it)}" } ?: "") + " · " + ruta.origen,
                    color = Colores.Secundario, fontSize = 13.sp,
                )
            }
            ruta.paradas.forEachIndexed { i, p ->
                // La colación tomada mientras se espera que abra no es espera.
                val almuerzaEsperando = ruta.colacion?.let { (inicio, antes) -> antes == i && inicio >= p.llegada } == true
                val espera = p.espera - if (almuerzaEsperando) estado.reglas.colacion else 0
                if (ruta.colacion?.second == i) item {
                    Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Iconos.Reloj, null, tint = Colores.Secundario, modifier = Modifier.size(18.dp))
                        Text("Colación a las ${hora(ruta.colacion.first)} · ${estado.reglas.colacion} min", color = Colores.Secundario, fontSize = 14.sp)
                    }
                }
                item {
                    val todosVisitados = p.clientes.all { estado.visitadoHoy(it.id) }
                    Tarjeta(relleno = 14.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(if (todosVisitados) Colores.Borde else Colores.Azul),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (todosVisitados) Icon(Iconos.Listo, "Visitada", tint = Colores.Secundario, modifier = Modifier.size(16.dp))
                                else Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(p.lugar.nombre ?: p.lugar.direccion, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    Text(hora(p.llegada), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                                Text(p.lugar.direccion, color = Colores.Secundario, fontSize = 13.sp)
                                Text(p.clientes.joinToString(", ") { it.nombre }, fontSize = 13.sp)
                                Text(
                                    if (espera > 0) "Abre a las ${hora(p.inicio)} (esperas $espera min) · hasta ${hora(p.cierra)}"
                                    else if (p.espera > 0) "Abre a las ${hora(p.inicio)} · hasta ${hora(p.cierra)}"
                                    else "Atiende hasta ${hora(p.cierra)}",
                                    color = Colores.Secundario, fontSize = 13.sp,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { p.clientes.filterNot { estado.visitadoHoy(it.id) }.forEach { estado.marcarVisita(it.id, p.lugar.id) } },
                                enabled = !todosVisitados, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(44.dp),
                            ) {
                                Icon(Iconos.Listo, null, modifier = Modifier.size(18.dp))
                                Text(if (todosVisitados) "  Visitada" else "  Marcar visita")
                            }
                            OutlinedButton(
                                onClick = {
                                    val uri = Uri.parse("geo:${p.lugar.lat},${p.lugar.lng}?q=${p.lugar.lat},${p.lugar.lng}(${Uri.encode(p.lugar.nombre ?: "Destino")})")
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                },
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.height(44.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Colores.Tinta),
                            ) {
                                Icon(Iconos.Navegar, null, modifier = Modifier.size(18.dp))
                                Text("  Navegar")
                            }
                        }
                    }
                }
            }
            if (ruta.fuera.isNotEmpty()) {
                item { TituloSeccion("No caben en esta ruta") }
                item {
                    Tarjeta(relleno = 0.dp) {
                        Column {
                            ruta.fuera.forEachIndexed { i, (cliente, motivo) ->
                                if (i > 0) HorizontalDivider(color = Colores.Separador)
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Text(cliente.nombre, fontSize = 15.sp)
                                    Text(
                                        when (motivo) {
                                            Motivo.SIN_CAMINO -> "No hay camino en los mapas descargados"
                                            Motivo.HORARIO -> "Ya habrá cerrado cuando alcances a llegar"
                                            Motivo.FIN_DE_JORNADA -> "Su horario cae después del fin de la jornada"
                                            Motivo.NO_CABE -> "No alcanza junto con los demás; quedó fuera por prioridad"
                                        },
                                        color = Colores.Naranja, fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
