package cl.visenda.app.mapas

import cl.visenda.app.importar.ZONA_BIOBIO_NUBLE
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Regiones publicadas en github.com/Jose94D/visenda-mapas (ver su README). */
const val URL_INDICE = "https://github.com/Jose94D/visenda-mapas/releases/latest/download/indice.json"
private const val VERSION_INDICE = 1

data class Archivo(val nombre: String, val bytes: Long, val sha256: String)

/** rect = [oeste, sur, este, norte]. */
data class Region(val clave: String, val nombre: String, val rect: List<Double>, val rutas: Archivo, val mapa: Archivo) {
    val bytes get() = rutas.bytes + mapa.bytes
    fun contiene(lat: Double, lng: Double) = lng in rect[0]..rect[2] && lat in rect[1]..rect[3]
}

data class Indice(val fecha: String, val regiones: List<Region>)

sealed interface EstadoRegion {
    data object Disponible : EstadoRegion
    data class Descargando(val fraccion: Float, val esperandoWifi: Boolean) : EstadoRegion
    data object Instalando : EstadoRegion
    data object Instalada : EstadoRegion
    data class Error(val mensaje: String) : EstadoRegion
}

fun leerIndice(json: String): Indice {
    val raiz = JSONObject(json)
    if (raiz.getInt("version") != VERSION_INDICE) throw IOException("Índice de mapas de una versión que esta app no conoce. Actualiza Visenda.")
    val regiones = raiz.getJSONObject("regiones")
    fun archivo(o: JSONObject) = Archivo(o.getString("archivo"), o.getLong("bytes"), o.getString("sha256"))
    return Indice(raiz.getString("fecha"), regiones.keys().asSequence().map { clave ->
        val r = regiones.getJSONObject(clave)
        val rect = r.getJSONArray("rect")
        Region(clave, r.getString("nombre"), List(4) { rect.getDouble(it) }, archivo(r.getJSONObject("rutas")), archivo(r.getJSONObject("mapa")))
    }.toList())
}

