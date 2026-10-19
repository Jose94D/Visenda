package cl.visenda.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.lightColorScheme
import cl.visenda.app.lab.Laboratorio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// Paleta del mockup: grises con azul solo para acciones y progreso.
private val VisendaColors = lightColorScheme(
    primary = Color(0xFF1F4FD1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EEFC),
    onPrimaryContainer = Color(0xFF1A44B8),
    background = Color(0xFFF3F4F6),
    onBackground = Color(0xFF15171C),
    surface = Color.White,
    onSurface = Color(0xFF15171C),
    onSurfaceVariant = Color(0xFF4B5160),
    outline = Color(0xFFE4E6EB),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VisendaApp() }
    }
}

@Composable
fun VisendaApp() {
    MaterialTheme(colorScheme = VisendaColors) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Laboratorio(Modifier.fillMaxSize().padding(padding).padding(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VisendaAppPreview() = VisendaApp()
