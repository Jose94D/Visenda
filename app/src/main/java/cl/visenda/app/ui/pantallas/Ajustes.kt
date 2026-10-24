package cl.visenda.app.ui.pantallas

import cl.visenda.app.ui.telefonoConBloqueo
import cl.visenda.app.ui.rememberVerificacion
import cl.visenda.app.ui.MINUTOS_SIN_BLOQUEO
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Switch
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.visenda.app.dominio.cicloVigente
import cl.visenda.app.ui.AppEstado
import cl.visenda.app.ui.Colores
import cl.visenda.app.ui.Iconos
import cl.visenda.app.ui.Navegador
import cl.visenda.app.ui.Pantalla
import cl.visenda.app.ui.Tarjeta
import cl.visenda.app.ui.TituloSeccion
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun Ajustes(estado: AppEstado, nav: Navegador, modifier: Modifier) {
    val d = estado.datos
    val corto = DateTimeFormatter.ofPattern("d MMM", ES)
    val regiones = remember(estado.archivosMapa) { estado.mapas.instaladas().size }

    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Ajustes", fontSize = 26.sp, fontWeight = FontWeight.SemiBold) }

        // Para quien recién empieza: los datos de ejemplo no cuentan como empresa propia ni como kardex importado.
        val pasos = listOf(
            Triple("Agrega tus empresas", "Los laboratorios o compañías para los que visitas.", d.empresas.any { it.id !in estado.idsDemo }) to Pantalla.EditarEmpresa(0),
            Triple("Importa tu kardex", "La planilla Excel con tus clientes.", d.cartera.any { it.empresaId !in estado.idsDemo }) to Pantalla.Importar,
            Triple("Descarga el mapa de tu región", "Sin mapa no se pueden calcular rutas.", regiones > 0) to Pantalla.Regiones,
        )
        if (pasos.any { !it.first.third }) item { PrimerosPasos(pasos, nav) }
        item {
            Tarjeta(Modifier.clickable { nav.ir(Pantalla.Guia) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Guía de uso", fontSize = 15.sp)
                        Text("Cómo usar Visenda, paso a paso y con imágenes", color = Colores.Secundario, fontSize = 13.sp)
                    }
                    Icon(Iconos.Siguiente, null, tint = Colores.Secundario, modifier = Modifier.size(18.dp))
                }
            }
        }

        item { Seccion("Empresas y ciclos", "Para quién trabajas y en qué período debes cumplir tus visitas.") }
        item {
            Lista(alTocar = { i -> nav.ir(Pantalla.EditarEmpresa(d.empresas[i].id)) }, filas = d.empresas.map { e ->
                val ciclo = d.cicloVigente(e.id, estado.hoy) ?: d.ciclos.filter { it.empresaId == e.id }.maxByOrNull { it.fin }
                val cats = d.categorias.filter { it.empresaId == e.id }
                e.nombre to listOfNotNull(
                    ciclo?.let { "${if (d.cicloVigente(e.id, estado.hoy) != null) "Ciclo actual" else it.nombre}: ${LocalDate.ofEpochDay(it.inicio).format(corto)} – ${LocalDate.ofEpochDay(it.fin).format(corto)}" },
                    if (cats.isEmpty()) null else "Visitas por ciclo: " + cats.joinToString(" · ") { "${it.nombre} ${it.frecuencia}" },
                ).joinToString("\n")
            })
        }

        item {
            OutlinedButton(onClick = { nav.ir(Pantalla.EditarEmpresa(0)) }, shape = RoundedCornerShape(12.dp)) { Text("Agregar empresa") }
        }

        item { Seccion("Tus clientes (kardex)") }
        item {
            Tarjeta(Modifier.clickable { nav.ir(Pantalla.Importar) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Iconos.Importar, null, tint = Colores.Azul, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Importar kardex", fontSize = 15.sp)
                        Text("Carga o actualiza tus clientes desde la planilla Excel de cada empresa", color = Colores.Secundario, fontSize = 13.sp)
                    }
                    Icon(Iconos.Siguiente, null, tint = Colores.Secundario, modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            TextButton(onClick = { nav.ir(Pantalla.GuiaKardex) }) { Text("¿Cómo preparar el kardex?") }
        }

        item { Seccion("Mapas sin conexión", "Descarga las regiones donde trabajas: así las rutas funcionan sin internet.") }
        item {
            Tarjeta(Modifier.clickable { nav.ir(Pantalla.Regiones) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Iconos.Lugar, null, tint = Colores.Azul, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Elegir regiones", fontSize = 15.sp)
                        Text(
                            when {
                                regiones > 0 -> "$regiones ${if (regiones == 1) "región descargada" else "regiones descargadas"}"
                                estado.archivosMapa.isNotEmpty() -> "Mapa de prueba de Biobío y Ñuble. Descarga tus regiones para reemplazarlo."
                                else -> "Ninguna descargada. Descarga tu región para poder calcular rutas."
                            },
                            color = Colores.Secundario, fontSize = 13.sp,
                        )
                    }
                    Icon(Iconos.Siguiente, null, tint = Colores.Secundario, modifier = Modifier.size(18.dp))
                }
            }
        }

        item { Seccion("Planificación", "Cómo arma Visenda tu día: horarios, duración de las visitas y avisos.") }
        item { AjustesPlanificacion(estado) }

        item { Seccion("Seguridad y respaldo", "Protege los datos de tus clientes y llévalos a otro teléfono.") }
        item {
            Tarjeta(Modifier.clickable { nav.ir(Pantalla.Respaldo) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Respaldo cifrado", fontSize = 15.sp)
                        Text("Guarda una copia protegida con contraseña, para cambiar de teléfono o no perder tus datos", color = Colores.Secundario, fontSize = 13.sp)
                    }
                    Icon(Iconos.Siguiente, null, tint = Colores.Secundario, modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            val context = LocalContext.current
            var aviso by remember { mutableStateOf<String?>(null) }
            // Activar o desactivar pide verificar: así nadie que tome el teléfono desbloqueado puede quitarlo.
            val verificar = rememberVerificacion(
                if (estado.bloqueoActivo) "Confirma para desactivar el bloqueo" else "Confirma para activar el bloqueo",
                alVerificar = { estado.activarBloqueo(!estado.bloqueoActivo); aviso = null },
                alFallar = { aviso = it },
            )
            Tarjeta {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bloqueo con huella o PIN", fontSize = 15.sp)
                        Text(
                            "Pide tu huella o el bloqueo del teléfono al abrir Visenda y al volver después de $MINUTOS_SIN_BLOQUEO minutos.",
                            color = Colores.Secundario, fontSize = 13.sp,
                        )
                    }
                    Switch(checked = estado.bloqueoActivo, onCheckedChange = {
                        if (!telefonoConBloqueo(context)) aviso = "Primero configura un bloqueo de pantalla (PIN, patrón o huella) en los ajustes del teléfono."
                        else verificar()
                    })
                }
                aviso?.let { Text(it, color = Colores.Naranja, fontSize = 13.sp) }
            }
        }

        item {
            Text(
                "Tus datos se guardan solo en este teléfono. Nadie más, ni siquiera el desarrollador, tiene acceso.",
                color = Colores.Secundario, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Título de sección con una línea que dice para qué sirve. */
@Composable
private fun Seccion(titulo: String, explicacion: String? = null) {
    Column {
        TituloSeccion(titulo)
        explicacion?.let { Text(it, color = Colores.Secundario, fontSize = 13.sp) }
    }
}

/** Lista de pasos para empezar; cada paso pendiente lleva a la pantalla donde se hace. */
@Composable
private fun PrimerosPasos(pasos: List<Pair<Triple<String, String, Boolean>, Pantalla>>, nav: Navegador) {
    Tarjeta(relleno = 0.dp) {
        Column {
            Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp)) {
                Text("Primeros pasos", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Completa estos pasos para empezar a planificar tus rutas.", color = Colores.Secundario, fontSize = 13.sp)
            }
            pasos.forEachIndexed { i, (paso, destino) ->
                val (titulo, detalle, listo) = paso
                Row(
                    Modifier.fillMaxWidth().then(if (listo) Modifier else Modifier.clickable { nav.ir(destino) }).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(if (listo) Colores.AzulSuave else Colores.Azul),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (listo) Icon(Iconos.Listo, "Listo", tint = Colores.Azul, modifier = Modifier.size(16.dp))
                        else Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(titulo, fontSize = 15.sp, color = if (listo) Colores.Secundario else Colores.Tinta)
                        Text(detalle, color = Colores.Secundario, fontSize = 13.sp)
                    }
                    if (!listo) Icon(Iconos.Siguiente, null, tint = Colores.Secundario, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun Lista(filas: List<Pair<String, String>>, alTocar: ((Int) -> Unit)? = null) {
    Tarjeta(relleno = 0.dp) {
        Column {
            filas.forEachIndexed { i, (titulo, detalle) ->
                if (i > 0) HorizontalDivider(color = Colores.Separador)
                Row(
                    Modifier.fillMaxWidth().then(if (alTocar != null) Modifier.clickable { alTocar(i) } else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(titulo, fontSize = 15.sp)
                        Text(detalle, color = Colores.Secundario, fontSize = 13.sp)
                    }
                    if (alTocar != null) Icon(Iconos.Siguiente, null, tint = Colores.Secundario, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
