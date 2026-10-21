package cl.visenda.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.location.Geocoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cl.visenda.app.data.Categoria
import cl.visenda.app.data.Ciclo
import cl.visenda.app.data.Cliente
import cl.visenda.app.data.Empresa
import cl.visenda.app.data.DatosDemo
import cl.visenda.app.data.Lugar
import cl.visenda.app.data.VisendaDatabase
import cl.visenda.app.data.Visita
import cl.visenda.app.dominio.Datos
import cl.visenda.app.dominio.Estado
import cl.visenda.app.dominio.Motivo
import cl.visenda.app.dominio.Opcion
import cl.visenda.app.dominio.Paso
import cl.visenda.app.dominio.Pedido
import cl.visenda.app.dominio.SIN_VIAJE
import cl.visenda.app.dominio.estadosDe
import cl.visenda.app.dominio.horariosDelDia
import cl.visenda.app.dominio.optimizar
import cl.visenda.app.importar.Cambios
import cl.visenda.app.importar.LectorXlsx
import cl.visenda.app.importar.Rol
import cl.visenda.app.importar.dentroDeZona
import cl.visenda.app.importar.geocodificar
import cl.visenda.app.importar.limpiarDireccion
import cl.visenda.app.importar.mapeoDeTexto
import cl.visenda.app.importar.mismaComuna
import cl.visenda.app.mapas.GestorMapas
import cl.visenda.app.data.NOMBRE_BASE
import cl.visenda.app.data.VERSION_ESQUEMA
import cl.visenda.app.respaldo.Manifiesto
import cl.visenda.app.respaldo.RespaldoInvalido
import cl.visenda.app.respaldo.cifrarRespaldo
import cl.visenda.app.respaldo.descifrarRespaldo
import cl.visenda.app.ui.decodificarPolilinea
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlin.coroutines.resume

/** inicio: cuando empieza a atender (si llega antes de que abra, espera); cierra: hasta cuándo atiende el primero que cierra. */
data class Parada(val lugar: Lugar, val clientes: List<Cliente>, val llegada: Int, val inicio: Int, val cierra: Int) {
    val espera get() = inicio - llegada
}

data class RutaDelDia(
    val fecha: LocalDate,
    val paradas: List<Parada>,
    val km: Double,
    val termina: Int,
    val trazado: List<Pair<Double, Double>>,
    val fuera: List<Pair<Cliente, Motivo>>,
    /** Desde dónde parte, para mostrarlo: la ubicación del teléfono o SALIDA, con el motivo. */
    val origen: String,
)

const val MINUTOS_POR_VISITA = 15

// shortcut: sin ubicación del teléfono la ruta sale del centro de Concepción; agregar una dirección de salida en Ajustes.
val SALIDA = -36.8270 to -73.0503

/** Estado de toda la app. Una sola instancia, creada en VisendaApp. */
class AppEstado(private val context: Context, private val scope: CoroutineScope) {
    // Getter: al restaurar un respaldo la base se cierra y se vuelve a abrir.
    private val dao get() = VisendaDatabase.get(context).dao()
    val mapas = GestorMapas(context)
    /** Un .pmtiles por región descargada; cambia al instalar o borrar regiones. */
    var archivosMapa by mutableStateOf(mapas.archivosMapa())
        private set

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

    // El motor tarda ~2 s en arrancar: se mantiene vivo mientras la app está abierta y se rehace si cambian las regiones.
    private var motorActual: Valhalla? = null
    private val motor: Valhalla
        get() = motorActual ?: Valhalla(context, ValhallaConfigFactory.usingTilesDir(mapas.dirTeselas.absolutePath)).also { motorActual = it }

    /** Llamar después de instalar o borrar una región. */
    fun mapasCambiaron() {
        motorActual?.close()
        motorActual = null
        archivosMapa = mapas.archivosMapa()
        ruta = null
    }

    /** El geocodificador acepta un solo rectángulo: el que cubre todas las regiones descargadas (null si no hay ninguna). */
    private fun zonaBusqueda(): DoubleArray? = mapas.zonas().takeIf { it.isNotEmpty() }?.let { z ->
        doubleArrayOf(z.minOf { it[0] }, z.minOf { it[1] }, z.maxOf { it[2] }, z.maxOf { it[3] })
    }

    private fun enRegiones(lat: Double, lng: Double) = mapas.zonas().any { dentroDeZona(lat, lng, it) }

    /** Texto de avance mientras se ubican las direcciones importadas; null si no hay nada en curso. */
    var progresoImportacion by mutableStateOf<String?>(null)
        private set

