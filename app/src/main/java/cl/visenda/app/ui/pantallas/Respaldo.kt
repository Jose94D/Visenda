package cl.visenda.app.ui.pantallas

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.respaldo.MINIMO_CONTRASENA
import cl.visenda.app.respaldo.Manifiesto
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.Pantalla
import cl.visenda.app.ui.Tarjeta
import cl.visenda.app.ui.TituloSeccion
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Ajustes > Respaldo cifrado (RN-13): crear un archivo .visenda o restaurar uno. */
@Composable
fun Respaldo(estado: AppEstado, nav: Navegador, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var clave by remember { mutableStateOf("") }
    var repetida by remember { mutableStateOf("") }
    var claveRestaurar by remember { mutableStateOf("") }
    var archivoRestaurar by remember { mutableStateOf<Uri?>(null) }
    var leido by remember { mutableStateOf<Pair<Manifiesto, ByteArray>?>(null) }
    var confirmar by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf<String?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var restaurado by remember { mutableStateOf<Manifiesto?>(null) }

    val crear = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            ocupado = "Cifrando y guardando…"; error = null; mensaje = null
            runCatching { estado.crearRespaldo(uri, clave.toCharArray()) }
                .onSuccess { mensaje = "Respaldo guardado. Anota la contraseña: sin ella no se puede recuperar."; clave = ""; repetida = "" }
                .onFailure { error = it.message ?: "No se pudo crear el respaldo." }
            ocupado = null
        }
    }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { archivoRestaurar = uri; leido = null; error = null; restaurado = null }
    }

    Column(modifier) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }, enabled = ocupado == null) { Icon(Iconos.Atras, "Volver") }
            Text("Respaldo cifrado", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Sirve para pasar tus datos a otro teléfono. El archivo va cifrado con tu contraseña: sin ella nadie puede leerlo, " +
                        "y si la olvidas no hay forma de recuperarlo. Los mapas no se incluyen; se vuelven a descargar.",
                    color = Colores.Secundario, fontSize = 13.sp,
                )
            }
            ocupado?.let {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(it, fontSize = 14.sp)
                    }
                }
            }
            error?.let { item { Text(it, color = Colores.Naranja, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) } }
            mensaje?.let { item { Text(it, color = Colores.AzulTexto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) } }

            item { TituloSeccion("Crear respaldo") }
            item {
                Tarjeta {
                    Clave("Contraseña (mínimo $MINIMO_CONTRASENA caracteres)", clave) { clave = it }
                    Clave("Repite la contraseña", repetida) { repetida = it }
                    val valida = clave.length >= MINIMO_CONTRASENA && clave == repetida
                    if (repetida.isNotEmpty() && clave != repetida) Text("Las contraseñas no coinciden.", color = Colores.Naranja, fontSize = 13.sp)
                    Button(
                        onClick = { crear.launch("visenda-${LocalDate.now()}.visenda") },
                        enabled = valida && ocupado == null, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Crear respaldo") }
                }
            }

            item { TituloSeccion("Restaurar respaldo") }
            item {
                Tarjeta {
                    Text("Reemplaza todos los datos de este teléfono por los del respaldo.", color = Colores.Secundario, fontSize = 13.sp)
                    OutlinedButton(onClick = { elegir.launch(arrayOf("*/*")) }, enabled = ocupado == null, shape = RoundedCornerShape(12.dp)) {
                        Text(if (archivoRestaurar == null) "Elegir archivo .visenda" else "Elegir otro archivo")
                    }
                    if (archivoRestaurar != null && leido == null) {
                        Clave("Contraseña del respaldo", claveRestaurar) { claveRestaurar = it }
                        Button(
                            onClick = {
                                scope.launch {
                                    ocupado = "Descifrando…"; error = null; mensaje = null
                                    runCatching { estado.leerRespaldo(archivoRestaurar!!, claveRestaurar.toCharArray()) }
                                        .onSuccess { leido = it; confirmar = true }
                                        .onFailure { error = it.message ?: "No se pudo abrir el respaldo." }
                                    ocupado = null
                                }
                            },
                            enabled = claveRestaurar.isNotEmpty() && ocupado == null, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { Text("Abrir respaldo") }
                    }
                }
            }
            restaurado?.let { m ->
                item {
                    Tarjeta {
                        Text("Datos restaurados: ${m.clientes} clientes.", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (m.regiones.isNotEmpty()) {
                            val nombres = estado.mapas.indiceGuardado()?.regiones?.associate { it.clave to it.nombre }.orEmpty()
                            Text("Tenías descargadas: ${m.regiones.joinToString(", ") { nombres[it] ?: it }}.", fontSize = 14.sp)
                            OutlinedButton(onClick = { nav.ir(Pantalla.Regiones) }, shape = RoundedCornerShape(12.dp)) { Text("Ir a mapas sin conexión") }
                        }
                    }
                }
            }
        }
    }

    val m = leido?.first
    if (confirmar && m != null) {
        val fecha = Instant.ofEpochMilli(m.fecha).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d 'de' MMMM yyyy, HH:mm", ES))
        AlertDialog(
            onDismissRequest = { confirmar = false; leido = null },
            title = { Text("¿Reemplazar todos los datos?") },
            text = { Text("El respaldo es del $fecha y tiene ${m.clientes} clientes. Los clientes, visitas y horarios actuales de este teléfono se borran.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = false
                    val base = leido!!.second
                    scope.launch {
                        ocupado = "Restaurando…"; error = null
                        runCatching { estado.restaurarRespaldo(base) }
                            .onSuccess { restaurado = m; claveRestaurar = ""; archivoRestaurar = null; estado.mapasCambiaron() }
                            .onFailure { error = it.message }
                        leido = null
                        ocupado = null
                    }
                }) { Text("Reemplazar", color = Colores.Naranja) }
            },
            dismissButton = { TextButton(onClick = { confirmar = false; leido = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Clave(etiqueta: String, valor: String, alCambiar: (String) -> Unit) {
    OutlinedTextField(
        valor, alCambiar, label = { Text(etiqueta) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
