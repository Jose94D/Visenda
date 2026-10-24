package cl.visenda.app.ui.pantallas

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.importar.PLANTILLA_KARDEX
import cl.visenda.app.importar.escribirXlsx
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.Tarjeta
import cl.visenda.app.ui.TituloSeccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

// Colores de una planilla: encabezados grises como en Excel, columnas destacadas en azul y errores en naranjo.
private val GrisPlanilla = Color(0xFFF1F3F4)
private val BordePlanilla = Color(0xFFD5D8DC)

/** Guía para preparar el kardex: qué es, cómo debe verse la planilla, ejemplos bien y mal, y la plantilla para descargar. */
@Composable
fun GuiaKardex(nav: Navegador, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var aviso by remember { mutableStateOf<String?>(null) }
    // El usuario elige dónde guardar la plantilla (Descargas, Drive…): la app no necesita permisos de almacenamiento.
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_XLSX)) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            aviso = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { escribirXlsx(PLANTILLA_KARDEX, it) } }
            }.fold({ "Plantilla guardada. Ábrela en Excel o Google Sheets y reemplaza las filas de ejemplo por tus clientes." }, { "No se pudo guardar: ${it.message}" })
        }
    }

    Column(modifier) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }) { Icon(Iconos.Atras, "Volver") }
            Text("Cómo preparar el kardex", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { TituloSeccion("¿Qué es el kardex?") }
            item {
                Parrafo(
                    "Es la planilla con tus clientes que te entrega cada empresa: nombre, dirección, horario de atención, categoría y otros datos. " +
                        "Visenda la lee para saber a quién visitar, dónde y cuándo.",
                )
            }

            item { TituloSeccion("Así debe verse tu planilla") }
            item { Parrafo("Una fila por consulta y los títulos de las columnas en la primera fila. Las columnas en azul son las más importantes.") }
            item { Planilla(PLANTILLA_KARDEX, destacadas = setOf(1, 6, 7, 8, 4)) }
            item { Text("Desliza la tabla hacia el lado para ver todas las columnas.", color = Colores.Secundario, fontSize = 13.sp) }
            item {
                Tarjeta {
                    Text("Columnas", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Punto("Indispensables: nombre y dirección (con comuna). Sin dirección no hay rutas.")
                    Punto("Muy recomendadas: horario y categoría. Sin horario Visenda no sabe cuándo atiende; sin categoría usa las visitas por defecto de la empresa.")
                    Punto("Opcionales: RUT o código, especialidad, nombre del lugar y otros datos, como teléfono o cumpleaños, que se muestran en la ficha del cliente.")
                    Punto("Los títulos no tienen que ser exactos: al importar puedes indicar qué contiene cada columna.")
                }
            }

            item { TituloSeccion("Horarios") }
            item { Parrafo("Escribe el día y el horario. Visenda entiende estas formas:") }
            item {
                Ejemplo(bien = true) {
                    Planilla(listOf(listOf("Horario"), listOf("Lun-Vie 9:00-13:00"), listOf("L a V 15 a 19"), listOf("Lun, Mié 9-13 / 15-19"), listOf("Martes 08:30 - 12:30")), destacadas = setOf(0))
                }
            }
            item {
                Ejemplo(bien = false) {
                    Planilla(listOf(listOf("Horario"), listOf("Mañanas"), listOf("Previa hora"), listOf("Consultar")), malas = setOf(1 to 0, 2 to 0, 3 to 0))
                }
            }
            item { Parrafo("Los horarios que no entiende quedan \"por revisar\": el cliente se importa igual, pero no entra a las rutas hasta corregirlo.") }

            item { TituloSeccion("Médicos con varias consultas") }
            item { Parrafo("Usa una fila por consulta, con el mismo nombre (o el mismo RUT). Visenda las junta en un solo cliente con sus dos horarios.") }
            item {
                Ejemplo(bien = true) {
                    Planilla(
                        listOf(
                            listOf("Nombre", "Dirección", "Horario"),
                            listOf("Dra. Ana Muñoz", "Calle Ejemplo 100", "Lun-Vie 9-13"),
                            listOf("Dra. Ana Muñoz", "Calle Ejemplo 250", "Lun, Mié 15-19"),
                        ),
                    )
                }
            }
            item {
                Ejemplo(bien = false) {
                    Planilla(
                        listOf(listOf("Nombre", "Dirección", "Horario"), listOf("Dra. Ana Muñoz", "Calle Ejemplo 100 y Calle Ejemplo 250", "Lun-Vie 9-13 y 15-19")),
                        malas = setOf(1 to 1, 1 to 2),
                    )
                }
            }

            item { TituloSeccion("Errores comunes") }
            item {
                Ejemplo(bien = false) {
                    Planilla(listOf(listOf("KARDEX OCTUBRE 2026", "", ""), listOf("Nombre", "Dirección", "Horario"), listOf("Dra. Ana Muñoz", "Calle Ejemplo 100", "Lun-Vie 9-13")), malas = setOf(0 to 0))
                }
            }
            item {
                Tarjeta {
                    Punto("Títulos, logos o filas vacías sobre la tabla: borra todo lo que esté arriba de los títulos de las columnas.")
                    Punto("Celdas combinadas: sepáralas y repite el dato en cada fila.")
                    Punto("Archivo .xls, .csv o Google Sheets: guárdalo como .xlsx (en Excel: Guardar como → Libro de Excel; en Google Sheets: Archivo → Descargar → Microsoft Excel).")
                    Punto("Varias hojas: Visenda lee solo la primera.")
                }
            }

            item { TituloSeccion("Importar en Visenda") }
            item {
                Tarjeta {
                    Punto("1. En Ajustes, toca Importar kardex, elige el archivo y la empresa a la que pertenece.")
                    Punto("2. Revisa qué contiene cada columna. Visenda lo propone solo; corrige lo que no calce.")
                    Punto("3. Revisa el resumen y toca Importar. Las direcciones se ubican en el mapa; las que no encuentre, las puedes ubicar a mano.")
                    Punto("Puedes volver a importar el kardex actualizado cuando quieras: no duplica clientes ni borra visitas.")
                }
            }
            item { Parrafo("El archivo no sale de tu teléfono. Solo se consultan las direcciones para ubicarlas en el mapa, nunca nombres ni RUT.") }

            item { TituloSeccion("Plantilla de ejemplo") }
            item {
                Tarjeta {
                    Parrafo("Una planilla con las columnas que Visenda reconoce sola y filas de ejemplo. Reemplaza los ejemplos por tus clientes.")
                    Button(onClick = { guardar.launch("plantilla-kardex-visenda.xlsx") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Descargar plantilla de ejemplo", fontWeight = FontWeight.SemiBold)
                    }
                    aviso?.let { Text(it, color = Colores.Secundario, fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun Parrafo(texto: String) = Text(texto, fontSize = 14.sp, color = Colores.Tinta)

@Composable
private fun Punto(texto: String) = Text(if (texto.first().isDigit()) texto else "• $texto", fontSize = 14.sp) // los pasos numerados no llevan viñeta

/** Encabezado "Así sí" / "Así no" sobre un ejemplo. */
@Composable
private fun Ejemplo(bien: Boolean, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(20.dp).background(if (bien) Colores.Azul else Colores.Naranja, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(if (bien) "✓" else "✕", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(if (bien) "Así sí" else "Así no", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (bien) Colores.AzulTexto else Colores.Naranja)
        }
        contenido()
    }
}

/**
 * Una planilla dibujada como en Excel: letras de columna, números de fila y celdas.
 * destacadas: columnas en azul. malas: celdas (fila, columna) en naranjo. Se desplaza a lo ancho si no cabe.
 */
@Composable
private fun Planilla(filas: List<List<String>>, destacadas: Set<Int> = emptySet(), malas: Set<Pair<Int, Int>> = emptySet()) {
    val columnas = filas.maxOf { it.size }
    // Hasta 3 columnas ocupan todo el ancho; más que eso se desplazan a lo ancho como en Excel.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val ancho = if (columnas <= 3) (maxWidth - 30.dp) / columnas else 120.dp
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).border(1.dp, BordePlanilla, RoundedCornerShape(6.dp))) {
        Column {
            Celda("", 28.dp, GrisPlanilla, Colores.Secundario)
            filas.indices.forEach { Celda("${it + 1}", 28.dp, GrisPlanilla, Colores.Secundario) }
        }
        (0 until columnas).forEach { c ->
            Column {
                Celda(('A' + c).toString(), ancho, GrisPlanilla, Colores.Secundario)
                filas.forEachIndexed { f, fila ->
                    val fondo = when {
                        f to c in malas -> Colores.NaranjaSuave
                        c in destacadas -> Colores.AzulSuave
                        else -> Color.White
                    }
                    Celda(fila.getOrElse(c) { "" }, ancho, fondo, if (f to c in malas) Colores.Naranja else Colores.Tinta, negrita = f == 0)
                }
            }
        }
    }
    }
}

@Composable
private fun Celda(texto: String, ancho: androidx.compose.ui.unit.Dp, fondo: Color, color: Color, negrita: Boolean = false) {
    Box(Modifier.width(ancho).height(30.dp).background(fondo).border(0.5.dp, BordePlanilla).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
        Text(texto, fontSize = 12.sp, color = color, fontWeight = if (negrita) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
