package cl.visenda.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.dominio.Estado

// Paleta del mockup: grises, azul solo para acciones y progreso, naranja para "en riesgo".
object Colores {
    val Azul = Color(0xFF1F4FD1)
    val AzulSuave = Color(0xFFE8EEFC)
    val AzulTexto = Color(0xFF1A44B8)
    val Fondo = Color(0xFFF3F4F6)
    val Tinta = Color(0xFF15171C)
    val Secundario = Color(0xFF4B5160)
    val Borde = Color(0xFFE4E6EB)
    val Separador = Color(0xFFEEF0F3)
    val Naranja = Color(0xFF9A4105)
    val NaranjaSuave = Color(0xFFFDF0E5)
}

private val esquema = lightColorScheme(
    primary = Colores.Azul, onPrimary = Color.White,
    primaryContainer = Colores.AzulSuave, onPrimaryContainer = Colores.AzulTexto,
    background = Colores.Fondo, onBackground = Colores.Tinta,
    surface = Color.White, onSurface = Colores.Tinta, onSurfaceVariant = Colores.Secundario,
    outline = Colores.Borde, outlineVariant = Colores.Separador,
)

@Composable
fun TemaVisenda(contenido: @Composable () -> Unit) = MaterialTheme(colorScheme = esquema, content = contenido)

@Composable
fun Tarjeta(modifier: Modifier = Modifier, relleno: Dp = 16.dp, contenido: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Colores.Borde)) {
        Column(Modifier.padding(relleno), verticalArrangement = Arrangement.spacedBy(10.dp), content = contenido)
    }
}

@Composable
fun TituloSeccion(texto: String, color: Color = Colores.Secundario) {
    Text(
        texto.uppercase(), color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
fun BarraProgreso(fraccion: Float, descripcion: String, modifier: Modifier = Modifier, alto: Dp = 6.dp) {
    Box(
        modifier.fillMaxWidth().height(alto).clip(CircleShape).background(Colores.Borde)
            .semantics { contentDescription = descripcion },
    ) {
        Box(Modifier.fillMaxWidth(fraccion.coerceIn(0f, 1f)).height(alto).clip(CircleShape).background(Colores.Azul))
    }
}

@Composable
fun Avatar(nombre: String, estado: Estado, tamano: Dp = 40.dp) {
    val (fondo, texto) = when (estado) {
        Estado.EN_RIESGO -> Colores.NaranjaSuave to Colores.Naranja
        Estado.COMPLETO, Estado.SIN_CICLO -> Colores.Fondo to Colores.Secundario
        Estado.PENDIENTE -> Colores.AzulSuave to Colores.AzulTexto
    }
    Box(Modifier.size(tamano).clip(CircleShape).background(fondo), contentAlignment = Alignment.Center) {
        Text(iniciales(nombre), color = texto, fontWeight = FontWeight.SemiBold, fontSize = (tamano.value * 0.36f).sp)
    }
}

/** "Dra. Valentina Rojas" → "VR" */
fun iniciales(nombre: String) = nombre.split(" ").filter { it.isNotBlank() && !it.endsWith(".") }
    .take(2).joinToString("") { it.first().uppercase() }

@Composable
fun Etiqueta(texto: String, estado: Estado) {
    val (fondo, color) = when (estado) {
        Estado.EN_RIESGO -> Colores.NaranjaSuave to Colores.Naranja
        Estado.PENDIENTE -> Colores.AzulSuave to Colores.AzulTexto
        else -> Colores.Fondo to Colores.Secundario
    }
    Text(
        texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(fondo).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