    init {
        scope.launch {
            withContext(Dispatchers.IO) {
                // Los datos de ejemplo se cargan solo la primera vez: si el usuario los quita, no vuelven.
                val prefs = context.getSharedPreferences("visenda", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("iniciada", false)) {
                    if (dao.empresas().isEmpty()) dao.cargar(DatosDemo.datos(hoy))
                    prefs.edit().putBoolean("iniciada", true).apply()
                }
            }
            recargar()
            cargado = true
            // Descargas que terminaron con la app cerrada.
            val instalo = withContext(Dispatchers.IO) { mapas.indiceGuardado()?.let { mapas.instalarTerminadas(it) } ?: false }
            if (instalo) mapasCambiaron()
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

    val idsDemo get() = datos.empresas.filter { it.nombre in DatosDemo.EMPRESAS }.map { it.id }

    /** Lee la primera hoja del .xlsx elegido. Lanza LectorXlsx.FormatoInvalido si el archivo no sirve. */
    suspend fun leerKardex(uri: Uri): List<List<String>> = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { LectorXlsx.leer(it) } ?: throw LectorXlsx.FormatoInvalido("No se pudo abrir el archivo.")
    }

    suspend fun mapeoGuardado(empresaId: Long): Map<String, Rol> =
        withContext(Dispatchers.IO) { dao.mapeo(empresaId)?.configuracion?.let(::mapeoDeTexto).orEmpty() }

    /** Guarda el kardex y después ubica las direcciones nuevas (RN-11). alTerminar recibe cuántos lugares quedaron sin ubicar. */
    fun importar(cambios: Cambios, empresasABorrar: List<Long>, alTerminar: (Int) -> Unit, alFallar: (String) -> Unit) = scope.launch {
        runCatching { withContext(Dispatchers.IO) { dao.importar(cambios, empresasABorrar) } }.onFailure {
            alFallar("No se pudo guardar el kardex: ${it.message}")
            return@launch
        }
        if (empresasABorrar.isNotEmpty()) seleccion = emptyList()
        ruta = null
        recargar()
        val pendientes = datos.lugares.filter { it.lat == null && !it.corregidoManual }
        if (pendientes.isNotEmpty() && Geocoder.isPresent()) {
            val geocoder = Geocoder(context, Locale.forLanguageTag("es-CL"))
            pendientes.forEachIndexed { i, lugar ->
                progresoImportacion = "Ubicando direcciones: ${i + 1} de ${pendientes.size}"
                // Solo sale la dirección, nunca el nombre ni el RUT (RN-11, RN-14).
                val encontrado = runCatching {
                    geocodificar(geocoder, "${limpiarDireccion(lugar.direccion)}, Chile", zonaBusqueda(), maximo = 5)
                        .firstOrNull { enRegiones(it.latitude, it.longitude) && mismaComuna(lugar.direccion, it.locality, it.subAdminArea) }
                }.getOrNull()
                if (encontrado != null) withContext(Dispatchers.IO) { dao.ubicar(lugar.id, encontrado.latitude, encontrado.longitude) }
            }
            recargar()
        }
        progresoImportacion = null
        alTerminar(datos.lugares.count { it.lat == null })
    }

    /** Punto de partida para Ubicar: lo que ya tiene, lo mejor del geocodificador (sin exigir la comuna) o el centro de la comuna. */
    suspend fun sugerirUbicacion(lugar: Lugar): Pair<Double, Double>? {
        if (lugar.lat != null && lugar.lng != null) return lugar.lat to lugar.lng
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.forLanguageTag("es-CL"))
        return listOf(limpiarDireccion(lugar.direccion), lugar.direccion.substringAfterLast(',').trim()).firstNotNullOfOrNull { texto ->
            runCatching { geocodificar(geocoder, "$texto, Chile", zonaBusqueda()) }.getOrNull()
                ?.firstOrNull { enRegiones(it.latitude, it.longitude) }?.let { it.latitude to it.longitude }
        }
    }

    /** CA-7.1: queda marcada como ajustada a mano, así una reimportación no la pisa. */
    fun corregirUbicacion(lugarId: Long, lat: Double, lng: Double) = scope.launch {
        withContext(Dispatchers.IO) { dao.corregirUbicacion(lugarId, lat, lng) }
        recargar()
    }

