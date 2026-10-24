package cl.visenda.app.ui.pantallas

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.R
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.Pantalla
import kotlinx.coroutines.launch

/** Una página de la guía. imagen: captura de la pantalla de la que habla. extra: botón opcional a otra pantalla. */
private class Pagina(val titulo: String, val pasos: List<String>, @DrawableRes val imagen: Int?, val extra: Pair<String, Pantalla>? = null)

private val PAGINAS = listOf(
    Pagina(
        "Te damos la bienvenida a Visenda",
        listOf(
            "Visenda te ayuda a cumplir las visitas de cada ciclo y arma la ruta del día según los horarios de tus clientes.",
            "Todo queda en tu teléfono: tus clientes no se envían a ningún servidor.",
            "Desliza para ver cómo empezar.",
        ),
        R.drawable.guia_inicio,
    ),
    Pagina(
        "1. Agrega tus empresas",
        listOf(
            "En Ajustes → Empresas y ciclos, agrega cada laboratorio o compañía para la que visitas.",
            "Define su ciclo: el período en que debes cumplir las visitas, normalmente el mes.",
            "Define sus categorías: cuántas veces visitar a cada grupo de clientes. Por ejemplo, A = 2 visitas y B = 1.",
        ),
        R.drawable.guia_ajustes,
    ),
    Pagina(
        "2. Importa tu kardex",
        listOf(
            "En Ajustes → Importar kardex, elige la planilla Excel (.xlsx) con tus clientes y la empresa a la que pertenece.",
            "Visenda propone qué contiene cada columna; revísalo y toca Importar.",
            "Las direcciones se ubican solas en el mapa. Las que no encuentre, las puedes ubicar a mano.",
        ),
        R.drawable.guia_importar,
        "Cómo preparar el kardex" to Pantalla.GuiaKardex,
    ),
    Pagina(
        "3. Descarga el mapa de tu región",
        listOf(
            "En Ajustes → Mapas sin conexión, descarga las regiones donde trabajas.",
            "Con el mapa descargado, las rutas se calculan sin internet.",
        ),
        R.drawable.guia_mapas,
        "Ir a Mapas sin conexión" to Pantalla.Regiones,
    ),
    Pagina(
        "4. Revisa tu avance en Inicio",
        listOf(
            "Cada empresa muestra cuántas visitas llevas en el ciclo y cuántos días quedan.",
            "El aviso naranjo indica los clientes en riesgo: les faltan visitas y queda poco tiempo.",
        ),
        R.drawable.guia_inicio,
    ),
    Pagina(
        "5. Tus clientes",
        listOf(
            "En Clientes los ves ordenados por estado: en riesgo, pendientes y completos. Si trabajas para varias empresas, puedes filtrar por cada una.",
            "Toca un cliente para ver sus consultas, horarios e historial.",
            "En su perfil puedes cambiar cuántas visitas le corresponden por ciclo.",
        ),
        R.drawable.guia_clientes,
    ),
    Pagina(
        "6. Planifica el día",
        listOf(
            "En Inicio, toca Planificar el día. Visenda sugiere a quién visitar según la urgencia del ciclo, los km y los horarios, y te dice por qué.",
            "Puedes marcar o desmarcar clientes, y elegir desde dónde partes.",
            "Toca Calcular ruta para ver el orden de las visitas.",
        ),
        R.drawable.guia_planificar,
    ),
    Pagina(
        "7. Sigue la ruta",
        listOf(
            "La ruta muestra a qué hora llegas a cada parada, cuánto esperas y cuándo almorzar.",
            "Toca Navegar para abrir la dirección en tu app de mapas.",
            "Después de cada visita, toca Marcar visita: así se actualiza tu avance.",
        ),
        R.drawable.guia_ruta,
    ),
    Pagina(
        "8. Protege tus datos",
        listOf(
            "En Ajustes → Seguridad y respaldo, activa el bloqueo con huella o PIN.",
            "Crea un respaldo cifrado para cambiar de teléfono o no perder tus datos.",
            "Puedes volver a ver esta guía cuando quieras desde Ajustes.",
        ),
        R.drawable.guia_seguridad,
    ),
)

/** Guía de uso para usuarios nuevos: páginas que se deslizan. Al terminarla queda marcada como vista. */
@Composable
fun Guia(estado: AppEstado, nav: Navegador, modifier: Modifier) {
    val pager = rememberPagerState { PAGINAS.size }
    val scope = rememberCoroutineScope()
    val ultima = pager.currentPage == PAGINAS.lastIndex

    Column(modifier) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }) { Icon(Iconos.Atras, "Volver") }
            Text("Guía de uso", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${pager.currentPage + 1} de ${PAGINAS.size}", color = Colores.Secundario, fontSize = 13.sp)
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i -> PaginaGuia(PAGINAS[i], nav) }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            PAGINAS.indices.forEach { i ->
                Box(Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(if (i == pager.currentPage) Colores.Azul else Colores.Borde))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Anterior") }
            Button(
                onClick = {
                    if (ultima) { estado.marcarGuiaVista(); nav.volver() }
                    else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(if (ultima) "Terminar" else "Siguiente", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun PaginaGuia(p: Pagina, nav: Navegador) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        p.imagen?.let {
            Image(
                painterResource(it), null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Colores.Borde, RoundedCornerShape(16.dp))
                    .background(Colores.Fondo).semantics { contentDescription = "Captura de la pantalla: ${p.titulo}" },
            )
        }
        Text(p.titulo, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        p.pasos.forEach { Text("• $it", fontSize = 15.sp) }
        p.extra?.let { (texto, destino) -> TextButton(onClick = { nav.ir(destino) }) { Text(texto) } }
    }
}
