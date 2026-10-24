package cl.visenda.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Íconos de trazo del mockup (24×24). Se dibujan desde el mismo SVG, sin librerías de íconos. */
object Iconos {
    private fun c(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun icono(vararg trazos: String): ImageVector {
        val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        trazos.forEach {
            b.addPath(
                PathParser().parsePathString(it).toNodes(), fill = null, stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val Inicio = icono("M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z")
    val Clientes = icono(c(9f, 8f, 3.5f), "M2.5 20c.8-3.6 3.4-5.5 6.5-5.5s5.7 1.9 6.5 5.5", "M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.8c1.8.7 3 2.4 3.5 5.2")
    val Ruta = icono(c(6f, 18f, 2.5f), c(18f, 6f, 2.5f), "M8.5 18H16a3.5 3.5 0 0 0 0-7H8a3.5 3.5 0 0 1 0-7h7.5")
    val Ajustes = icono("M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12", c(16f, 6f, 2f), c(10f, 12f, 2f), c(18f, 18f, 2f))
    val Atras = icono("m15 5-7 7 7 7")
    val Siguiente = icono("m9 5 7 7-7 7")
    val Buscar = icono(c(11f, 11f, 7f), "m20 20-4-4")
    val Reloj = icono(c(12f, 12f, 9f), "M12 7v5l3 2")
    val Lugar = icono("M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z", c(12f, 9.5f, 2.5f))
    val Alerta = icono("M12 3 2 20h20z", "M12 10v4M12 17v.5")
    val Listo = icono("m5 12.5 4.5 4.5L19 7")
    val Pastel = icono("M4 21v-8a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v8M3 21h18M4 16c2 1.5 4 1.5 5.3 0 1.4 1.5 4 1.5 5.4 0 1.3 1.5 3.3 1.5 5.3 0M12 11V7")
    val Telefono = icono("M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a1 1 0 0 1-1 1A17 17 0 0 1 4 5a1 1 0 0 1 1-1")
    val Agregar = icono("M12 5v14M5 12h14")
    val Navegar = icono("M3 11 21 3l-8 18-2-8z")
    val Flecha = icono("M5 12h14M13 6l6 6-6 6")
    val Importar = icono("M12 3v12M7 10l5 5 5-5M5 21h14")
    val Cerrar = icono("M6 6l12 12M18 6 6 18")
    val Candado = icono("M6 11h12v10H6z", "M8 11V7a4 4 0 0 1 8 0v4")
}
