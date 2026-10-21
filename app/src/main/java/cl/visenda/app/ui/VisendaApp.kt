package cl.visenda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.visenda.app.lab.Laboratorio
import cl.visenda.app.ui.pantallas.Ajustes
import cl.visenda.app.ui.pantallas.Clientes
import cl.visenda.app.ui.pantallas.EditarEmpresa
import cl.visenda.app.ui.pantallas.Importar
import cl.visenda.app.ui.pantallas.Inicio
import cl.visenda.app.ui.pantallas.Perfil
import cl.visenda.app.ui.pantallas.Planificar
import cl.visenda.app.ui.pantallas.Regiones
import cl.visenda.app.ui.pantallas.Respaldo
import cl.visenda.app.ui.pantallas.Ruta
import cl.visenda.app.ui.pantallas.SinUbicar
import cl.visenda.app.ui.pantallas.Ubicar
import cl.visenda.app.dominio.Estado

sealed interface Pantalla {
    data object Inicio : Pantalla
    data class Clientes(val filtro: Estado? = null) : Pantalla
    data object Ruta : Pantalla
    data object Ajustes : Pantalla
    data class Perfil(val clienteId: Long) : Pantalla
    data object Planificar : Pantalla
    data object Laboratorio : Pantalla
    data object Importar : Pantalla
    data object SinUbicar : Pantalla
    data object Regiones : Pantalla
    data object Respaldo : Pantalla
    data class EditarEmpresa(val empresaId: Long) : Pantalla
    data class Ubicar(val lugarId: Long) : Pantalla
}

/** Navegación mínima: una pila de pantallas. Las pestañas reinician la pila. */
class Navegador {
    val pila = mutableStateListOf<Pantalla>(Pantalla.Inicio)
    val actual get() = pila.last()
    fun ir(p: Pantalla) { pila += p }
    fun pestana(p: Pantalla) { pila.clear(); pila += p }
    fun volver() { if (pila.size > 1) pila.removeAt(pila.lastIndex) }
}

private data class Pestana(val pantalla: Pantalla, val texto: String, val icono: ImageVector)

private val PESTANAS = listOf(
    Pestana(Pantalla.Inicio, "Inicio", Iconos.Inicio),
    Pestana(Pantalla.Clientes(), "Clientes", Iconos.Clientes),
    Pestana(Pantalla.Ruta, "Ruta", Iconos.Ruta),
    Pestana(Pantalla.Ajustes, "Ajustes", Iconos.Ajustes),
)

@Composable
fun VisendaApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val estado = remember { AppEstado(context.applicationContext, scope) }
    val nav = remember { Navegador() }
    BackHandler(enabled = nav.pila.size > 1) { nav.volver() }

    TemaVisenda {
        val actual = nav.actual
        val esPestana = PESTANAS.any { it.pantalla::class == actual::class }
        Scaffold(
            containerColor = Colores.Fondo,
            bottomBar = {
                if (esPestana) NavigationBar(containerColor = Color.White) {
                    PESTANAS.forEach { p ->
                        NavigationBarItem(
                            selected = p.pantalla::class == actual::class,
                            onClick = { nav.pestana(p.pantalla) },
                            icon = { Icon(p.icono, contentDescription = null) },
                            label = { Text(p.texto) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Colores.Azul, selectedTextColor = Colores.Azul, indicatorColor = Colores.AzulSuave,
                                unselectedIconColor = Colores.Secundario, unselectedTextColor = Colores.Secundario,
                            ),
                        )
                    }
                }
            },
        ) { relleno ->
            val m = Modifier.fillMaxSize().padding(relleno)
            if (!estado.cargado) {
                Box(m, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }
            when (actual) {
                Pantalla.Inicio -> Inicio(estado, nav, m)
                is Pantalla.Clientes -> Clientes(estado, nav, actual.filtro, m)
                Pantalla.Ruta -> Ruta(estado, nav, m)
                Pantalla.Ajustes -> Ajustes(estado, nav, m)
                is Pantalla.Perfil -> Perfil(estado, nav, actual.clienteId, m)
                Pantalla.Planificar -> Planificar(estado, nav, m)
                Pantalla.Laboratorio -> Laboratorio(m.padding(24.dp))
                Pantalla.Importar -> Importar(estado, nav, m)
                Pantalla.SinUbicar -> SinUbicar(estado, nav, m)
                Pantalla.Regiones -> Regiones(estado, nav, m)
                Pantalla.Respaldo -> Respaldo(estado, nav, m)
                is Pantalla.EditarEmpresa -> EditarEmpresa(estado, nav, actual.empresaId, m)
                is Pantalla.Ubicar -> Ubicar(estado, nav, actual.lugarId, m)
            }
        }
    }
}
