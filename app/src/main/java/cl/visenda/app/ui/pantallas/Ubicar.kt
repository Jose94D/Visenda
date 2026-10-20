package cl.visenda.app.ui.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.MapaOffline
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.Pantalla
import cl.visenda.app.ui.SALIDA
import cl.visenda.app.ui.Tarjeta

/** Pantalla 5.7: el pin queda fijo al centro y el usuario mueve el mapa debajo. */
@Composable
fun Ubicar(estado: AppEstado, nav: Navegador, lugarId: Long, modifier: Modifier) {
    val lugar = estado.datos.lugares.firstOrNull { it.id == lugarId } ?: return
    val clientes = estado.datos.atenciones.filter { it.lugarId == lugarId }
        .mapNotNull { a -> estado.datos.clientes.firstOrNull { it.id == a.clienteId }?.nombre }
    var inicio by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // Sin estado de Compose: si el mapa se recompusiera con cada movimiento, volvería al punto inicial.
    val elegido = remember { doubleArrayOf(0.0, 0.0) }
    LaunchedEffect(lugarId) {
        val punto = estado.sugerirUbicacion(lugar) ?: SALIDA
        elegido[0] = punto.first; elegido[1] = punto.second
        inicio = punto
    }

    Column(modifier) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }) { Icon(Iconos.Atras, "Volver") }
            Column {
                Text(lugar.nombre ?: "Ubicar en el mapa", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(lugar.direccion, color = Colores.Secundario, fontSize = 13.sp)
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val punto = inicio
            if (punto == null) CircularProgressIndicator()
            else {
                MapaOffline(estado.archivoMapa, emptyList(), emptyList(), Modifier.fillMaxSize(), centro = punto) { lat, lng ->
                    elegido[0] = lat; elegido[1] = lng
                }
                // La punta del pin (abajo) marca el centro del mapa.
                Icon(Iconos.Lugar, "Ubicación elegida", tint = Colores.Azul, modifier = Modifier.size(44.dp).offset(y = (-20).dp))
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (clientes.isNotEmpty()) Text(
                "Atienden aquí: " + clientes.take(3).joinToString(", ") + if (clientes.size > 3) " y ${clientes.size - 3} más" else "",
                fontSize = 14.sp, maxLines = 2,
            )
            Text("Mueve el mapa hasta que el pin quede sobre la entrada.", color = Colores.Secundario, fontSize = 13.sp)
            Button(
                onClick = { estado.corregirUbicacion(lugar.id, elegido[0], elegido[1]); nav.volver() },
                enabled = inicio != null, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Confirmar ubicación", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Lugares que el geocodificador no encontró: no entran a las rutas hasta ubicarlos (RN-11). */
@Composable
fun SinUbicar(estado: AppEstado, nav: Navegador, modifier: Modifier) {
    val d = estado.datos
    val lugares = d.lugares.filter { it.lat == null }
    Column(modifier) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }) { Icon(Iconos.Atras, "Volver") }
            Text("Direcciones sin ubicar", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (lugares.isEmpty()) item { Text("Todas las direcciones están ubicadas.", color = Colores.Secundario) }
            items(lugares, key = { it.id }) { l ->
                val clientes = d.atenciones.filter { it.lugarId == l.id }.mapNotNull { a -> d.clientes.firstOrNull { it.id == a.clienteId }?.nombre }
                Tarjeta(Modifier.clickable { nav.ir(Pantalla.Ubicar(l.id)) }, relleno = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Iconos.Lugar, null, tint = Colores.Naranja, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.nombre ?: l.direccion, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            if (l.nombre != null) Text(l.direccion, color = Colores.Secundario, fontSize = 13.sp)
                            Text(
                                clientes.take(3).joinToString(", ") + if (clientes.size > 3) " y ${clientes.size - 3} más" else "",
                                fontSize = 13.sp,
                            )
                        }
                        Icon(Iconos.Siguiente, null, tint = Colores.Secundario, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
