package cl.visenda.app.lab

import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Prueba del Hito 1: direcciones escritas como vendrían en un kardex (con local, oficina, etc.).
val DIRECCIONES_PRUEBA = listOf(
    "Lincoyán 470, Local 4, Concepción",
    "Barros Arana 1050, Of. 21, Concepción",
    "Av. Paicaví 120, Oficina 304, Concepción",
    "O'Higgins 940, Depto 52, Concepción",
    "Castellón 450, Piso 3, Concepción",
    "Freire 1220, Concepción",
    "Av. Pedro Aguirre Cerda 1500, San Pedro de la Paz",
    "Av. Michimalonco 1150, San Pedro de la Paz",
    "Colón 3230, Local 12, Talcahuano",
    "Av. Manuel Rodríguez 1080, Chiguayante",
    "Janequeo 365, Of. 702, Concepción",
    "Chacabuco 916, Concepción",
    "Av. Collao 1202, Concepción",
    "Roosevelt 1550, Concepción",
    "Av. Los Carrera 301, Consulta 5, Concepción",
    "Arauco 645, Chillán",
    "Av. Libertad 1015, Of. 3, Chillán",
    "Constitución 550, Chillán",
    "Av. Bernardo O'Higgins 202, Coronel",
    "Serrano 527, Tomé",
)

/** Rectángulo de Biobío + Ñuble: sur, oeste, norte, este. */
val ZONA_BIOBIO_NUBLE = doubleArrayOf(-38.55, -73.95, -35.95, -71.0)

// Lo que identifica el local dentro del edificio confunde al geocoder: se quita solo para buscar (RN-10).
private val SUFIJO_INTERIOR = Regex(
    """,?\s*\b(local|oficina|of|departamento|depto|dpto|piso|torre|box|consulta)\b\.?\s*[\w-]+""",
    RegexOption.IGNORE_CASE,
)

fun limpiarDireccion(direccion: String): String =
    direccion.replace(SUFIJO_INTERIOR, "").replace(Regex("""\s{2,}"""), " ").trim().trim(',')

private fun normalizar(texto: String) =
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
