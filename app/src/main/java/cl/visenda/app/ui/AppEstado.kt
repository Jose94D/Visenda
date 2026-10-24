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
import cl.visenda.app.dominio.FRECUENCIAS
import cl.visenda.app.dominio.Motivo
import cl.visenda.app.dominio.Opcion
import cl.visenda.app.dominio.Paso
import cl.visenda.app.dominio.Pedido
import cl.visenda.app.dominio.urgencia
import cl.visenda.app.dominio.explicar
import cl.visenda.app.dominio.desvios
import cl.visenda.app.dominio.PREMIO_ELEGIDO_EN_RIESGO
import cl.visenda.app.dominio.PREMIO_ELEGIDO
import cl.visenda.app.dominio.KM_POR_VISITA
import cl.visenda.app.dominio.Plan
import cl.visenda.app.dominio.Jornada
import cl.visenda.app.dominio.Reglas
import cl.visenda.app.dominio.SIN_VIAJE
import cl.visenda.app.dominio.estadosDe
import cl.visenda.app.dominio.horariosDelDia
import cl.visenda.app.dominio.optimizar
import cl.visenda.app.dominio.exportarCliente
import cl.visenda.app.importar.Cambios
import cl.visenda.app.importar.LectorXlsx
import cl.visenda.app.importar.Rol
import cl.visenda.app.importar.dentroDeZona
import cl.visenda.app.importar.geocodificar
import cl.visenda.app.importar.limpiarDireccion
import cl.visenda.app.importar.mapeoDeTexto
import cl.visenda.app.importar.mismaComuna
import cl.visenda.app.mapas.GestorMapas
import cl.visenda.app.data.BaseCifrada
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
    /** Desde dónde parte, para mostrarlo: la ubicación del teléfono o el punto elegido. */
    val origen: String,
    /** (hora de inicio, índice de la parada después de la cual se almuerza; 0 = antes de la primera). */
    val colacion: Pair<Int, Int>? = null,
    /** Llegada de vuelta al punto de partida, si se pidió volver. */
    val vuelta: Int? = null,
    /** Hora de salida para llegar a la primera visita justo cuando atiende. */
    val sale: Int? = null,
)

/** Centro del mapa cuando no hay otro punto: Concepción. */
val CENTRO_POR_DEFECTO = -36.8270 to -73.0503

/** Candidatos que se mandan a Valhalla y al optimizador al sugerir. */
const val MAXIMO_CANDIDATOS = 35

