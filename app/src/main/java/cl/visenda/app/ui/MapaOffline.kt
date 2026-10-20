package cl.visenda.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.io.File

// Mapa offline: archivo PMTiles local (Protomaps) con un estilo propio en grises y la ruta en azul.

@Composable
fun MapaOffline(
    archivoMapa: File,
    ruta: List<Pair<Double, Double>>,
    puntos: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
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
                mapa.setStyle(Style.Builder().fromJson(estilo(archivoMapa, ruta, puntos))) {
                    val todos = ruta + puntos
                    val camara = if (todos.size >= 2) {
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

private fun estilo(archivoMapa: File, ruta: List<Pair<Double, Double>>, puntos: List<Pair<Double, Double>>): String {
    val coordenadas = ruta.joinToString(",", "[", "]") { (lat, lon) -> "[$lon,$lat]" }
    // Un lugar visitado dos veces (mañana y tarde) es un solo marcador con ambos números: "2·4".
    val marcadores = puntos.withIndex().groupBy({ it.value }, { it.index + 1 }).entries.joinToString(",", "[", "]") { (p, numeros) ->
        """{"type":"Feature","properties":{"n":"${numeros.joinToString("·")}"},"geometry":{"type":"Point","coordinates":[${p.second},${p.first}]}}"""
    }
    return """
    {
      "version": 8,
      "glyphs": "asset://glyphs/{fontstack}/{range}.pbf",
      "sources": {
        "base": {"type": "vector", "url": "pmtiles://file://${archivoMapa.absolutePath}",
                 "attribution": "© OpenStreetMap · Protomaps"},
        "ruta": {"type": "geojson", "data": {"type": "Feature", "properties": {},
                 "geometry": {"type": "LineString", "coordinates": $coordenadas}}},
        "puntos": {"type": "geojson", "data": {"type": "FeatureCollection", "features": $marcadores}}
      },
      "layers": [
        {"id": "agua-fondo", "type": "background", "paint": {"background-color": "#DCE1E8"}},
        {"id": "tierra", "type": "fill", "source": "base", "source-layer": "earth", "paint": {"fill-color": "#F3F4F6"}},
        {"id": "parques", "type": "fill", "source": "base", "source-layer": "landuse",
         "filter": ["in", ["get", "kind"], ["literal", ["park", "forest", "wood", "nature_reserve", "grass"]]],
         "paint": {"fill-color": "#E4E9E1"}},
        {"id": "agua", "type": "fill", "source": "base", "source-layer": "water", "paint": {"fill-color": "#DCE1E8"}},
        {"id": "edificios", "type": "fill", "source": "base", "source-layer": "buildings", "minzoom": 14,
         "paint": {"fill-color": "#E6E8EC"}},
        {"id": "calles-borde", "type": "line", "source": "base", "source-layer": "roads",
         "filter": ["in", ["get", "kind"], ["literal", ["highway", "major_road"]]],
         "paint": {"line-color": "#D3D6DD", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 1.5, 16, 14]}},
        {"id": "calles", "type": "line", "source": "base", "source-layer": "roads",
         "filter": ["in", ["get", "kind"], ["literal", ["highway", "major_road", "minor_road"]]],
         "paint": {"line-color": "#FFFFFF", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.5, 16, 10]}},
        {"id": "ruta", "type": "line", "source": "ruta",
         "layout": {"line-cap": "round", "line-join": "round"},
         "paint": {"line-color": "#1F4FD1", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 3, 16, 8]}},
        {"id": "nombres-calles", "type": "symbol", "source": "base", "source-layer": "roads", "minzoom": 14,
         "layout": {"symbol-placement": "line", "text-field": ["get", "name"], "text-font": ["NotoSans"], "text-size": 12},
         "paint": {"text-color": "#4B5160", "text-halo-color": "#FFFFFF", "text-halo-width": 1.5}},
        {"id": "ciudades", "type": "symbol", "source": "base", "source-layer": "places",
         "filter": ["==", ["get", "kind"], "locality"],
         "layout": {"text-field": ["get", "name"], "text-font": ["NotoSans"], "text-size": 14},
         "paint": {"text-color": "#15171C", "text-halo-color": "#FFFFFF", "text-halo-width": 1.5}},
        {"id": "puntos", "type": "circle", "source": "puntos",
         "paint": {"circle-radius": 11, "circle-color": "#1F4FD1", "circle-stroke-color": "#FFFFFF", "circle-stroke-width": 2}},
        {"id": "numeros", "type": "symbol", "source": "puntos",
         "layout": {"text-field": ["get", "n"], "text-font": ["NotoSans"], "text-size": 12, "text-allow-overlap": true},
         "paint": {"text-color": "#FFFFFF"}}
      ]
    }
    """.trimIndent()
}
