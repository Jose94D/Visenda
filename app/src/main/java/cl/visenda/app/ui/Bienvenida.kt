package cl.visenda.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import cl.visenda.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Colores del banner (docs/banner.webp).
private val Marino = Color(0xFF1B3260)
private val Celeste = Color(0xFFE4EBF5)
// Fondo del banner; el mismo va en res/values-v31/themes.xml y res/values/colors.xml para que el arranque de Android empalme.
private val FONDO_BANNER = Color(0xFFFEFEFE)

// "Visenda" y "App Android para visitadores en terreno", recortados del banner (2000 × 627) para usar su misma tipografía.
// Por separado y ajustados: un solo rectángulo alcanzaba el borde de la onda de arriba a la derecha.
private val RECORTE_NOMBRE = IntOffset(735, 180) to IntSize(875, 192)
private val RECORTE_FRASE = IntOffset(735, 385) to IntSize(990, 70)

/**
 * Bienvenida mientras se cargan los datos: ondas, el pin cae con un rebote y aparecen los textos del banner.
 * Dura unos 2,5 s aunque los datos estén antes, para que se aprecie; si tardan más, muestra una barra de carga.
 */
@Composable
fun Bienvenida(cargado: Boolean, alTerminar: () -> Unit) {
    val ondas = remember { Animatable(0f) }
    val pin = remember { Animatable(0f) }
    val texto = remember { Animatable(0f) }
    val salida = remember { Animatable(1f) }
    var animado by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch { ondas.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
        delay(250)
        pin.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 110f))
        texto.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        delay(1000) // el logo completo queda a la vista un momento
        animado = true
    }
    LaunchedEffect(animado, cargado) {
        if (animado && cargado) {
            salida.animateTo(0f, tween(350))
            alTerminar()
        }
    }
    Logotipo(ondas.value, pin.value, texto.value, cargando = animado && !cargado, Modifier.graphicsLayer { alpha = salida.value })
}

/**
 * Portada sin datos: tapa la app cuando pierde el foco (gesto de Recientes, salir, bajar las notificaciones),
 * así la miniatura que guarda Android es el logo. Funciona en todas las versiones y no impide capturas de pantalla.
 */
@Composable
fun Portada() = Logotipo(ondas = 1f, pin = 1f, texto = 1f, cargando = false)

/** Diálogos de Visenda que tienen el foco: un diálogo propio le quita el foco a la app, pero no debe taparla. */
val dialogosEnfocados = mutableIntStateOf(0)

/** Va dentro de cada diálogo (AlertDialog, DatePickerDialog): avisa mientras su ventana tiene el foco. */
@Composable
fun AvisarFocoDeDialogo() {
    val enfocado = LocalWindowInfo.current.isWindowFocused
    DisposableEffect(enfocado) {
        if (enfocado) dialogosEnfocados.intValue++
        onDispose { if (enfocado) dialogosEnfocados.intValue-- }
    }
}

/** El banner armado en pantalla: [ondas], [pin] y [texto] van de 0 (oculto) a 1 (en su lugar). */
@Composable
private fun Logotipo(ondas: Float, pin: Float, texto: Float, cargando: Boolean, modifier: Modifier = Modifier) {
    val banner = ImageBitmap.imageResource(R.drawable.banner_visenda)
    val nombre = remember(banner) { BitmapPainter(banner, RECORTE_NOMBRE.first, RECORTE_NOMBRE.second) }
    val frase = remember(banner) { BitmapPainter(banner, RECORTE_FRASE.first, RECORTE_FRASE.second) }
    Box(modifier.fillMaxSize().background(FONDO_BANNER)) {
        Canvas(Modifier.fillMaxSize()) { dibujarOndas(ondas) }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painterResource(R.mipmap.ic_launcher_foreground), null,
                Modifier.size(200.dp).graphicsLayer {
                    translationY = (1 - pin) * -320.dp.toPx()
                    alpha = pin.coerceIn(0f, 1f)
                },
            )
            Column(
                Modifier.fillMaxWidth().graphicsLayer {
                    alpha = texto
                    translationY = (1 - texto) * 20.dp.toPx()
                }.semantics(mergeDescendants = true) { contentDescription = "Visenda. App Android para visitadores en terreno" },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Mismas proporciones que en el banner: la frase es más ancha que el nombre y ambos parten alineados a la izquierda.
                Image(nombre, null, Modifier.fillMaxWidth(RECORTE_NOMBRE.second.width / RECORTE_FRASE.second.width.toFloat()).aspectRatio(RECORTE_NOMBRE.second.width.toFloat() / RECORTE_NOMBRE.second.height))
                Image(frase, null, Modifier.fillMaxWidth().aspectRatio(RECORTE_FRASE.second.width.toFloat() / RECORTE_FRASE.second.height))
            }
            Box(Modifier.height(24.dp)) {
                if (cargando) LinearProgressIndicator(Modifier.width(140.dp).padding(top = 16.dp), color = Marino, trackColor = Celeste)
            }
        }
    }
}

/** Ondas de las esquinas del banner: arriba a la derecha y, giradas, abajo a la izquierda. */
private fun DrawScope.dibujarOndas(avance: Float) {
    val w = size.width
    val h = size.height
    onda(w, h, avance)
    rotate(180f) { onda(w, h, avance) }
}

private fun DrawScope.onda(w: Float, h: Float, avance: Float) {
    val desplazar = (1 - avance) * w * 0.4f // entran desde la orilla
    val banda = Path().apply {
        moveTo(w * 0.35f + desplazar, 0f)
        cubicTo(w * 0.65f + desplazar, h * 0.02f, w * 0.8f, h * 0.14f, w, h * 0.2f)
        lineTo(w, 0f)
        close()
    }
    drawPath(banda, Celeste)
    val linea = Path().apply {
        moveTo(w * 0.6f + desplazar, -4f)
        cubicTo(w * 0.78f + desplazar, h * 0.03f, w * 0.88f, h * 0.08f, w + 8f, h * 0.12f)
    }
    drawPath(linea, Marino, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
}

/** El pin del logo saltando: para esperas como la importación del kardex. */
@Composable
fun PinSaltando(modifier: Modifier = Modifier) {
    val salto by rememberInfiniteTransition(label = "pin").animateFloat(
        0f, 1f, infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "salto",
    )
    Image(
        painterResource(R.mipmap.ic_launcher_foreground), null,
        modifier.graphicsLayer { translationY = -salto * 18.dp.toPx() },
    )
}

/** Avance de una tarea larga con el pin del logo. fraccion null = sin porcentaje conocido. */
@Composable
fun ProgresoConLogo(texto: String, fraccion: Float?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PinSaltando(Modifier.size(140.dp))
        Text(texto, color = Marino, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (fraccion == null) LinearProgressIndicator(Modifier.width(220.dp), color = Marino, trackColor = Celeste)
        else LinearProgressIndicator(progress = { fraccion }, modifier = Modifier.width(220.dp), color = Marino, trackColor = Celeste)
    }
}
