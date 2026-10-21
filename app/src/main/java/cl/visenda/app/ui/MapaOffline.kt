package cl.visenda.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.io.File

// Mapa offline: archivo PMTiles local (Protomaps) con un estilo propio en grises y la ruta en azul.

@Composable
fun MapaOffline(
    archivosMapa: List<File>,
    ruta: List<Pair<Double, Double>>,
    puntos: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
    /** Para elegir un punto: el mapa abre aquí, cerca, y avisa el centro cada vez que el usuario lo suelta. */
    centro: Pair<Double, Double>? = null,
    alDetenerse: ((Double, Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val avisar by rememberUpdatedState(alDetenerse)
    val mapView = remember {
        MapLibre.getInstance(context)
        // TextureView: con SurfaceView, en algunas pantallas de Compose (Ubicar) el mapa nunca quedaba listo y se veía vacío.
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(null)
        }
    }
    DisposableEffect(lifecycle) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observador)
        onDispose {
            lifecycle.removeObserver(observador)
            mapView.onDestroy()
        }
    }
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { vista ->
            vista.getMapAsync { mapa ->
                if (vista.tag == null) {
                    vista.tag = true
                    mapa.addOnCameraIdleListener { mapa.cameraPosition.target?.let { avisar?.invoke(it.latitude, it.longitude) } }
                }
                mapa.setStyle(Style.Builder().fromJson(estilo(archivosMapa, ruta, puntos))) {
                    val todos = ruta + puntos
                    val camara = if (centro != null) {
                        CameraUpdateFactory.newLatLngZoom(LatLng(centro.first, centro.second), 15.0) // el archivo de mapa llega hasta el zoom 15
                    } else if (todos.size >= 2) {
                        val limites = LatLngBounds.Builder().includes(todos.map { (lat, lon) -> LatLng(lat, lon) }).build()
                        CameraUpdateFactory.newLatLngBounds(limites, (48 * vista.resources.displayMetrics.density).toInt())
                    } else {
                        CameraUpdateFactory.newLatLngZoom(LatLng(-36.827, -73.050), 12.0) // Concepción
                    }
                    mapa.moveCamera(camara)
                }
            }
        },
    )
}

/** Decodifica una polilínea codificada (Valhalla usa precisión 6) a pares (lat, lon). */
fun decodificarPolilinea(texto: String, precision: Int = 6): List<Pair<Double, Double>> {
    val factor = Math.pow(10.0, precision.toDouble())
    val puntos = mutableListOf<Pair<Double, Double>>()
    var i = 0
    var lat = 0
    var lon = 0
    fun siguiente(): Int {
        var resultado = 0
        var desplazamiento = 0
        var b: Int
        do {
            b = texto[i++].code - 63
            resultado = resultado or ((b and 0x1f) shl desplazamiento)
            desplazamiento += 5
        } while (b >= 0x20)
        return if (resultado and 1 != 0) (resultado shr 1).inv() else resultado shr 1
    }
    while (i < texto.length) {
        lat += siguiente()
        lon += siguiente()
        puntos += lat / factor to lon / factor
    }
    return puntos
}

