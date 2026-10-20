package cl.visenda.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cl.visenda.app.data.Cliente
import cl.visenda.app.data.DatosDemo
import cl.visenda.app.data.Lugar
import cl.visenda.app.data.VisendaDatabase
import cl.visenda.app.data.Visita
import cl.visenda.app.dominio.Datos
import cl.visenda.app.dominio.Estado
import cl.visenda.app.dominio.visitables
import cl.visenda.app.dominio.agruparParadas
import cl.visenda.app.dominio.estadosDe
import cl.visenda.app.dominio.horariosDelDia
import cl.visenda.app.ui.decodificarPolilinea
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class Parada(val lugar: Lugar, val clientes: List<Cliente>, val llegada: Int, val abre: Int, val cierra: Int) {
    val espera get() = (abre - llegada).coerceAtLeast(0)
    val atrasada get() = maxOf(llegada, abre) + MINUTOS_POR_VISITA * clientes.size > cierra
}

data class RutaDelDia(val fecha: LocalDate, val paradas: List<Parada>, val km: Double, val termina: Int, val trazado: List<Pair<Double, Double>>)

const val MINUTOS_POR_VISITA = 15

// shortcut: punto de salida fijo en Concepción; usar la ubicación del teléfono en el hito 3.
val SALIDA = -36.8270 to -73.0503

/** Estado de toda la app. Una sola instancia, creada en VisendaApp. */
class AppEstado(context: Context, private val scope: CoroutineScope) {
    private val dao = VisendaDatabase.get(context).dao()
    val dirTeselas = File(context.getExternalFilesDir(null), "teselas")
    val archivoMapa = File(context.getExternalFilesDir(null), "mapa/biobio-nuble.pmtiles")

    var datos by mutableStateOf(Datos())
        private set
    var cargado by mutableStateOf(false)
        private set
    var hoy: LocalDate = LocalDate.now()
        private set

    /** Clientes elegidos en "Planificar el día" (o agregados desde un perfil). */
    var seleccion by mutableStateOf(listOf<Long>())
    var ruta by mutableStateOf<RutaDelDia?>(null)
        private set
    var errorRuta by mutableStateOf<String?>(null)
        private set

    // El motor tarda ~2 s en arrancar: se mantiene vivo mientras la app está abierta.
    private val motor by lazy { Valhalla(context, ValhallaConfigFactory.usingTilesDir(dirTeselas.absolutePath)) }

    init {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (dao.empresas().isEmpty()) dao.cargar(DatosDemo.datos(hoy))
            }
            recargar()
            cargado = true
        }
    }

    private suspend fun recargar() {
        hoy = LocalDate.now()
        datos = withContext(Dispatchers.IO) { dao.todo() }
    }

    /** RN-05: la visita cuenta para todas las empresas donde el cliente tiene visitas pendientes. */
    fun marcarVisita(clienteId: Long, lugarId: Long?) = scope.launch {
        val empresas = datos.estadosDe(clienteId, hoy)
            .filter { it.estado == Estado.EN_RIESGO || it.estado == Estado.PENDIENTE }
            .map { it.empresa.id }
            .ifEmpty { datos.cartera.filter { it.clienteId == clienteId }.map { it.empresaId } }
        withContext(Dispatchers.IO) {
            dao.marcarVisita(Visita(clienteId = clienteId, lugarId = lugarId, fechaHora = System.currentTimeMillis(), notas = null), empresas)
        }
        recargar()
    }

    fun visitadoHoy(clienteId: Long) = datos.visitas.any {
        it.clienteId == clienteId && cl.visenda.app.dominio.fechaDeVisita(it) == hoy
    }

    /** Ruta con las paradas ordenadas por hora de apertura (ver agruparParadas). */
    fun calcularRuta(fecha: LocalDate, salidaMinutos: Int, alTerminar: () -> Unit) = scope.launch {
        errorRuta = null
        val dia = fecha.dayOfWeek
        val grupos = agruparParadas(datos.visitables(seleccion, dia, salidaMinutos, MINUTOS_POR_VISITA), MINUTOS_POR_VISITA)
        if (grupos.isEmpty()) {
            errorRuta = "Ninguno de los clientes elegidos atiende ese día."
            return@launch
        }
        val lugares = grupos.map { g -> datos.lugares.first { it.id == g.lugarId } }
        runCatching {
            val puntos = listOf(SALIDA) + lugares.map { it.lat!! to it.lng!! }
            val json = puntos.joinToString(",", """{"locations":[""", """],"costing":"auto","directions_type":"none"}""") { (lat, lon) ->
                """{"lat":$lat,"lon":$lon}"""
            }
            val viaje = JSONObject(withContext(Dispatchers.IO) { motor.routeRaw(json) }).getJSONObject("trip")
            val tramos = viaje.getJSONArray("legs")
            var reloj = salidaMinutos
            val paradas = grupos.mapIndexed { i, g ->
                reloj += (tramos.getJSONObject(i).getJSONObject("summary").getDouble("time") / 60).toInt()
                val llegada = reloj
                reloj = maxOf(reloj, g.abre) + MINUTOS_POR_VISITA * g.clientes.size // si llega antes de que abra, espera
                Parada(lugares[i], g.clientes.map { id -> datos.clientes.first { it.id == id } }, llegada, g.abre, g.cierra)
            }
            RutaDelDia(
                fecha = fecha,
                paradas = paradas,
                km = viaje.getJSONObject("summary").getDouble("length"),
                termina = reloj,
                trazado = (0 until tramos.length()).flatMap { decodificarPolilinea(tramos.getJSONObject(it).getString("shape")) },
            )
        }.onSuccess {
            ruta = it
            alTerminar()
        }.onFailure {
            errorRuta = "No se pudo calcular la ruta: ${it.message}. ¿Están descargados los mapas de la región?"
        }
    }
}