    /** RN-13: la base completa más un manifiesto, cifrada con la contraseña. El usuario elige dónde guardarla. */
    suspend fun crearRespaldo(destino: Uri, contrasena: CharArray) = withContext(Dispatchers.IO) {
        val db = VisendaDatabase.get(context)
        // Pasa al archivo principal lo que aún está en el registro WAL, para copiar un solo archivo completo.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val base = context.getDatabasePath(NOMBRE_BASE).readBytes()
        val manifiesto = Manifiesto(
            versionApp = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?",
            esquema = VERSION_ESQUEMA, fecha = System.currentTimeMillis(), clientes = datos.clientes.size,
            regiones = mapas.instaladas().sorted(),
        )
        val archivo = withContext(Dispatchers.Default) { cifrarRespaldo(manifiesto, base, contrasena) }
        context.contentResolver.openOutputStream(destino, "wt")?.use { it.write(archivo) } ?: throw RespaldoInvalido("No se pudo escribir el respaldo.")
    }

    /** Descifra y valida sin tocar los datos actuales. */
    suspend fun leerRespaldo(origen: Uri, contrasena: CharArray): Pair<Manifiesto, ByteArray> = withContext(Dispatchers.IO) {
        val archivo = context.contentResolver.openInputStream(origen)?.use { it.readBytes() } ?: throw RespaldoInvalido("No se pudo abrir el archivo.")
        val (manifiesto, base) = withContext(Dispatchers.Default) { descifrarRespaldo(archivo, contrasena) }
        if (manifiesto.esquema > VERSION_ESQUEMA) throw RespaldoInvalido("El respaldo es de una versión más nueva de Visenda (${manifiesto.versionApp}). Actualiza la app para restaurarlo.")
        manifiesto to base
    }