internal fun estilo(archivosMapa: List<File>, ruta: List<Pair<Double, Double>>, puntos: List<Pair<Double, Double>>): String {
    val coordenadas = ruta.joinToString(",", "[", "]") { (lat, lon) -> "[$lon,$lat]" }
    // Una línea sin puntos no es GeoJSON válido y MapLibre rechazaría todo el estilo (pasa en Ubicar, que no tiene ruta).
    val lineaRuta = if (ruta.size < 2) """{"type": "FeatureCollection", "features": []}"""
        else """{"type": "Feature", "properties": {}, "geometry": {"type": "LineString", "coordinates": $coordenadas}}"""
    // Un lugar visitado dos veces (mañana y tarde) es un solo marcador con ambos números: "2·4".
    val marcadores = puntos.withIndex().groupBy({ it.value }, { it.index + 1 }).entries.joinToString(",", "[", "]") { (p, numeros) ->
        """{"type":"Feature","properties":{"n":"${numeros.joinToString("·")}"},"geometry":{"type":"Point","coordinates":[${p.second},${p.first}]}}"""
    }
    // Una fuente por región. Las capas se intercalan por tipo (fondos de todas, luego etiquetas de todas)
    // para que el fondo de una región no tape las etiquetas de la vecina.
    val fuentes = archivosMapa.mapIndexed { i, f ->
        """"base$i": {"type": "vector", "url": "pmtiles://file://${f.absolutePath.replace('\\', '/')}", "attribution": "© OpenStreetMap · Protomaps"},"""
    }.joinToString("\n")
    fun porRegion(capas: String) = capas.trim().removeSuffix(",").split(",\n").flatMap { capa ->
        archivosMapa.indices.map { i -> capa.replace("\"source\": \"base\"", "\"source\": \"base$i\"").replaceFirst(Regex("\"id\": \"([^\"]+)\""), "\"id\": \"$1-$i\"") }
    }.joinToString(",\n") { it.trim() }.let { if (it.isEmpty()) "" else "$it," }
    val fondos = porRegion("""
        {"id": "tierra", "type": "fill", "source": "base", "source-layer": "earth", "paint": {"fill-color": "#F3F4F6"}},
        {"id": "parques", "type": "fill", "source": "base", "source-layer": "landuse", "filter": ["in", ["get", "kind"], ["literal", ["park", "forest", "wood", "nature_reserve", "grass"]]], "paint": {"fill-color": "#E4E9E1"}},
        {"id": "agua", "type": "fill", "source": "base", "source-layer": "water", "paint": {"fill-color": "#DCE1E8"}},
        {"id": "edificios", "type": "fill", "source": "base", "source-layer": "buildings", "minzoom": 14, "paint": {"fill-color": "#E6E8EC"}},
        {"id": "calles-borde", "type": "line", "source": "base", "source-layer": "roads", "filter": ["in", ["get", "kind"], ["literal", ["highway", "major_road"]]], "paint": {"line-color": "#D3D6DD", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 1.5, 16, 14]}},
        {"id": "calles", "type": "line", "source": "base", "source-layer": "roads", "filter": ["in", ["get", "kind"], ["literal", ["highway", "major_road", "minor_road"]]], "paint": {"line-color": "#FFFFFF", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.5, 16, 10]}}
    """)
    val etiquetas = porRegion("""
        {"id": "nombres-calles", "type": "symbol", "source": "base", "source-layer": "roads", "minzoom": 14, "layout": {"symbol-placement": "line", "text-field": ["get", "name"], "text-font": ["NotoSans"], "text-size": 12}, "paint": {"text-color": "#4B5160", "text-halo-color": "#FFFFFF", "text-halo-width": 1.5}},
        {"id": "ciudades", "type": "symbol", "source": "base", "source-layer": "places", "filter": ["==", ["get", "kind"], "locality"], "layout": {"text-field": ["get", "name"], "text-font": ["NotoSans"], "text-size": 14}, "paint": {"text-color": "#15171C", "text-halo-color": "#FFFFFF", "text-halo-width": 1.5}}
    """)
    return """
    {
      "version": 8,
      "glyphs": "asset://glyphs/{fontstack}/{range}.pbf",
      "sources": {
        $fuentes
        "ruta": {"type": "geojson", "data": $lineaRuta},
        "puntos": {"type": "geojson", "data": {"type": "FeatureCollection", "features": $marcadores}}
      },
      "layers": [
        {"id": "agua-fondo", "type": "background", "paint": {"background-color": "#DCE1E8"}},
        $fondos
        {"id": "ruta", "type": "line", "source": "ruta",
         "layout": {"line-cap": "round", "line-join": "round"},
         "paint": {"line-color": "#1F4FD1", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 3, 16, 8]}},
        $etiquetas
        {"id": "puntos", "type": "circle", "source": "puntos",
         "paint": {"circle-radius": 11, "circle-color": "#1F4FD1", "circle-stroke-color": "#FFFFFF", "circle-stroke-width": 2}},
        {"id": "numeros", "type": "symbol", "source": "puntos",
         "layout": {"text-field": ["get", "n"], "text-font": ["NotoSans"], "text-size": 12, "text-allow-overlap": true},
         "paint": {"text-color": "#FFFFFF"}}
      ]
    }
    """.trimIndent()
}
