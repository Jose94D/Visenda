package cl.visenda.app.ui.pantallas

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.data.Categoria
import cl.visenda.app.data.Ciclo
import cl.visenda.app.data.Empresa
import cl.visenda.app.dominio.FRECUENCIAS
import cl.visenda.app.dominio.cicloSiguiente
import cl.visenda.app.dominio.validarEmpresa
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.AvisarFocoDeDialogo
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.Tarjeta
import cl.visenda.app.ui.TituloSeccion
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val DIA_MS = 86_400_000L

/** Ajustes > Empresas y ciclos (5.8): empresaId 0 = empresa nueva. */
@Composable
fun EditarEmpresa(estado: AppEstado, nav: Navegador, empresaId: Long, modifier: Modifier) {
    val d = estado.datos
    val original = d.empresas.firstOrNull { it.id == empresaId }
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(original?.nombre ?: "") }
    var frecuencia by remember { mutableIntStateOf(original?.frecuenciaDefecto ?: 1) }
    val ciclos = remember { mutableStateListOf<Ciclo>().apply { addAll(d.ciclos.filter { it.empresaId == empresaId }.sortedBy { it.inicio }) } }
    val categorias = remember { mutableStateListOf<Categoria>().apply { addAll(d.categorias.filter { it.empresaId == empresaId }) } }
    var fecha by remember { mutableStateOf<Pair<Int, Boolean>?>(null) } // (ciclo, ¿inicio?) cuyo selector de fecha está abierto
    var eliminar by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var intento by remember { mutableStateOf(false) }

    val errores = validarEmpresa(nombre, d.empresas.filter { it.id != empresaId }.map { it.nombre }, frecuencia, ciclos, categorias)
    val corto = DateTimeFormatter.ofPattern("d MMM yyyy", ES)
    // Clientes que se borran con la empresa: los que no están en ninguna otra cartera (RN-12).
    val seBorran = d.cartera.filter { it.empresaId == empresaId }.count { c -> d.cartera.none { it.clienteId == c.clienteId && it.empresaId != empresaId } }

    Column(modifier) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }) { Icon(Iconos.Atras, "Volver") }
            Text(original?.nombre ?: "Nueva empresa", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Una empresa es el laboratorio o compañía para la que visitas. Cada una tiene sus propios ciclos y metas de visitas.", color = Colores.Secundario, fontSize = 13.sp)
            }
            item {
                Tarjeta {
                    OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre de la empresa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Fila("Visitas por ciclo (por defecto)", "Para clientes sin categoría ni un número propio en su perfil", frecuencia) { frecuencia = it }
                }
            }

            item { TituloSeccion("Ciclos") }
            item { Text("Un ciclo es el período en que debes cumplir tus visitas, normalmente un mes. Al empezar uno nuevo, el conteo parte de cero.", color = Colores.Secundario, fontSize = 13.sp) }
            if (ciclos.isEmpty()) item { Text("Sin un ciclo vigente, Visenda no puede saber qué visitas te faltan ni avisarte de clientes en riesgo.", color = Colores.Naranja, fontSize = 13.sp) }
            itemsIndexed(ciclos) { i, c ->
                Tarjeta(relleno = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(c.nombre, { ciclos[i] = c.copy(nombre = it) }, label = { Text("Nombre del ciclo") }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(onClick = { ciclos.removeAt(i) }) { Text("Quitar", color = Colores.Naranja) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { fecha = i to true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Text("Desde ${LocalDate.ofEpochDay(c.inicio).format(corto)}", fontSize = 13.sp)
                        }
                        OutlinedButton(onClick = { fecha = i to false }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Text("Hasta ${LocalDate.ofEpochDay(c.fin).format(corto)}", fontSize = 13.sp)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { ciclos += cicloSiguiente(ciclos, empresaId, estado.hoy) }, shape = RoundedCornerShape(12.dp)) {
                    Text(if (ciclos.isEmpty()) "Agregar ciclo de este mes" else "Agregar el ciclo siguiente")
                }
            }

            item { TituloSeccion("Categorías") }
            item {
                Text(
                    "Las categorías agrupan a tus clientes según cuántas veces debes visitarlos en cada ciclo. Por ejemplo: A = 2 visitas, B = 1 visita. " +
                        "Cada cliente toma su categoría de la columna \"Categoría\" del kardex.",
                    color = Colores.Secundario, fontSize = 13.sp,
                )
            }
            if (categorias.isNotEmpty()) item {
                Tarjeta(relleno = 0.dp) {
                    Column {
                        categorias.forEachIndexed { i, c ->
                            if (i > 0) HorizontalDivider(color = Colores.Separador)
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(c.nombre, { categorias[i] = c.copy(nombre = it) }, label = { Text("Categoría") }, singleLine = true, modifier = Modifier.width(110.dp))
                                Contador(c.frecuencia, Modifier.weight(1f)) { categorias[i] = c.copy(frecuencia = it) }
                                TextButton(onClick = { categorias.removeAt(i) }) { Text("Quitar", color = Colores.Naranja) }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { categorias += Categoria(0, empresaId, "", frecuencia) }, shape = RoundedCornerShape(12.dp)) { Text("Agregar categoría") }
            }
            item {
                Text(
                    "Si a un médico le corresponde un número distinto, cámbialo en su perfil: eso manda sobre la categoría. " +
                        "Si quitas una categoría, sus clientes pasan a las visitas por defecto.",
                    color = Colores.Secundario, fontSize = 13.sp,
                )
            }

            if (original != null) item {
                OutlinedButton(onClick = { eliminar = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Text("Eliminar empresa", color = Colores.Naranja)
                }
            }
        }
        HorizontalDivider(color = Colores.Borde)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (intento) errores.forEach { Text(it, color = Colores.Naranja, fontSize = 13.sp) }
            Button(
                onClick = {
                    intento = true
                    if (errores.isEmpty()) scope.launch {
                        guardando = true
                        estado.guardarEmpresa(Empresa(empresaId, nombre.trim(), frecuencia), ciclos.map { it.copy(nombre = it.nombre.trim()) }, categorias.map { it.copy(nombre = it.nombre.trim()) })
                        nav.volver()
                    }
                },
                enabled = !guardando, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Guardar", fontWeight = FontWeight.SemiBold) }
        }
    }

    fecha?.let { (i, esInicio) -> SelectorFecha(if (esInicio) ciclos[i].inicio else ciclos[i].fin, { fecha = null }) { dia ->
        ciclos[i] = if (esInicio) ciclos[i].copy(inicio = dia) else ciclos[i].copy(fin = dia)
        fecha = null
    } }

    if (eliminar && original != null) {
        var confirmacion by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { eliminar = false },
            title = { AvisarFocoDeDialogo(); Text("¿Eliminar ${original.nombre}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se borran sus ciclos, categorías y cartera. $seBorran ${if (seBorran == 1) "cliente queda" else "clientes quedan"} sin ninguna empresa y se borran con sus visitas. No se puede deshacer.")
                    Text("Escribe el nombre de la empresa para confirmar:", fontSize = 13.sp)
                    OutlinedTextField(confirmacion, { confirmacion = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { eliminar = false; scope.launch { estado.eliminarEmpresa(original.id); nav.volver() } },
                    enabled = confirmacion.trim() == original.nombre,
                ) { Text("Eliminar", color = Colores.Naranja) }
            },
            dismissButton = { TextButton(onClick = { eliminar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Fila(titulo: String, detalle: String, valor: Int, alCambiar: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, fontSize = 15.sp)
            Text(detalle, color = Colores.Secundario, fontSize = 13.sp)
        }
        Contador(valor, Modifier, alCambiar)
    }
}

@Composable
fun Contador(valor: Int, modifier: Modifier, alCambiar: (Int) -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { alCambiar(valor - 1) }, enabled = valor > FRECUENCIAS.first, modifier = Modifier.size(48.dp).semantics { contentDescription = "Una visita menos" }) { Text("−", fontSize = 20.sp) }
        Text("$valor", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
        TextButton(onClick = { alCambiar(valor + 1) }, enabled = valor < FRECUENCIAS.last, modifier = Modifier.size(48.dp).semantics { contentDescription = "Una visita más" }) { Text("+", fontSize = 20.sp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorFecha(dia: Long, alCerrar: () -> Unit, alElegir: (Long) -> Unit) {
    // El selector trabaja en milisegundos UTC; los ciclos, en días desde epoch.
    val estado = rememberDatePickerState(initialSelectedDateMillis = dia * DIA_MS)
    DatePickerDialog(
        onDismissRequest = alCerrar,
        confirmButton = { TextButton(onClick = { estado.selectedDateMillis?.let { alElegir(it / DIA_MS) } ?: alCerrar() }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    ) { AvisarFocoDeDialogo(); DatePicker(estado) }
}