    /** Reemplaza todos los datos. Si la base restaurada no abre, vuelve la anterior: nunca queda el teléfono sin datos. */
    suspend fun restaurarRespaldo(base: ByteArray) = withContext(Dispatchers.IO) {
        val actual = context.getDatabasePath(NOMBRE_BASE)
        val anterior = File(actual.path + ".anterior")
        val extras = listOf("-wal", "-shm", "-journal").map { File(actual.path + it) }
        VisendaDatabase.get(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        VisendaDatabase.cerrar()
        anterior.delete()
        if (!actual.renameTo(anterior)) throw RespaldoInvalido("No se pudo preparar la restauración.")
        extras.forEach { it.delete() }
        try {
            actual.writeBytes(base)
            dao.empresas() // abre la base (y la migra si es de un esquema anterior)
            anterior.delete()
        } catch (e: Exception) {
            VisendaDatabase.cerrar()
            actual.delete(); extras.forEach { it.delete() }
            anterior.renameTo(actual)
            throw RespaldoInvalido("No se pudo restaurar el respaldo; tus datos quedaron como estaban. (${e.message})")
        }
        withContext(Dispatchers.Main) { seleccion = emptyList(); ruta = null }
        recargar()
    }

    suspend fun guardarEmpresa(e: Empresa, ciclos: List<Ciclo>, categorias: List<Categoria>) {
        withContext(Dispatchers.IO) { dao.guardarEmpresaCompleta(e, ciclos, categorias) }
        recargar()
    }

    suspend fun eliminarEmpresa(id: Long) {
        withContext(Dispatchers.IO) { dao.eliminarEmpresa(id) }
        recargar()
        seleccion = seleccion.filter { c -> datos.clientes.any { it.id == c } }
        ruta = null
    }

    fun quitarDeCartera(empresaId: Long, clientes: List<Long>) = scope.launch {
        withContext(Dispatchers.IO) { dao.quitarDeCartera(empresaId, clientes) }
        recargar()
        seleccion = seleccion.filter { id -> datos.clientes.any { it.id == id } }
    }

    /** RN-07: el optimizador elige lugar y orden con los tiempos de Valhalla; luego se pide el trazado de ese orden. */
    fun calcularRuta(fecha: LocalDate, salidaMinutos: Int, finJornada: Int, alTerminar: () -> Unit) = scope.launch {
        errorRuta = null
        val dia = fecha.dayOfWeek
        val opciones = seleccion.associateWith { id -> datos.horariosDelDia(id, dia).filter { (l, _) -> l.lat != null && l.lng != null } }
        val lugares = opciones.values.flatten().map { it.first }.distinctBy { it.id }
        if (lugares.isEmpty()) {
            errorRuta = "Ninguno de los clientes elegidos atiende ese día."
            return@launch
        }
        val pedidos = seleccion.map { id ->
            Pedido(
                id, datos.estadosDe(id, hoy).any { it.estado == Estado.EN_RIESGO },
                opciones.getValue(id).map { (l, tramos) -> Opcion(1 + lugares.indexOf(l), tramos.map { it.abre to it.cierra }) },
            )
        }
        val ubicacion = ubicacionActual()
        val aqui = ubicacion?.takeIf { (lat, lng) -> enRegiones(lat, lng) }
        val origen = when {
            aqui != null -> "sale desde tu ubicación"
            ubicacion != null -> "sale desde el centro de Concepción: estás fuera de las regiones con mapa"
            else -> "sale desde el centro de Concepción: no se obtuvo tu ubicación"
        }
        val salida = aqui ?: SALIDA
        runCatching {
            val viaje = withContext(Dispatchers.IO) { matriz(listOf(salida) + lugares.map { it.lat!! to it.lng!! }) }
            val plan = withContext(Dispatchers.Default) { optimizar(pedidos, viaje, salidaMinutos, finJornada, MINUTOS_POR_VISITA) }
            val fuera = plan.fuera.map { (id, motivo) -> datos.clientes.first { it.id == id } to motivo }
            if (plan.pasos.isEmpty()) return@runCatching null

            // Visitas seguidas en el mismo lugar son una sola parada.
            val tandas = mutableListOf<MutableList<Paso>>()
            plan.pasos.forEach { p -> if (tandas.lastOrNull()?.last()?.lugar == p.lugar) tandas.last() += p else tandas += mutableListOf(p) }
            val paradas = tandas.map { t ->
                Parada(lugares[t[0].lugar - 1], t.map { p -> datos.clientes.first { it.id == p.clienteId } }, t[0].llegada, t[0].inicio, t.minOf { it.cierra })
            }
            val puntos = listOf(salida) + paradas.map { it.lugar.lat!! to it.lugar.lng!! }
            val json = puntos.joinToString(",", """{"locations":[""", """],"costing":"auto","directions_type":"none"}""") { (lat, lon) ->
                """{"lat":$lat,"lon":$lon}"""
            }
            val trayecto = JSONObject(withContext(Dispatchers.IO) { motor.routeRaw(json) }).getJSONObject("trip")
            val tramos = trayecto.getJSONArray("legs")
            RutaDelDia(
                fecha = fecha,
                paradas = paradas,
                km = trayecto.getJSONObject("summary").getDouble("length"),
                termina = plan.termina,
                trazado = (0 until tramos.length()).flatMap { decodificarPolilinea(tramos.getJSONObject(it).getString("shape")) },
                fuera = fuera,
                origen = origen,
            )
        }.onSuccess {
            if (it == null) errorRuta = "Ninguno de los elegidos alcanza a ser visitado dentro de su horario."
            else {
                ruta = it
                alTerminar()
            }
        }.onFailure {
            errorRuta = "No se pudo calcular la ruta: ${it.message}. ¿Están descargados los mapas de la región?"
        }
    }

    /** Ubicación actual (hasta 5 s de espera) o la última conocida; null sin permiso o sin señal. Nunca sale del teléfono. */
    @SuppressLint("MissingPermission")
    private suspend fun ubicacionActual(): Pair<Double, Double>? {
        val permiso = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!permiso) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val proveedores = listOfNotNull(if (Build.VERSION.SDK_INT >= 31) LocationManager.FUSED_PROVIDER else null, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val actual: Location? = if (Build.VERSION.SDK_INT >= 30) proveedores.firstOrNull()?.let { p ->
            withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { cont ->
                    val cancelar = CancellationSignal()
                    cont.invokeOnCancellation { cancelar.cancel() }
                    lm.getCurrentLocation(p, cancelar, context.mainExecutor) { cont.resume(it) }
                }
            }
        } else null
        val ultima = { (proveedores + LocationManager.PASSIVE_PROVIDER).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } }
        return (actual ?: ultima())?.let { it.latitude to it.longitude }
    }

    /** Minutos de manejo entre todos los puntos (redondeados hacia arriba); SIN_VIAJE si Valhalla no encuentra camino. */
    private fun matriz(puntos: List<Pair<Double, Double>>): Array<IntArray> {
        val lista = puntos.joinToString(",", "[", "]") { (lat, lon) -> """{"lat":$lat,"lon":$lon}""" }
        val filas = JSONObject(motor.matrixRaw("""{"sources":$lista,"targets":$lista,"costing":"auto"}""")).getJSONArray("sources_to_targets")
        return Array(puntos.size) { i ->
            val fila = filas.get(i) as JSONArray
            IntArray(puntos.size) { j ->
                val celda = fila.getJSONObject(j)
                when {
                    i == j -> 0
                    celda.isNull("time") -> SIN_VIAJE
                    else -> Math.ceil(celda.getDouble("time") / 60).toInt()
                }
            }
        }
    }
}
