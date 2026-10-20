package cl.visenda.app.importar

import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Rectángulo de Biobío + Ñuble: sur, oeste, norte, este. */
val ZONA_BIOBIO_NUBLE = doubleArrayOf(-38.55, -73.95, -35.95, -71.0)

// Lo que identifica el local dentro del edificio confunde al geocoder: se quita solo para buscar (RN-10).
private val SUFIJO_INTERIOR = Regex(
    """,?\s*\b(local|oficina|of|departamento|depto|dpto|piso|torre|box|consulta)\b\.?\s*[\w-]+""",
    RegexOption.IGNORE_CASE,
)

fun limpiarDireccion(direccion: String): String =
    direccion.replace(SUFIJO_INTERIOR, "").replace(Regex("""\s{2,}"""), " ").trim().trim(',')

fun normalizar(texto: String) =
    java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "").lowercase().trim()

/** La comuna es la última parte de la dirección del kardex ("…, Concepción"). */
fun comunaEsperada(direccion: String) = normalizar(direccion.substringAfterLast(','))

/** ¿El resultado cae en la comuna escrita? Android la entrega en locality o en subAdminArea según el caso. */
fun mismaComuna(direccion: String, locality: String?, subAdminArea: String?) =
    comunaEsperada(direccion).let { esperada -> listOfNotNull(locality, subAdminArea).any { normalizar(it) == esperada } }

fun dentroDeZona(lat: Double, lon: Double, zona: DoubleArray = ZONA_BIOBIO_NUBLE) =
    lat in zona[0]..zona[2] && lon in zona[1]..zona[3]

/** Busca una dirección (hasta [maximo] candidatos); con [zona] limita los resultados a ese rectángulo. */
suspend fun geocodificar(geocoder: Geocoder, texto: String, zona: DoubleArray?, maximo: Int = 1): List<Address> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { cont ->
            val listener = object : Geocoder.GeocodeListener {
                override fun onGeocode(resultados: MutableList<Address>) = cont.resume(resultados.toList())
                override fun onError(mensaje: String?) = cont.resume(emptyList())
            }
            if (zona == null) geocoder.getFromLocationName(texto, maximo, listener)
            else geocoder.getFromLocationName(texto, maximo, zona[0], zona[1], zona[2], zona[3], listener)
        }
    } else {
        @Suppress("DEPRECATION")
        runCatching {
            if (zona == null) geocoder.getFromLocationName(texto, maximo)
            else geocoder.getFromLocationName(texto, maximo, zona[0], zona[1], zona[2], zona[3])
        }.getOrNull().orEmpty()
    }
