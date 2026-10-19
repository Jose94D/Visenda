package cl.visenda.app.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Pantalla temporal del Hito 1 (prueba de concepto). Se elimina cuando existan las pantallas reales.

private const val CONCEPCION = """{"lat":-36.8270,"lon":-73.0503}"""
private const val CHILLAN = """{"lat":-36.6066,"lon":-72.1034}"""

// 15 puntos del Gran Concepción para medir la matriz que usará el optimizador.
private val PUNTOS = listOf(
    -36.8270 to -73.0503, -36.7920 to -73.0660, -36.8170 to -73.0390, -36.7249 to -73.1168,
    -36.8430 to -73.1090, -36.9250 to -73.0280, -36.7840 to -73.0960, -36.7400 to -72.9950,
    -36.6170 to -72.9570, -37.0300 to -73.1500, -37.0880 to -73.1570, -36.9760 to -72.9370,
    -36.8290 to -73.0360, -36.8030 to -73.0350, -36.8140 to -73.0230,
).joinToString(",", "[", "]") { (lat, lon) -> """{"lat":$lat,"lon":$lon}""" }

@Composable
fun Laboratorio(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dirTeselas = remember { File(context.getExternalFilesDir(null), "teselas") }
    var resultados by remember { mutableStateOf(listOf<String>()) }
    var ocupado by remember { mutableStateOf(false) }

    fun probar(nombre: String, consulta: (Valhalla) -> String) = scope.launch {
        ocupado = true
        val linea = withContext(Dispatchers.IO) {
            runCatching {
                val t0 = System.nanoTime()
                Valhalla(context, ValhallaConfigFactory.usingTilesDir(dirTeselas.absolutePath)).use { motor ->
                    val t1 = System.nanoTime()
                    val res = consulta(motor)
                    "$nombre: $res · motor ${(t1 - t0) / 1_000_000} ms · consulta ${(System.nanoTime() - t1) / 1_000_000} ms"
                }
            }.getOrElse { "$nombre: ERROR ${it.message}" }
        }
        resultados = resultados + linea
        ocupado = false
    }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Laboratorio · Hito 1", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Teselas: ${dirTeselas.walk().count { it.extension == "gph" }} archivos en ${dirTeselas.absolutePath}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            enabled = !ocupado,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                probar("Concepción → Chillán") { motor ->
                    val raw = motor.routeRaw("""{"locations":[$CONCEPCION,$CHILLAN],"costing":"auto","directions_type":"none"}""")
                    val resumen = JSONObject(raw).getJSONObject("trip").getJSONObject("summary")
                    "%.1f km, %.0f min".format(resumen.getDouble("length"), resumen.getDouble("time") / 60)
                }
            },
        ) { Text("Ruta Concepción → Chillán") }
        Button(
            enabled = !ocupado,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                probar("Matriz 15×15") { motor ->
                    val raw = motor.matrixRaw("""{"sources":$PUNTOS,"targets":$PUNTOS,"costing":"auto"}""")
                    val filas = JSONObject(raw).getJSONArray("sources_to_targets")
                    val celdas = (0 until filas.length()).map { filas.get(it) as JSONArray }
                    val conTiempo = celdas.sumOf { fila -> (0 until fila.length()).count { !fila.getJSONObject(it).isNull("time") } }
                    "$conTiempo de ${celdas.sumOf { it.length() }} tiempos calculados"
                }
            },
        ) { Text("Matriz de tiempos 15 × 15") }
        resultados.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
