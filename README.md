# Visenda

App Android para visitadores en terreno (visitadores médicos y oficios similares). Importa el kardex de clientes desde Excel, controla las visitas de cada ciclo por empresa y calcula la ruta óptima del día según los horarios de atención de cada cliente.

Funciona **100% dentro del teléfono**: sin servidor, sin cuentas y sin que los datos salgan del dispositivo.

> **Estado:** en desarrollo, Hito 0 (proyecto base). La app todavía no tiene funciones de usuario.

## Funciones previstas

- **Importar el kardex** desde `.xlsx`, asignando cada columna. Las columnas extra (cumpleaños, teléfono, etc.) aparecen en el perfil del cliente.
- **Clientes con varias oficinas**, cada una con su horario, incluido el horario partido.
- **Ciclos por empresa**, con frecuencias por categoría (A/B/C) y avisos de clientes en riesgo antes de que termine el ciclo.
- **Varias empresas a la vez**: una misma visita puede contar para más de una.
- **Planificar el día**: sugiere a quién visitar y calcula el orden óptimo respetando los horarios de atención.
- **Mapas offline** descargables por región de Chile.
- **Respaldo cifrado** para traspasar los datos a otro teléfono.

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
| Rutas offline | Valhalla (planificado) |
| Mapa | MapLibre con archivos offline por región (planificado) |
| Optimizador | Propio en Kotlin, con ventanas de tiempo (planificado) |
| Android mínimo | 8.0 (API 26) |

## Compilar

Requisitos: Android Studio reciente (incluye el JDK y el SDK de Android con la API 37).

**Desde Android Studio:** *File → Open*, elige la carpeta del proyecto y presiona ▶ (Run).

**Desde la terminal:**

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

Cada push compila el APK automáticamente en GitHub Actions; se descarga desde la pestaña **Actions**.

## Estructura

```
app/src/main/java/cl/visenda/app/
├── MainActivity.kt          Punto de entrada y tema
└── data/
    ├── Entidades.kt         Modelo de datos (12 tablas)
    └── VisendaDatabase.kt   Base de datos Room
app/schemas/                 Esquemas de Room versionados (para migraciones)
docs/
├── ESPECIFICACION.md        Pantallas, reglas de negocio y criterios de aceptación
└── Plan-de-desarrollo.pdf   Hitos y calendario
```

## Documentación

- [Especificación](docs/ESPECIFICACION.md)
- [Plan de desarrollo](docs/Plan-de-desarrollo.pdf)
