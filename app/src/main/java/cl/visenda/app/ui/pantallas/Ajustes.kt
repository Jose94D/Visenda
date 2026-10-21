package cl.visenda.app.ui.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.MINUTOS_POR_VISITA
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.Pantalla
import cl.visenda.app.ui.Tarjeta
import cl.visenda.app.ui.TituloSeccion
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun Ajustes(estado: AppEstado, nav: Navegador, modifier: Modifier) {
    val d = estado.datos
    val corto = DateTimeFormatter.ofPattern("d MMM", ES)
    val regiones = remember(estado.archivosMapa) { estado.mapas.instaladas().size }

    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Ajustes", fontSize = 26.sp, fontWeight = FontWeight.SemiBold) }

        item { TituloSeccion("Kardex") }
        item {
            Tarjeta(Modifier.clickable { nav.ir(Pantalla.Importar) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Iconos.Importar, null, tint = Colores.Azul, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Importar kardex", fontSize = 15.sp)
                        Text("Desde un archivo Excel (.xlsx)", color = Colores.Secundario, fontSize = 13.sp)
                    }
                    Icon(Iconos.Siguiente, null, tint = Colores.Secundario, modifier = Modifier.size(18.dp))
                }
            }
        }

        item { TituloSeccion("Empresas y ciclos") }
        item {
            Lista(d.empresas.map { e ->
                val ciclo = d.ciclos.filter { it.empresaId == e.id }.maxByOrNull { it.fin }
                val cats = d.categorias.filter { it.empresaId == e.id }
                e.nombre to listOfNotNull(
                    ciclo?.let { "${it.nombre}: ${LocalDate.ofEpochDay(it.inicio).format(corto)} – ${LocalDate.ofEpochDay(it.fin).format(corto)}" },
                    if (cats.isEmpty()) null else "Visitas por categoría: " + cats.joinToString(" · ") { "${it.nombre} ${it.frecuencia}" },
                ).joinToString("\n")
            })
        }

        item { TituloSeccion("Mapas sin conexión") }
        item {
            Tarjeta(Modifier.clickable { nav.ir(Pantalla.Regiones) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Iconos.Lugar, null, tint = Colores.Azul, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Elegir regiones", fontSize = 15.sp)
                        Text(
                            when {
                                regiones > 0 -> "$regiones ${if (regiones == 1) "región descargada" else "regiones descargadas"}"
                                estado.archivosMapa.isNotEmpty() -> "Biobío y Ñuble de prueba (Hito 1): descarga las regiones para reemplazarlos"
                                else -> "Ninguna descargada: sin ellas no hay rutas"
                            },
                            color = Colores.Secundario, fontSize = 13.sp,
                        )
                    }
                    Icon(Iconos.Siguiente, null, tint = Colores.Secundario, modifier = Modifier.size(18.dp))
                }
            }
        }

        item { TituloSeccion("Planificación") }
        item { Lista(listOf("Tiempo por visita" to "$MINUTOS_POR_VISITA minutos", "Aviso de riesgo" to "5 días hábiles antes del cierre")) }

        item { TituloSeccion("Seguridad y respaldo") }
        item { Lista(listOf("Bloqueo con huella" to "Disponible en el hito 4", "Respaldo cifrado" to "Disponible en el hito 3")) }

        item { TituloSeccion("Desarrollo") }
        item {
            Tarjeta(Modifier.clickable { nav.ir(Pantalla.Laboratorio) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Iconos.Laboratorio, null, tint = Colores.Azul, modifier = Modifier.size(20.dp))
                    Text("Laboratorio del hito 1", Modifier.weight(1f))
                    Icon(Iconos.Siguiente, null, tint = Colores.Secundario, modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            Text(
                "Tus datos se guardan solo en este teléfono. Nadie más, ni siquiera el desarrollador, tiene acceso.",
                color = Colores.Secundario, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Lista(filas: List<Pair<String, String>>) {
    Tarjeta(relleno = 0.dp) {
        Column {
            filas.forEachIndexed { i, (titulo, detalle) ->
                if (i > 0) HorizontalDivider(color = Colores.Separador)
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(titulo, fontSize = 15.sp)
                    Text(detalle, color = Colores.Secundario, fontSize = 13.sp)
                }
            }
        }
    }
}
