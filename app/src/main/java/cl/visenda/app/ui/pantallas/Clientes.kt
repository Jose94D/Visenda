package cl.visenda.app.ui.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.data.Cliente
import cl.visenda.app.dominio.Estado
import cl.visenda.app.dominio.estadosDe
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.Avatar
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.Pantalla
import cl.visenda.app.ui.Tarjeta
import cl.visenda.app.ui.TituloSeccion
import java.text.Normalizer

private val GRUPOS = listOf(
    Estado.EN_RIESGO to "En riesgo", Estado.PENDIENTE to "Pendientes", Estado.COMPLETO to "Listos este ciclo", Estado.SIN_CICLO to "Sin ciclo vigente",
)

fun sinTildes(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

@Composable
fun Clientes(estado: AppEstado, nav: Navegador, filtroInicial: Estado?, modifier: Modifier) {
    val d = estado.datos
    var busqueda by rememberSaveable { mutableStateOf("") }
    var filtro by rememberSaveable { mutableStateOf(filtroInicial) }
    var empresaId by rememberSaveable { mutableStateOf<Long?>(null) }

    data class Fila(val cliente: Cliente, val estado: Estado, val hechas: Int, val requeridas: Int, val comuna: String)
    // Con una empresa elegida, el estado y el avance son solo los de esa empresa.
    val filas = d.clientes.mapNotNull { c ->
        val estados = d.estadosDe(c.id, estado.hoy).filter { empresaId == null || it.empresa.id == empresaId }
        if (empresaId != null && estados.isEmpty()) return@mapNotNull null
        val lugar = d.atenciones.firstOrNull { it.clienteId == c.id }?.let { a -> d.lugares.firstOrNull { it.id == a.lugarId } }
        Fila(c, estados.minOfOrNull { it.estado } ?: Estado.SIN_CICLO, estados.sumOf { it.hechas }, estados.sumOf { it.requeridas },
            lugar?.direccion?.substringAfterLast(',')?.trim().orEmpty())
    }
    val q = sinTildes(busqueda.trim())
    val visibles = filas.filter { f ->
        (filtro == null || f.estado == filtro) &&
            (q.isEmpty() || listOf(f.cliente.nombre, f.cliente.especialidad.orEmpty(), f.comuna).any { q in sinTildes(it) })
    }

    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Clientes", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text("${filas.size}", fontSize = 17.sp, color = Colores.Secundario, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        item {
            OutlinedTextField(
                value = busqueda, onValueChange = { busqueda = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nombre, especialidad o comuna") }, label = { Text("Buscar clientes") },
                leadingIcon = { Icon(Iconos.Buscar, null) }, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, unfocusedBorderColor = Colores.Borde),
            )
        }
        if (d.empresas.size > 1) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf<Pair<Long?, String>>(null to "Todas las empresas") + d.empresas.map { it.id to it.nombre }).forEach { (id, nombre) ->
                    FilterChip(
                        selected = empresaId == id, onClick = { empresaId = id }, label = { Text(nombre) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Colores.Azul, selectedLabelColor = Color.White, containerColor = Color.White),
                    )
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf<Pair<Estado?, String>>(null to "Todos") + GRUPOS.take(3)).forEach { (e, texto) ->
                    val n = if (e == null) filas.size else filas.count { it.estado == e }
                    FilterChip(
                        selected = filtro == e, onClick = { filtro = e }, label = { Text("${texto.substringBefore(" este")} $n") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Colores.Tinta, selectedLabelColor = Color.White, containerColor = Color.White),
                    )
                }
            }
        }
        GRUPOS.forEach { (e, titulo) ->
            val grupo = visibles.filter { it.estado == e }
            if (grupo.isEmpty()) return@forEach
            item { TituloSeccion(titulo, if (e == Estado.EN_RIESGO) Colores.Naranja else Colores.Secundario) }
            item {
                Tarjeta(relleno = 0.dp) {
                    Column {
                        grupo.forEachIndexed { i, f ->
                            if (i > 0) HorizontalDivider(color = Colores.Separador)
                            Row(
                                Modifier.fillMaxWidth().clickable { nav.ir(Pantalla.Perfil(f.cliente.id)) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Avatar(f.cliente.nombre, f.estado)
                                Column(Modifier.weight(1f)) {
                                    Text(f.cliente.nombre, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(listOfNotNull(f.cliente.especialidad, f.comuna.ifEmpty { null }).joinToString(" · "), color = Colores.Secundario, fontSize = 13.sp)
                                }
                                Text(
                                    "${f.hechas}/${f.requeridas}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                    color = if (f.estado == Estado.EN_RIESGO) Colores.Naranja else Colores.Tinta,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (visibles.isEmpty()) item { Text("No hay clientes que coincidan.", color = Colores.Secundario, modifier = Modifier.padding(top = 24.dp)) }
    }
}
