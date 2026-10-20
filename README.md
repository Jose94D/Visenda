# Visenda

App Android para visitadores en terreno (visitadores médicos y oficios similares). Importa el kardex de clientes desde Excel, controla las visitas de cada ciclo por empresa y calcula la ruta óptima del día según los horarios de atención de cada cliente.

Funciona **100% dentro del teléfono**: sin servidor, sin cuentas y sin que los datos salgan del dispositivo. Rutas y mapas también son offline.

> **Estado:** Hito 2 terminado. El prototipo es navegable con datos de ejemplo y calcula rutas reales sin internet; está en validación con visitadores. Sigue el Hito 3 (optimizador e importador completo).
>
> Avance del proyecto: [tablero en Trello](https://trello.com/b/AAhJ7hXT/visenda).

## Capturas

Prototipo del Hito 2 con datos de ejemplo (clientes ficticios).

| Inicio | Clientes | Perfil |
|:---:|:---:|:---:|
| <img src="docs/capturas/inicio.png" width="240" alt="Inicio: avance del ciclo por empresa y clientes en riesgo"> | <img src="docs/capturas/clientes.png" width="240" alt="Clientes agrupados por estado: en riesgo, pendientes y completos"> | <img src="docs/capturas/perfil.png" width="240" alt="Perfil del cliente con avance por empresa, oficinas y horarios"> |
| Avance del ciclo por empresa y alertas de riesgo | Clientes ordenados por estado | Avance por empresa, oficinas con su horario y datos del kardex |

| Planificar | Ruta | Ajustes |
|:---:|:---:|:---:|
| <img src="docs/capturas/planificar.png" width="240" alt="Planificar: clientes sugeridos según riesgo y horario"> | <img src="docs/capturas/ruta.png" width="240" alt="Ruta del día sobre el mapa offline con horas de llegada"> | <img src="docs/capturas/ajustes.png" width="240" alt="Ajustes: empresas, ciclos, mapas sin conexión y planificación"> |
| Sugerencias según riesgo y horario de atención | Ruta en mapa offline, con llegada y espera en cada parada | Empresas, ciclos y mapas descargados |

## Funciones

**Ya funcionan (prototipo):**

- **Ciclos por empresa**, con frecuencias por categoría y aviso de clientes en riesgo antes del cierre del ciclo.
- **Varias empresas a la vez**: una misma visita puede contar para más de una.
- **Clientes con varias oficinas**, cada una con su horario, incluido el horario partido.
- **Planificar el día**: sugiere a quién visitar y agrupa en una parada a los clientes del mismo lugar cuando sus horarios lo permiten. Después de las 18:00 planifica el día hábil siguiente.
- **Ruta y mapa offline** con Valhalla y MapLibre (probado en Biobío y Ñuble).
- **Perfil del cliente** con oficinas, horarios, datos extra del kardex (cumpleaños, teléfono…) e historial de visitas.

**Próximos hitos:**

- Optimizador de ruta con ventanas de tiempo (hoy el orden es por horario de apertura).
- Importador completo del kardex `.xlsx`, asignando cada columna (el lector ya está hecho).
- Geocodificación de direcciones con corrección manual en el mapa.
- Descarga de mapas por región de Chile.
- Respaldo cifrado para traspasar los datos a otro teléfono y bloqueo con huella.

## Privacidad

Los datos del kardex son personales y confidenciales, por eso:

- Se guardan solo en el teléfono. El respaldo automático de Android está desactivado (`allowBackup="false"` y `dataExtractionRules`).
- Lo único que sale del dispositivo son las direcciones que se envían al geocodificador de Android al importar, nunca nombres ni RUT.
- No hay analítica ni reportes de errores con datos personales.

Diseñado considerando la Ley 21.719 de protección de datos personales de Chile.

## Tecnología

| Pieza | Elección |
|---|---|
| Lenguaje e interfaz | Kotlin + Jetpack Compose (Material 3) |
| Base de datos | Room (SQLite) |
| Rutas offline | Valhalla (valhalla-mobile), teselas por región |
| Mapa | MapLibre con archivo PMTiles offline (Protomaps) |
| Excel | Lector `.xlsx` propio, sin dependencias |
| Optimizador | Propio en Kotlin, con ventanas de tiempo (Hito 3) |
| Android mínimo | 8.0 (API 26) |

## Compilar

Requisitos: Android Studio reciente (incluye el JDK y el SDK de Android con la API 37).

**Desde Android Studio:** *File → Open*, elige la carpeta del proyecto y presiona ▶ (Run).

**Desde la terminal:**

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Las pruebas se corren con `./gradlew test`.

Cada push compila el APK automáticamente en GitHub Actions; se descarga desde la pestaña **Actions**. El flujo manual **Generar datos de rutas** construye las teselas de Valhalla para todo Chile y las corta por región.

> Los mapas y teselas de prueba no están en el repositorio: se copian al teléfono en `Android/data/cl.visenda.app/files/`.

## Estructura

```
app/src/main/java/cl/visenda/app/
├── MainActivity.kt          Punto de entrada
├── data/                    Modelo de datos (12 tablas), Room y datos de ejemplo
├── dominio/                 Reglas de negocio: ciclos, estados, agrupación de paradas
├── importar/                Lector de Excel (.xlsx)
├── ui/                      Navegación, tema, mapa offline y estado de la app
│   └── pantallas/           Inicio, Clientes, Perfil, Planificar, Ruta, Ajustes
└── lab/                     Pantallas de prueba del Hito 1
app/schemas/                 Esquemas de Room versionados (para migraciones)
herramientas/                Corte de teselas de rutas por región
docs/
├── ESPECIFICACION.md        Pantallas, reglas de negocio y criterios de aceptación
├── Plan-de-desarrollo.pdf   Hitos y calendario
└── capturas/                Imágenes de este README
```

## Documentación

- [Especificación](docs/ESPECIFICACION.md)
- [Plan de desarrollo](docs/Plan-de-desarrollo.pdf)
- [Tablero de avance (Trello)](https://trello.com/b/AAhJ7hXT/visenda)