fun sha256(archivo: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    archivo.inputStream().use { entrada ->
        val buf = ByteArray(1 shl 16)
        while (true) { val n = entrada.read(buf); if (n < 0) break; md.update(buf, 0, n) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Descarga, verifica e instala regiones. Archivos en la carpeta externa de la app:
 * teselas/ (rutas de todas las regiones juntas), mapa/<clave>.pmtiles, regiones/<clave>.lista (teselas de cada región)
 * y regiones/indice.json (la última copia del índice, para funcionar sin conexión).
 */
class GestorMapas(private val context: Context) {
    private val base = context.getExternalFilesDir(null)!!
    val dirTeselas = File(base, "teselas")
    private val dirMapas = File(base, "mapa")
    private val dirRegiones = File(base, "regiones")
    private val dirDescargas = File(base, "descargas")
    private val archivoIndice = File(dirRegiones, "indice.json")
    private val dm = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("mapas", Context.MODE_PRIVATE)

    /** Del Hito 1: un mapa y rutas copiados a mano. Se usan mientras no haya regiones descargadas y se borran con la primera. */
    private val mapaAntiguo = File(dirMapas, "biobio-nuble.pmtiles")

    var soloWifi: Boolean
        get() = prefs.getBoolean("solo_wifi", true)
        set(v) = prefs.edit().putBoolean("solo_wifi", v).apply()

    fun indiceGuardado(): Indice? = runCatching { leerIndice(archivoIndice.readText()) }.getOrNull()

    /** Baja el índice publicado; sin conexión devuelve la copia guardada. Solo se consulta GitHub: ningún dato sale del teléfono. */
    fun actualizarIndice(): Indice {
        val texto = runCatching {
            (URL(URL_INDICE).openConnection() as HttpURLConnection).run {
                connectTimeout = 15_000; readTimeout = 15_000
                try { if (responseCode != 200) throw IOException("HTTP $responseCode") else inputStream.bufferedReader().readText() } finally { disconnect() }
            }
        }.getOrElse { return indiceGuardado() ?: throw IOException("Sin conexión y sin índice guardado de mapas.", it) }
        val indice = leerIndice(texto)
        dirRegiones.mkdirs()
        archivoIndice.writeText(texto)
        return indice
    }

    fun instaladas(): Set<String> =
        dirRegiones.listFiles { f -> f.extension == "lista" }.orEmpty().map { it.nameWithoutExtension }.filter { File(dirMapas, "$it.pmtiles").exists() }.toSet()

    /** Mapas para dibujar: los de las regiones instaladas o, si no hay ninguna, el del Hito 1. */
    fun archivosMapa(): List<File> =
        instaladas().map { File(dirMapas, "$it.pmtiles") }.ifEmpty { listOf(mapaAntiguo).filter { it.exists() } }

    /** Zonas [sur, oeste, norte, este] (formato de dentroDeZona) de las regiones instaladas, o de Biobío + Ñuble con los datos del Hito 1. */
    fun zonas(): List<DoubleArray> {
        val instaladas = instaladas()
        val regiones = indiceGuardado()?.regiones?.filter { it.clave in instaladas }.orEmpty()
        if (regiones.isEmpty()) return if (mapaAntiguo.exists()) listOf(ZONA_BIOBIO_NUBLE) else emptyList()
        return regiones.map { doubleArrayOf(it.rect[1], it.rect[0], it.rect[3], it.rect[2]) }
    }

    fun estado(region: Region): EstadoRegion {
        prefs.getString("error_${region.clave}", null)?.let { return EstadoRegion.Error(it) }
        val ids = idsDescarga(region) ?: return if (region.clave in instaladas()) EstadoRegion.Instalada else EstadoRegion.Disponible
        var hechos = 0L
        var esperandoWifi = false
        var exitosas = 0
        ids.forEach { id ->
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (!c.moveToFirst()) return EstadoRegion.Error("La descarga se canceló.")
                when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> exitosas++
                    DownloadManager.STATUS_FAILED -> return EstadoRegion.Error("La descarga falló. Intenta de nuevo.")
                    DownloadManager.STATUS_PAUSED -> esperandoWifi = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)) == DownloadManager.PAUSED_QUEUED_FOR_WIFI
                }
                hechos += c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)).coerceAtLeast(0)
            }
        }
        return if (exitosas == ids.size) EstadoRegion.Instalando else EstadoRegion.Descargando(hechos.toFloat() / region.bytes, esperandoWifi)
    }

    /** CA-8.1: DownloadManager reanuda sola una descarga cortada; con "Solo con Wi-Fi" espera a estar en Wi-Fi. */
    fun descargar(region: Region, urlRelease: String = URL_INDICE.substringBeforeLast('/')) {
        prefs.edit().remove("error_${region.clave}").apply()
        dirDescargas.mkdirs()
        val ids = listOf(region.rutas, region.mapa).map { a ->
            File(dirDescargas, a.nombre).delete()
            dm.enqueue(
                DownloadManager.Request(Uri.parse("$urlRelease/${a.nombre}"))
                    .setTitle("Visenda: ${region.nombre}")
                    .setDestinationInExternalFilesDir(context, "descargas", a.nombre)
                    .setAllowedOverMetered(!soloWifi)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE),
            )
        }
        prefs.edit().putString("descarga_${region.clave}", ids.joinToString(",")).apply()
    }

    fun cancelar(region: Region) {
        idsDescarga(region)?.let { dm.remove(*it.toLongArray()) }
        prefs.edit().remove("descarga_${region.clave}").remove("error_${region.clave}").apply()
        listOf(region.rutas, region.mapa).forEach { File(dirDescargas, it.nombre).delete() }
    }

    /** Lento (lee y extrae cientos de MB): llamar fuera del hilo principal. Devuelve true si instaló algo. */
    @Synchronized // la app al abrirse y la pantalla de regiones pueden pedirlo a la vez
    fun instalarTerminadas(indice: Indice): Boolean = indice.regiones.filter { estado(it) == EstadoRegion.Instalando }.onEach { region ->
        try {
            instalar(region)
        } catch (e: Exception) {
            prefs.edit().putString("error_${region.clave}", e.message ?: "No se pudo instalar.").apply()
        } finally {
            prefs.edit().remove("descarga_${region.clave}").apply()
            listOf(region.rutas, region.mapa).forEach { File(dirDescargas, it.nombre).delete() }
        }
    }.isNotEmpty()

    private fun instalar(region: Region) {
        val rutas = File(dirDescargas, region.rutas.nombre)
        val mapa = File(dirDescargas, region.mapa.nombre)
        listOf(rutas to region.rutas, mapa to region.mapa).forEach { (f, a) ->
            if (f.length() != a.bytes || sha256(f) != a.sha256) throw IOException("El archivo de ${region.nombre} llegó dañado. Descárgalo de nuevo.")
        }
        // La primera región reemplaza los datos copiados a mano en el Hito 1, que son de otro build y no calzan en los bordes.
        if (instaladas().isEmpty()) { dirTeselas.deleteRecursively(); mapaAntiguo.delete() }
        dirTeselas.mkdirs(); dirMapas.mkdirs(); dirRegiones.mkdirs()
        val lista = rutas.inputStream().buffered().use { extraerTar(it, dirTeselas) }
        File(dirRegiones, "${region.clave}.lista").writeText(lista.joinToString("\n"))
        if (!mapa.renameTo(File(dirMapas, "${region.clave}.pmtiles"))) mapa.copyTo(File(dirMapas, "${region.clave}.pmtiles"), overwrite = true)
    }

    /** Borra la región sin tocar las teselas de borde que comparte con otra región instalada. */
    fun borrar(region: Region) {
        val otras = instaladas().filter { it != region.clave }.flatMap { File(dirRegiones, "$it.lista").readLines() }.toSet()
        File(dirRegiones, "${region.clave}.lista").takeIf { it.exists() }?.readLines()?.filter { it !in otras }?.forEach { File(dirTeselas, it).delete() }
        File(dirRegiones, "${region.clave}.lista").delete()
        File(dirMapas, "${region.clave}.pmtiles").delete()
        prefs.edit().remove("error_${region.clave}").apply()
    }

    fun espacioUsado(): Long = listOf(dirTeselas, dirMapas).sumOf { d -> d.walk().filter { it.isFile }.sumOf { it.length() } }

    private fun idsDescarga(region: Region) = prefs.getString("descarga_${region.clave}", null)?.split(',')?.map { it.toLong() }
}
