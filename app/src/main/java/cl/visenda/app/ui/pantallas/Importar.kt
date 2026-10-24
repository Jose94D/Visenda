package cl.visenda.app.ui.pantallas

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.data.Empresa
import cl.visenda.app.importar.Cambios
import cl.visenda.app.importar.Rol
import cl.visenda.app.importar.normalizar
import cl.visenda.app.importar.prepararImportacion
import cl.visenda.app.importar.proponerMapeo
import cl.visenda.app.importar.sinEmpresas
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.ProgresoConLogo
import cl.visenda.app.ui.Pantalla
import cl.visenda.app.ui.Tarjeta
import cl.visenda.app.ui.TituloSeccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TIPOS_XLSX = arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")

/** Pantalla 5.6: elegir archivo y empresa, asignar columnas, revisar e importar. */
@Composable
fun Importar(estado: AppEstado, nav: Navegador, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var paso by remember { mutableIntStateOf(1) }
    var archivo by remember { mutableStateOf<String?>(null) }
    var filas by remember { mutableStateOf<List<List<String>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var empresaId by remember { mutableStateOf<Long?>(null) } // null = empresa nueva
    var nombreNueva by remember { mutableStateOf("") }
    var quitarDemo by remember { mutableStateOf(estado.idsDemo.isNotEmpty()) }
    var roles by remember { mutableStateOf(listOf<Rol>()) }
    var cambios by remember { mutableStateOf<Cambios?>(null) }
    var quitarAusentes by remember { mutableStateOf(false) }
    var autorizo by remember { mutableStateOf(false) }
    var importando by remember { mutableStateOf(false) }
    var sinUbicar by remember { mutableStateOf<Int?>(null) }

    val empresas = estado.datos.empresas.filter { !quitarDemo || it.id !in estado.idsDemo }
    val repetida = nombreNueva.isNotBlank() && estado.datos.empresas.any { normalizar(it.nombre) == normalizar(nombreNueva) }

    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            runCatching { estado.leerKardex(uri) }
                .onSuccess {
                    if (it.size < 2) error = "El archivo no tiene filas de clientes."
                    else {
                        filas = it
                        archivo = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Kardex"
                    }
                }
                .onFailure { error = "No se pudo leer el archivo: ${it.message ?: "no es un .xlsx válido"}" }
        }
    }

    BackHandler(enabled = paso in 2..3 && !importando) { paso-- }

    Column(modifier) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (paso in 2..3) paso-- else nav.volver() }, enabled = !importando) { Icon(Iconos.Atras, "Volver") }
            Column {
                Text("Importar kardex", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(if (paso <= 3) "Paso $paso de 3" else "Listo", color = Colores.Secundario, fontSize = 13.sp)
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let { item { Text(it, color = Colores.Naranja, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) } }
            if (importando) item {
                ProgresoConLogo(estado.progresoImportacion ?: "Guardando el kardex…", estado.fraccionImportacion)
            } else when (paso) {
                1 -> {
                    item { TituloSeccion("Archivo") }
                    item {
                        Tarjeta {
                            Text(archivo?.let { "$it · ${filas!!.size - 1} filas" } ?: "Elige el kardex en formato .xlsx", fontSize = 15.sp)
                            OutlinedButton(onClick = { elegir.launch(TIPOS_XLSX) }, shape = RoundedCornerShape(12.dp)) {
                                Text(if (archivo == null) "Elegir archivo" else "Elegir otro")
                            }
                        }
                    }
                    item { TextButton(onClick = { nav.ir(Pantalla.GuiaKardex) }) { Text("¿Cómo preparar el kardex?") } }
                    item { TituloSeccion("¿De qué empresa es?") }
                    item {
                        Tarjeta(relleno = 0.dp) {
                            Column {
                                empresas.forEach { e ->
                                    Opcion(e.nombre, empresaId == e.id) { empresaId = e.id }
                                    HorizontalDivider(color = Colores.Separador)
                                }
                                Opcion("Empresa nueva", empresaId == null) { empresaId = null }
                                if (empresaId == null) {
                                    OutlinedTextField(
                                        nombreNueva, { nombreNueva = it }, label = { Text("Nombre de la empresa") }, singleLine = true,
                                        isError = repetida, supportingText = if (repetida) ({ Text("Ya existe: elígela en la lista.") }) else null,
                                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (estado.idsDemo.isNotEmpty()) item {
                        Casilla("Quitar los datos de ejemplo (clientes ficticios del prototipo)", quitarDemo) {
                            quitarDemo = it
                            if (it && empresaId in estado.idsDemo) empresaId = null
                        }
                    }
                }
                2 -> {
                    item { Text("Indica qué contiene cada columna. Las de \"Campo del perfil\" se muestran en la ficha del cliente.", color = Colores.Secundario, fontSize = 14.sp) }
                    if (Rol.NOMBRE !in roles) item { Text("Falta indicar la columna con el nombre del cliente.", color = Colores.Naranja, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    itemsIndexed(filas!!.first()) { i, encabezado ->
                        val ejemplo = filas!!.drop(1).firstNotNullOfOrNull { it.getOrNull(i)?.trim()?.ifEmpty { null } }
                        Columna(encabezado.ifBlank { "Columna ${i + 1}" }, ejemplo, roles[i]) { nuevo ->
                            roles = roles.mapIndexed { j, r -> if (j == i) nuevo else if (r == nuevo && nuevo != Rol.CAMPO && nuevo != Rol.NO_IMPORTAR) Rol.CAMPO else r }
                        }
                    }
                }
                3 -> cambios?.resumen?.let { r ->
                    item { TituloSeccion("Resumen") }
                    item {
                        Tarjeta {
                            Linea("${r.nuevos}", "clientes nuevos")
                            Linea("${r.actualizados}", "clientes actualizados")
                            Linea("${r.sinCambios}", "sin cambios")
                            Linea("${r.lugaresNuevos}", if (r.lugaresNuevos == 1) "dirección nueva, se ubica en el mapa al importar" else "direcciones nuevas, se ubican en el mapa al importar")
                            if (r.filasSinNombre > 0) Linea("${r.filasSinNombre}", "filas sin nombre, se omiten")
                        }
                    }
                    if (r.porRevisar.isNotEmpty()) {
                        item { TituloSeccion("Horarios por revisar (${r.porRevisar.size})", Colores.Naranja) }
                        item { Lista(r.porRevisar, "No entran a la planificación hasta corregirlos en el perfil del cliente.") }
                    }
                    if (r.posiblesDuplicados.isNotEmpty()) {
                        item { TituloSeccion("Posibles duplicados (${r.posiblesDuplicados.size})", Colores.Naranja) }
                        item { Lista(r.posiblesDuplicados, "Clientes nuevos con el mismo nombre que uno existente, pero otro RUT o código.") }
                    }
                    if (r.yaNoEstan.isNotEmpty()) {
                        item { TituloSeccion("Ya no están en el kardex (${r.yaNoEstan.size})") }
                        item { Lista(r.yaNoEstan.map { it.nombre }, null) }
                        item { Casilla("Quitarlos de la cartera de esta empresa", quitarAusentes) { quitarAusentes = it } }
                    }
                    item {
                        Casilla(
                            "Confirmo que estoy autorizado a tratar estos datos personales. Se guardan solo en este teléfono; " +
                                "para ubicarlas en el mapa, solo las direcciones se consultan al servicio de ubicación de Android.",
                            autorizo,
                        ) { autorizo = it }
                    }
                }
                else -> item {
                    Tarjeta {
                        Icon(Iconos.Listo, null, tint = Colores.Azul, modifier = Modifier.size(32.dp))
                        Text("Kardex importado", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        cambios?.resumen?.let { Text("${it.nuevos} nuevos · ${it.actualizados} actualizados · ${it.sinCambios} sin cambios", color = Colores.Secundario, fontSize = 14.sp) }
                        if ((sinUbicar ?: 0) > 0) {
                            Text(
                                "$sinUbicar ${if (sinUbicar == 1) "dirección quedó" else "direcciones quedaron"} sin ubicar. Esos clientes no entran a las rutas hasta ubicarlos en el mapa.",
                                color = Colores.Naranja, fontSize = 14.sp,
                            )
                            OutlinedButton(onClick = { nav.ir(Pantalla.SinUbicar) }, shape = RoundedCornerShape(12.dp)) { Text("Ubicarlas ahora") }
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
            Box(Modifier.weight(1f)) {

            }
            Button(
                onClick = {
                    error = null
                    when (paso) {
                        1 -> scope.launch {
                            roles = proponerMapeo(filas!!.first(), empresaId?.let { estado.mapeoGuardado(it) }.orEmpty())
                            paso = 2
                        }
                        2 -> scope.launch {
                            val base = if (quitarDemo) estado.datos.sinEmpresas(estado.idsDemo.toSet()) else estado.datos
                            val empresa = base.empresas.firstOrNull { it.id == empresaId } ?: Empresa(0, nombreNueva.trim())
                            runCatching { withContext(Dispatchers.Default) { prepararImportacion(base, filas!!, roles, empresa, estado.hoy) } }
                                .onSuccess { cambios = it; autorizo = false; quitarAusentes = false; paso = 3 }
                                .onFailure { error = it.message }
                        }
                        3 -> {
                            val c = cambios!!
                            importando = true
                            estado.importar(
                                c, if (quitarDemo) estado.idsDemo else emptyList(),
                                alTerminar = { n ->
                                    if (quitarAusentes) estado.quitarDeCartera(c.empresa.id, c.resumen.yaNoEstan.map { it.id })
                                    sinUbicar = n; importando = false; paso = 4
                                },
                                alFallar = { error = it; importando = false },
                            )
                        }
                        else -> nav.pestana(Pantalla.Inicio)
                    }
                },
                enabled = !importando && when (paso) {
                    1 -> filas != null && (empresaId != null || (nombreNueva.isNotBlank() && !repetida))
                    2 -> Rol.NOMBRE in roles
                    3 -> autorizo
                    else -> true
                },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.height(52.dp),
            ) {
                Text(when (paso) { 3 -> "Importar"; 4 -> "Listo"; else -> "Siguiente" }, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Opcion(texto: String, elegida: Boolean, alElegir: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = alElegir).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = elegida, onClick = null, modifier = Modifier.padding(8.dp))
        Text(texto, fontSize = 15.sp)
    }
}

@Composable
private fun Casilla(texto: String, marcada: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { alCambiar(!marcada) }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = marcada, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Colores.Azul), modifier = Modifier.padding(top = 2.dp))
        Text(texto, fontSize = 14.sp)
    }
}

@Composable
private fun Columna(encabezado: String, ejemplo: String?, rol: Rol, alCambiar: (Rol) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Tarjeta(Modifier.clickable { abierto = true }, relleno = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(encabezado, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(ejemplo ?: "(vacía)", color = Colores.Secundario, fontSize = 13.sp, maxLines = 1)
            }
            Box {
                Text(rol.texto, color = if (rol == Rol.NO_IMPORTAR) Colores.Secundario else Colores.AzulTexto, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                    Rol.entries.forEach { r ->
                        DropdownMenuItem(text = { Text(r.texto) }, onClick = { alCambiar(r); abierto = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun Linea(numero: String, texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(numero, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(texto, fontSize = 15.sp)
    }
}

@Composable
private fun Lista(filas: List<String>, nota: String?) {
    Tarjeta {
        nota?.let { Text(it, color = Colores.Secundario, fontSize = 13.sp) }
        filas.take(20).forEach { Text(it, fontSize = 14.sp) }
        if (filas.size > 20) Text("y ${filas.size - 20} más", color = Colores.Secundario, fontSize = 13.sp)
    }
}