/** Distancia en línea recta (haversine), en km. */
fun kmEnLinea(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val r = Math.PI / 180
    val h = Math.pow(Math.sin((b.first - a.first) * r / 2), 2.0) +
        Math.cos(a.first * r) * Math.cos(b.first * r) * Math.pow(Math.sin((b.second - a.second) * r / 2), 2.0)
    return 2 * 6371 * Math.asin(Math.sqrt(h))
}

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

    private val prefsSeguridad = context.getSharedPreferences("seguridad", Context.MODE_PRIVATE)

    private val prefsPlan = context.getSharedPreferences("planificacion", Context.MODE_PRIVATE)

    /** Ajustes > Planificación. Van también dentro de datos porque cambian los estados de los clientes. */
    var reglas by mutableStateOf(Reglas().let { d ->
        with(prefsPlan) {
            Reglas(
                getInt("minutosVisita", d.minutosVisita), getInt("diasAviso", d.diasAviso), getInt("salida", d.salida),
                getInt("finJornada", d.finJornada), getInt("colacion", d.colacion), getInt("colacionDesde", d.colacionDesde),
                getInt("colacionHasta", d.colacionHasta), getBoolean("sabados", d.sabados), getBoolean("volver", d.volver),
            )
        }
    })
        private set

    fun guardarReglas(r: Reglas) {
        prefsPlan.edit()
            .putInt("minutosVisita", r.minutosVisita).putInt("diasAviso", r.diasAviso).putInt("salida", r.salida)
            .putInt("finJornada", r.finJornada).putInt("colacion", r.colacion).putInt("colacionDesde", r.colacionDesde)
            .putInt("colacionHasta", r.colacionHasta).putBoolean("sabados", r.sabados).putBoolean("volver", r.volver)
            .apply()
        reglas = r
        datos = datos.copy(reglas = r)
        ruta = null
    }

    private val prefsApp = context.getSharedPreferences("visenda", Context.MODE_PRIVATE)

    /** La tarjeta de la guía de uso en Inicio desaparece al terminar la guía o al cerrarla con la X. */
    var guiaVista by mutableStateOf(prefsApp.getBoolean("guiaVista", false))
        private set

    fun marcarGuiaVista() {
        prefsApp.edit().putBoolean("guiaVista", true).apply()
        guiaVista = true
    }

    /** Ajustes > Bloqueo con huella o PIN. */
    var bloqueoActivo by mutableStateOf(prefsSeguridad.getBoolean("bloqueo", false))
        private set

    /** La app está tapada hasta verificar: al abrirse y al volver tras MINUTOS_SIN_BLOQUEO en segundo plano (CA-8.2). */
    var bloqueado by mutableStateOf(bloqueoActivo)
        private set
    private var enFondoDesde: Long? = null

    fun activarBloqueo(activo: Boolean) {
        prefsSeguridad.edit().putBoolean("bloqueo", activo).apply()
        bloqueoActivo = activo
    }

    fun desbloquear() { bloqueado = false }
    fun alIrAFondo() { enFondoDesde = System.currentTimeMillis() }
    fun alVolver() {
        val desde = enFondoDesde ?: return
        enFondoDesde = null
        if (bloqueoActivo && System.currentTimeMillis() - desde >= MINUTOS_SIN_BLOQUEO * 60_000L) bloqueado = true
    }

    /** Texto de avance mientras se ubican las direcciones importadas; null si no hay nada en curso. */
    var progresoImportacion by mutableStateOf<String?>(null)
        private set
    /** Fracción 0..1 mientras se ubican las direcciones; null mientras se guarda (sin porcentaje). */
    var fraccionImportacion by mutableStateOf<Float?>(null)
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
        datos = withContext(Dispatchers.IO) { dao.todo() }.copy(reglas = reglas)
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
                fraccionImportacion = i.toFloat() / pendientes.size
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
        fraccionImportacion = null
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
        // La base está cifrada con la clave de este teléfono: el respaldo lleva una copia sin cifrar, cifrada después con la contraseña.
        val temporal = File(context.cacheDir, "respaldo.db")
        val base = try {
            BaseCifrada.descifrar(context.getDatabasePath(NOMBRE_BASE), BaseCifrada.clave(context), temporal)
            temporal.readBytes()
        } finally {
            listOf("", "-journal", "-wal", "-shm").forEach { File(temporal.path + it).delete() }
        }
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
            dao.empresas() // abre la base: la cifra con la clave de este teléfono y la migra si es de un esquema anterior
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

    /** Visitas por ciclo propias del cliente en una empresa (RN-01: mandan sobre la categoría); null vuelve a la de su categoría. */
    fun fijarVisitas(clienteId: Long, empresaId: Long, visitas: Int?) = scope.launch {
        require(visitas == null || visitas in FRECUENCIAS)
        withContext(Dispatchers.IO) { dao.fijarVisitas(clienteId, empresaId, visitas) }
        recargar()
    }

    suspend fun eliminarCliente(id: Long) {
        withContext(Dispatchers.IO) { dao.eliminarCliente(id) }
        seleccion = seleccion - id
        ruta = null
        recargar()
    }

    /** CA-5.3: texto con todo lo guardado del cliente, donde el usuario elija. */
    suspend fun exportarCliente(id: Long, destino: Uri) = withContext(Dispatchers.IO) {
        val texto = datos.exportarCliente(id, java.time.LocalDateTime.now())
        context.contentResolver.openOutputStream(destino, "wt")?.use { it.write(texto.toByteArray()) } ?: throw java.io.IOException("No se pudo escribir el archivo.")
    }

    fun quitarDeCartera(empresaId: Long, clientes: List<Long>) = scope.launch {
        withContext(Dispatchers.IO) { dao.quitarDeCartera(empresaId, clientes) }
        recargar()
        seleccion = seleccion.filter { id -> datos.clientes.any { it.id == id } }
    }

    /** Planificar: sugerencias calculándose. */
    var sugiriendo by mutableStateOf(false)
        private set
    /** Por qué se sugirió cada cliente (Planificar). */
    var sugerencias by mutableStateOf(mapOf<Long, String>())
        private set

    /**
     * Punto de partida elegido en el mapa para la próxima ruta; null = la ubicación del teléfono.
     * Solo vive en memoria: la casa del visitador no se guarda (privacidad).
     */
    var partidaElegida by mutableStateOf<Pair<Double, Double>?>(null)

    /** Dónde centrar el mapa para elegir la partida: la elegida, la del teléfono o el centro de los clientes. */
    suspend fun centroParaPartida(): Pair<Double, Double> =
        partidaElegida ?: ubicacionActual()?.takeIf { (lat, lng) -> enRegiones(lat, lng) }
            ?: datos.lugares.filter { it.lat != null && it.lng != null }.takeIf { it.isNotEmpty() }?.let { l -> l.map { it.lat!! }.average() to l.map { it.lng!! }.average() }
            ?: CENTRO_POR_DEFECTO

    /** (punto, texto para mostrar) o un error que dice qué hacer. */
    private suspend fun partida(): Result<Pair<Pair<Double, Double>, String>> {
        partidaElegida?.let { return Result.success(it to "sale del punto elegido") }
        val aqui = ubicacionActual() ?: return Result.failure(Exception("No se obtuvo tu ubicación: elige el punto de partida en el mapa."))
        if (!enRegiones(aqui.first, aqui.second)) return Result.failure(Exception("Estás fuera de las regiones con mapa: elige el punto de partida en el mapa."))
        return Result.success(aqui to "sale desde tu ubicación")
    }

    private fun jornada(salida: Int) = with(reglas) { Jornada(salida, finJornada, minutosVisita, colacion, colacionDesde, colacionHasta, volver) }

    private class Planeado(val plan: Plan, val pedidos: List<Pedido>, val lugares: List<Lugar>, val viaje: Array<IntArray>, val km: Array<DoubleArray>)

    /** Arma los pedidos con sus lugares de ese día, pide a Valhalla tiempos y km, y optimiza. null si ninguno tiene lugar ese día. */
    private suspend fun planear(fecha: LocalDate, ids: List<Long>, desde: Pair<Double, Double>, jornada: Jornada, premio: (Long) -> Double): Planeado? {
        val opciones = ids.associateWith { id -> datos.horariosDelDia(id, fecha.dayOfWeek).filter { (l, _) -> l.lat != null && l.lng != null } }
        val lugares = opciones.values.flatten().map { it.first }.distinctBy { it.id }
        if (lugares.isEmpty()) return null
        val pedidos = ids.map { id -> Pedido(id, premio(id), opciones.getValue(id).map { (l, tramos) -> Opcion(1 + lugares.indexOf(l), tramos.map { it.abre to it.cierra }) }) }
        val (viaje, km) = withContext(Dispatchers.IO) { matriz(listOf(desde) + lugares.map { it.lat!! to it.lng!! }) }
        val plan = withContext(Dispatchers.Default) { optimizar(pedidos, viaje, km, jornada) }
        return Planeado(plan, pedidos, lugares, viaje, km)
    }

    /**
     * RN-06: entre los candidatos del día elige a quiénes conviene visitar según urgencia, km y espera, y explica por qué.
     * Con muchos candidatos, antes de pedir la matriz a Valhalla se quedan los que más valen descontando la distancia en línea recta.
     */
    fun sugerir(fecha: LocalDate, salidaMinutos: Int, candidatos: List<Long>) = scope.launch {
        errorRuta = null
        sugiriendo = true
        try {
            val (desde, _) = partida().getOrElse { errorRuta = it.message; return@launch }
            val urgencias = candidatos.associateWith { datos.urgencia(it, hoy, fecha) }
            val premio = { id: Long -> urgencias[id]?.premio ?: KM_POR_VISITA }
            val cerca = { id: Long ->
                datos.horariosDelDia(id, fecha.dayOfWeek).mapNotNull { (l, _) -> if (l.lat != null && l.lng != null) kmEnLinea(desde, l.lat to l.lng) else null }
                    .minOrNull() ?: Double.MAX_VALUE
            }
            // shortcut: la recta subestima el camino ~30 %; si quedan fuera clientes que convenían, subir MAXIMO_CANDIDATOS.
            val pool = candidatos.sortedByDescending { premio(it) - 1.3 * cerca(it) }.take(MAXIMO_CANDIDATOS)
            val jornada = jornada(salidaMinutos)
            val p = runCatching { planear(fecha, pool, desde, jornada, premio) }
                .getOrElse { errorRuta = "No se pudo calcular: ${it.message}. ¿Están descargados los mapas de la región?"; return@launch }
                ?: run { errorRuta = "Ninguno de los candidatos tiene ubicación."; return@launch }
            val desvio = withContext(Dispatchers.Default) { desvios(p.pedidos, p.viaje, p.km, jornada, p.plan) }
            seleccion = p.plan.pasos.map { it.clienteId }.distinct()
            sugerencias = seleccion.associateWith { id -> urgencias[id]?.let { explicar(it, desvio[id]) } ?: "Agregado por ti" }
            if (seleccion.isEmpty()) errorRuta = "Ningún candidato alcanza a ser visitado dentro de su horario."
        } finally {
            sugiriendo = false
        }
    }

    /** RN-07: el optimizador elige lugar y orden de los elegidos con los tiempos de Valhalla; luego se pide el trazado de ese orden. */
    fun calcularRuta(fecha: LocalDate, salidaMinutos: Int, alTerminar: () -> Unit) = scope.launch {
        errorRuta = null
        val (desde, origen) = partida().getOrElse { errorRuta = it.message; return@launch }
        runCatching {
            val p = planear(fecha, seleccion, desde, jornada(salidaMinutos)) { id ->
                val u = datos.urgencia(id, hoy, fecha)
                (if (u?.enRiesgo == true) PREMIO_ELEGIDO_EN_RIESGO else PREMIO_ELEGIDO) + (u?.premio ?: 0.0)
            } ?: return@runCatching "Ninguno de los clientes elegidos atiende ese día."
            val plan = p.plan
            if (plan.pasos.isEmpty()) return@runCatching "Ninguno de los elegidos alcanza a ser visitado dentro de su horario."

            // Visitas seguidas en el mismo lugar, sin esperar entre ellas, son una sola parada.
            val tandas = mutableListOf<MutableList<Paso>>()
            plan.pasos.forEach { s -> if (tandas.lastOrNull()?.last()?.lugar == s.lugar && s.inicio == s.llegada) tandas.last() += s else tandas += mutableListOf(s) }
            val paradas = tandas.map { t ->
                Parada(p.lugares[t[0].lugar - 1], t.map { s -> datos.clientes.first { it.id == s.clienteId } }, t[0].llegada, t[0].inicio, t.minOf { it.cierra })
            }.toMutableList()
            // Antes de la primera visita no se espera: se sale a tiempo para llegar cuando abre (así lo calcula el optimizador).
            val sale = paradas[0].inicio - p.viaje[0][plan.pasos[0].lugar]
            paradas[0] = paradas[0].copy(llegada = paradas[0].inicio)
            // Tras pasosAntes visitas: la colación va después de la parada donde se completan esas visitas.
            val colacion = plan.colacion?.let { c -> c.inicio to tandas.runningFold(0) { n, t -> n + t.size }.indexOfFirst { it >= c.pasosAntes } }
            val puntos = listOf(desde) + paradas.map { it.lugar.lat!! to it.lugar.lng!! } + listOfNotNull(desde.takeIf { plan.vuelta != null })
            val json = puntos.joinToString(",", """{"locations":[""", """],"costing":"auto","directions_type":"none"}""") { (lat, lon) ->
                """{"lat":$lat,"lon":$lon}"""
            }
            val trayecto = JSONObject(withContext(Dispatchers.IO) { motor.routeRaw(json) }).getJSONObject("trip")
            val tramos = trayecto.getJSONArray("legs")
            ruta = RutaDelDia(
                fecha = fecha,
                paradas = paradas,
                sale = sale,
                km = trayecto.getJSONObject("summary").getDouble("length"),
                termina = plan.termina,
                trazado = (0 until tramos.length()).flatMap { decodificarPolilinea(tramos.getJSONObject(it).getString("shape")) },
                fuera = plan.fuera.map { (id, motivo) -> datos.clientes.first { it.id == id } to motivo },
                origen = origen,
                colacion = colacion,
                vuelta = plan.vuelta,
            )
            null
        }.onSuccess {
            if (it != null) errorRuta = it else alTerminar()
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

    /** Minutos de manejo (redondeados hacia arriba) y km entre todos los puntos; SIN_VIAJE si Valhalla no encuentra camino. */
    private fun matriz(puntos: List<Pair<Double, Double>>): Pair<Array<IntArray>, Array<DoubleArray>> {
        val lista = puntos.joinToString(",", "[", "]") { (lat, lon) -> """{"lat":$lat,"lon":$lon}""" }
        val filas = JSONObject(motor.matrixRaw("""{"sources":$lista,"targets":$lista,"costing":"auto"}""")).getJSONArray("sources_to_targets")
        val celda = { i: Int, j: Int -> (filas.get(i) as JSONArray).getJSONObject(j) }
        val minutos = Array(puntos.size) { i ->
            IntArray(puntos.size) { j ->
                when {
                    i == j -> 0
                    celda(i, j).isNull("time") -> SIN_VIAJE
                    else -> Math.ceil(celda(i, j).getDouble("time") / 60).toInt()
                }
            }
        }
        val km = Array(puntos.size) { i -> DoubleArray(puntos.size) { j -> if (i == j) 0.0 else celda(i, j).optDouble("distance", 0.0) } }
        return minutos to km
    }
}
