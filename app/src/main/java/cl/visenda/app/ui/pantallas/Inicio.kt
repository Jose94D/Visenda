package cl.visenda.app.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.dominio.Datos
import cl.visenda.app.dominio.Estado
import cl.visenda.app.dominio.avance
import cl.visenda.app.dominio.horariosDelDia
import cl.visenda.app.dominio.peorEstado
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.BarraProgreso
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.Pantalla
import cl.visenda.app.ui.Tarjeta
import cl.visenda.app.ui.TituloSeccion
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.util.Locale

val ES = Locale.forLanguageTag("es-CL")

@Composable
fun Inicio(estado: AppEstado, nav: Navegador, modifier: Modifier) {
    val d = estado.datos
    val hoy = estado.hoy
    val enRiesgo = d.clientes.count { d.peorEstado(it.id, hoy) == Estado.EN_RIESGO }
    val sinUbicar = d.lugares.count { it.lat == null }
    val atiendenHoy = d.clientes.count { d.horariosDelDia(it.id, hoy.dayOfWeek).isNotEmpty() }
    val saludo = when (LocalTime.now().hour) { in 5..11 -> "Buenos días"; in 12..19 -> "Buenas tardes"; else -> "Buenas noches" }

    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(hoy.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)).replaceFirstChar { it.uppercase() }, color = Colores.Secundario, fontSize = 13.sp)
                Text(saludo, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!estado.guiaVista) item {
            Tarjeta(Modifier.clickable { nav.ir(Pantalla.Guia) }, relleno = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("¿Primera vez en Visenda?", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Mira la guía de uso: cómo empezar, paso a paso y con imágenes.", color = Colores.Secundario, fontSize = 13.sp)
                    }
                    IconButton(onClick = { estado.marcarGuiaVista() }) { Icon(Iconos.Cerrar, "No mostrar más la guía en Inicio", tint = Colores.Secundario) }
                }
            }
        }
        items(d.empresas.size) { i ->
            val a = d.avance(d.empresas[i], hoy)
            Tarjeta {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(a.empresa.nombre, fontWeight = FontWeight.SemiBold)
                    Text(a.ciclo?.nombre ?: "Sin ciclo vigente", color = Colores.Secundario, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${a.hechas}", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                        Text("de ${a.requeridas} visitas", color = Colores.Secundario, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    if (a.ciclo != null) Text("${a.diasRestantes} días restantes", color = Colores.Azul, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                BarraProgreso(if (a.requeridas == 0) 0f else a.hechas.toFloat() / a.requeridas, "Avance del ciclo: ${a.hechas} de ${a.requeridas}", alto = 8.dp)
            }
        }
        if (enRiesgo > 0) item {
            Fila(
                icono = Iconos.Alerta, fondo = Colores.NaranjaSuave, colorIcono = Color(0xFFB04A06),
                titulo = "$enRiesgo ${if (enRiesgo == 1) "cliente en riesgo" else "clientes en riesgo"}",
                detalle = "Les faltan visitas y quedan pocos días para cerrar el ciclo.",
                alTocar = { nav.ir(Pantalla.Clientes(Estado.EN_RIESGO)) },
            )
        }
        if (sinUbicar > 0) item {
            Fila(
                icono = Iconos.Lugar, fondo = Colores.NaranjaSuave, colorIcono = Color(0xFFB04A06),
                titulo = "$sinUbicar ${if (sinUbicar == 1) "dirección sin ubicar" else "direcciones sin ubicar"}",
                detalle = "Esos clientes no entran a las rutas hasta ubicarlos en el mapa.",
                alTocar = { nav.ir(Pantalla.SinUbicar) },
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Colores.Azul)
                    .clickable { nav.ir(Pantalla.Planificar) }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Iconos.Ruta, null, tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("Planificar el día", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("$atiendenHoy clientes atienden hoy", color = Color(0xFFDCE5FB), fontSize = 13.sp)
                }
                Icon(Iconos.Flecha, null, tint = Color.White)
            }
        }
        val cumples = cumpleanosProximos(d, hoy)
        if (cumples.isNotEmpty()) {
            item { TituloSeccion("Esta semana") }
            cumples.forEach { (clienteId, nombre, dia) ->
                item {
                    Fila(
                        icono = Iconos.Pastel, fondo = Color.White, colorIcono = Colores.Azul, conBorde = true,
                        titulo = nombre, detalle = "Cumpleaños · ${dia.format(DateTimeFormatter.ofPattern("EEEE d", ES))}",
                        alTocar = { nav.ir(Pantalla.Perfil(clienteId)) },
                    )
                }
            }
        }
    }
}

/** Busca un campo del kardex llamado "cumpleaños" o "fecha de nacimiento" con día y mes en los próximos 7 días. */
private fun cumpleanosProximos(d: Datos, hoy: LocalDate): List<Triple<Long, String, LocalDate>> =
    d.campos.filter { it.campo.lowercase(ES).let { c -> "cumple" in c || "nacimiento" in c } }.mapNotNull { campo ->
        val md = runCatching { MonthDay.parse("--" + campo.valor.take(5).split("-", "/").reversed().joinToString("-")) }.getOrNull()
            ?: return@mapNotNull null
        val fecha = md.atYear(hoy.year).let { if (it.isBefore(hoy)) it.plusYears(1) else it }
        if (fecha.isAfter(hoy.plusDays(7))) null
        else Triple(campo.clienteId, d.clientes.first { it.id == campo.clienteId }.nombre, fecha)
    }.distinctBy { it.first }.sortedBy { it.third }

@Composable
private fun Fila(icono: ImageVector, fondo: Color, colorIcono: Color, titulo: String, detalle: String, alTocar: () -> Unit, conBorde: Boolean = false) {
    val forma = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(forma).background(fondo)
            .then(if (conBorde) Modifier.border(1.dp, Colores.Borde, forma) else Modifier)
            .clickable(onClick = alTocar).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (conBorde) Colores.AzulSuave else Color.White), contentAlignment = Alignment.Center) {
            Icon(icono, null, tint = colorIcono, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detalle, color = Colores.Secundario, fontSize = 13.sp)
        }
        Icon(Iconos.Siguiente, null, tint = Colores.Secundario, modifier = Modifier.size(18.dp))
    }
}
