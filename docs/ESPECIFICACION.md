# Visenda — Especificación

Versión 0.1 · octubre 2026 · Hito 0

App Android para visitadores (médicos u otros rubros similares) que importa su kardex desde Excel, controla las visitas por ciclo y empresa, y calcula la ruta óptima del día respetando los horarios de atención. Todo funciona dentro del teléfono, sin servidor.

Identificador de la app: `cl.visenda.app` (no se puede cambiar después de publicar en Play Store).

Mockup de referencia: https://claude.ai/artifact/AQm6RygXpB3NTb7iFzrJmS

---

## 1. Alcance

**Incluido (versión 1.0)**
- Importar el kardex desde `.xlsx`, con asignación de columnas.
- Clientes con varias oficinas y horarios distintos por oficina y día, incluido el horario partido.
- Varias empresas, cada una con sus ciclos, categorías y frecuencias.
- Estado de visitas por ciclo y aviso de clientes en riesgo.
- Planificación del día y ruta óptima offline.
- Mapas offline descargados por región de Chile.
- Respaldo cifrado transferible a otro teléfono.
- Funciones de privacidad: bloqueo, eliminación de datos, exportación de los datos de un cliente.

**Fuera de alcance (1.0)**
- Sincronización en la nube, cuentas de usuario, servidor propio.
- Navegación paso a paso: se delega a la app de mapas del teléfono.
- Tráfico en tiempo real.
- Versión web o iOS.

## 2. Glosario

| Término | Significado |
|---|---|
| Kardex | Planilla de clientes que entrega cada empresa al visitador |
| Empresa | Laboratorio o compañía para la que trabaja el visitador. Puede haber varias |
| Ciclo | Período fijado por una empresa (p. ej. 1–31 oct) en el que hay que visitar a toda la cartera |
| Cartera | Los clientes de una empresa. Un cliente puede estar en varias carteras |
| Lugar | Edificio o consulta con dirección y coordenadas |
| Atención | Un cliente atiende en un lugar, con sus tramos de horario |
| Frecuencia | Visitas requeridas por ciclo |
| Parada | Un lugar en la ruta. Puede incluir varios clientes |

## 3. Modelo de datos

Implementado en `app/src/main/java/cl/visenda/app/data/Entidades.kt`. El esquema exportado está en `app/schemas/`.

```
empresas ─┬─ ciclos
          ├─ categorias
          ├─ cartera ──────── clientes ─┬─ cliente_campos
          ├─ cliente_campos             ├─ atenciones ── horarios
          ├─ visita_empresa ── visitas ─┘       │
          └─ mapeos_import                    lugares
```

Convenciones:
- Fechas: días desde epoch (`LocalDate.toEpochDay()`).
- Horas del día: minutos desde las 00:00.
- Instantes: milisegundos epoch.
- Día de la semana: 1 = lunes … 7 = domingo.
- Borrar un cliente o una empresa borra en cascada todos sus datos dependientes.

## 4. Reglas de negocio

**RN-01 Frecuencia requerida.** Para un cliente en una empresa: `frecuenciaManual`; si no tiene, la frecuencia de su categoría; si tampoco, la frecuencia por defecto de la empresa.

**RN-02 Ciclo vigente.** El ciclo de la empresa con `inicio ≤ hoy ≤ fin`. Si no hay ninguno, los clientes de esa empresa se muestran "sin ciclo" y no generan avisos. Los ciclos de una misma empresa no pueden solaparse.

**RN-03 Visitas contadas.** Una visita cuenta para una empresa si está asociada a esa empresa (`visita_empresa`) y su fecha cae dentro del ciclo vigente.

**RN-04 Estado de un cliente en una empresa.**
- **Completo:** visitas contadas ≥ frecuencia requerida.
- **En riesgo:** le faltan visitas y además se cumple una de estas dos condiciones:
  - quedan ≤ N días hábiles en el ciclo (N configurable, por defecto 5);
  - los días que quedan en el ciclo en que el cliente atiende son menos que las visitas que le faltan.
- **Pendiente:** cualquier otro caso con visitas faltantes.
- Días hábiles: lunes a viernes, contando hoy.
- En listas que mezclan empresas, el cliente muestra su peor estado (en riesgo > pendiente > completo).

**RN-05 Marcar visita.**
- Crea una visita con la fecha y hora actuales y el lugar de la parada (o el elegido).
- Por defecto se asocia a todas las empresas en las que el cliente tiene visitas pendientes en el ciclo vigente. El usuario puede cambiarlo.
- Si el cliente ya tiene una visita hoy, se pide confirmación.
- Se puede deshacer durante 10 segundos y editar o borrar después.

**RN-06 Candidatos del día.** Clientes con visitas pendientes en algún ciclo vigente, que tienen coordenadas y al menos un tramo de horario hoy que termina después de la hora de salida.
- Orden: en riesgo primero; luego menos días restantes en que atiende; luego más visitas faltantes.
- Se preseleccionan los primeros 8.

**RN-07 Optimización de la ruta.**
- Datos de entrada: hora y punto de salida, fin de jornada, clientes elegidos y tiempo de visita por cliente (por defecto 15 minutos, configurable).
- Los clientes que atienden en el mismo lugar se agrupan en una sola parada.
- Si un cliente atiende en varios lugares hoy, el optimizador elige uno.
- Se puede llegar antes de que abra (se espera), pero la visita debe terminar antes del cierre: `llegada + duración ≤ cierre`.
- Objetivo: visitar a todos los elegidos; si no caben, maximizar la cantidad de clientes en riesgo y luego el total, y después minimizar la hora de término.
- Los clientes que no caben se informan con el motivo (horario o fin de jornada).
- Algoritmo exacto hasta 15 paradas; por encima, heurístico.
- Tiempos de viaje: los calcula el motor de rutas offline (Valhalla, en auto).

**RN-08 Horarios.**
- Cada tramo cumple `0 ≤ abre < cierra ≤ 1440`; no se aceptan tramos que crucen la medianoche.
- Los tramos de un mismo día y atención no pueden solaparse.
- Un horario que el intérprete no pudo leer queda como "por revisar", con el texto original. Esa atención no entra a la planificación hasta que se corrija.

**RN-09 Identificación al importar.**
- Orden de búsqueda: (1) `codigoKardex` en esa empresa, (2) RUT, (3) cliente nuevo.
- Si se crea un cliente nuevo con el mismo nombre que uno existente, se avisa del posible duplicado.
- La reimportación actualiza nombre, especialidad, categoría, campos y horarios.
- Nunca sobrescribe las coordenadas de un lugar con `corregidoManual`.
- Los clientes que ya no aparecen en el kardex nuevo se listan, y el usuario decide si los quita de la cartera. Nunca se borran automáticamente.

**RN-10 Lugares.**
- Dos direcciones iguales tras normalizarlas son el mismo lugar. Para normalizar: minúsculas, sin tildes, espacios simples y sin el sufijo de local u oficina.
- El sufijo (Local, Of., Depto., Piso) se quita para geocodificar, pero la dirección original se guarda tal cual.

**RN-11 Geocodificación.**
- Usa `android.location.Geocoder` con la dirección limpia más la comuna y ", Chile".
- Restringe la búsqueda al rectángulo de las regiones descargadas.
- **Solo se envía la dirección**: nunca el nombre ni el RUT.
- Si falla, el lugar queda "sin ubicación" y no entra a rutas hasta que el usuario lo ubique manualmente en el mapa.

**RN-12 Eliminación.**
- Eliminar un cliente borra todos sus datos (cartera, campos, atenciones, horarios y visitas).
- Eliminar una empresa borra sus ciclos, categorías, cartera, campos y asociaciones de visitas, y además los clientes que queden sin ninguna cartera.
- Ambas acciones piden confirmación escribiendo el nombre.

**RN-13 Respaldo.**
- Un archivo `.visenda` que contiene la base de datos y un manifiesto (versión de la app, versión del esquema, fecha).
- Cifrado con AES-256-GCM. La clave se deriva de la contraseña con PBKDF2 (mínimo 8 caracteres).
- Restaurar reemplaza todos los datos tras una confirmación.
- Un respaldo de un esquema anterior se migra con las migraciones de Room. Uno de un esquema más nuevo se rechaza con un mensaje para actualizar la app.
- Los mapas no se incluyen: solo la lista de regiones elegidas.

**RN-14 Privacidad.**
- Ningún dato del kardex sale del teléfono, salvo las direcciones enviadas al Geocoder (RN-11).
- Sin analítica ni reportes de errores con datos personales.
- Respaldo de Android desactivado (`allowBackup=false` y `dataExtractionRules`).

## 5. Pantallas y criterios de aceptación

### 5.1 Inicio
Muestra el avance de cada empresa en su ciclo vigente (visitas hechas / requeridas, días restantes), un aviso de clientes en riesgo, el acceso a "Planificar el día" y los cumpleaños de los próximos 7 días (si existe un campo de fecha llamado "cumpleaños" o "fecha de nacimiento").
- CA-1.1 Con dos empresas con ciclo vigente se ven dos tarjetas de avance con cifras que cumplen RN-03.
- CA-1.2 El aviso de riesgo muestra el número correcto de clientes según RN-04 y abre Clientes filtrado por "En riesgo".
- CA-1.3 Sin empresas, la pantalla muestra un estado vacío con la acción "Importar kardex".

### 5.2 Planificar el día
Hora de salida (por defecto 08:30 y la ubicación actual), fin de jornada (por defecto 18:00) y la lista de candidatos según RN-06, con casillas, estado, horario de hoy y cantidad de oficinas.
- CA-2.1 Solo aparecen clientes que cumplen RN-06, en el orden definido.
- CA-2.2 La barra inferior muestra cuántos clientes y paradas hay, e indica cuando varios comparten lugar.
- CA-2.3 "Calcular ruta" está deshabilitado sin clientes elegidos.

### 5.3 Ruta del día
Mapa offline con la ruta y las paradas numeradas, más una lista con la hora estimada de llegada a cada parada, los clientes de cada una y el horario hasta el que atienden.
- CA-3.1 Ninguna parada viola RN-07.
- CA-3.2 "Marcar visita" aplica RN-05 y la parada se muestra completada.
- CA-3.3 "Navegar" abre la app de mapas del teléfono con el destino.
- CA-3.4 Los clientes que no caben se listan con su motivo.
- CA-3.5 Funciona en modo avión si las regiones están descargadas.
- CA-3.6 El cálculo con 15 paradas termina en menos de 3 segundos en un teléfono de gama baja.

### 5.4 Clientes
Buscador (nombre, especialidad, comuna), filtros por estado y empresa, y la lista agrupada en "En riesgo", "Pendientes" y "Listos", con el avance de cada cliente (p. ej. 1/2).
- CA-4.1 La búsqueda ignora mayúsculas y tildes.
- CA-4.2 Con 500 clientes, la lista se desplaza con fluidez.

### 5.5 Perfil del cliente
Acciones (llamar, marcar visita, agregar a hoy), avance por empresa y categoría, oficinas con sus horarios, campos del kardex agrupados por empresa (con el cumpleaños destacado si es en los próximos 7 días) e historial de visitas.
- CA-5.1 Todo campo del kardex no asignado aparece en el perfil con su nombre de columna original.
- CA-5.2 Desde el perfil se pueden editar los horarios y corregir la ubicación.
- CA-5.3 "Exportar datos de este cliente" genera un archivo con todos sus datos (derecho de acceso).
- CA-5.4 "Eliminar cliente" aplica RN-12.

### 5.6 Importar kardex (3 pasos)
1. Elegir el archivo y la empresa (existente o nueva).
2. Asignar cada columna: Nombre, RUT, Código, Especialidad, Dirección, Comuna, Nombre del lugar, Horario, Categoría, Campo del perfil o No importar. El último mapeo de la empresa se propone por defecto.
3. Revisar: cuántos clientes nuevos, actualizados y sin cambios; horarios por revisar; direcciones sin ubicar; posibles duplicados.

Antes de importar hay que marcar la casilla de autorización.
- CA-6.1 Un kardex con una fila por cliente y oficina se agrupa por RUT o código en un solo cliente con varias atenciones.
- CA-6.2 Los formatos de horario soportados (Anexo A) se interpretan sin errores; el resto queda "por revisar" (RN-08).
- CA-6.3 Reimportar el mismo archivo no crea duplicados ni borra visitas.
- CA-6.4 Las columnas "No importar" no se guardan en ninguna parte.
- CA-6.5 Un archivo dañado o que no sea `.xlsx` muestra un error claro y no modifica datos.

### 5.7 Ubicar en el mapa
Mapa centrado en el resultado del Geocoder (o en la comuna) con un pin arrastrable y el botón "Confirmar ubicación".
- CA-7.1 Al confirmar, se guardan las coordenadas con `corregidoManual = true`.

### 5.8 Ajustes
- **Mapas sin conexión:** lista de las 16 regiones con su estado (descargada, descargando, disponible), tamaño, fecha de actualización y espacio usado; opción "Solo con Wi‑Fi".
- **Empresas y ciclos:** crear y editar empresas, ciclos, categorías y frecuencias; eliminar empresa (RN-12).
- **Planificación:** tiempo de visita por defecto y días de aviso de riesgo (N).
- **Seguridad:** bloqueo con huella o PIN; crear y restaurar respaldo (RN-13).
- **Legal:** política de privacidad y términos.

Criterios:
- CA-8.1 Una descarga interrumpida se reanuda sin empezar de cero.
- CA-8.2 Con el bloqueo activo, la app pide huella o PIN al abrirse y al volver tras 5 minutos en segundo plano.
- CA-8.3 Un respaldo creado en el teléfono A, restaurado en el teléfono B, deja los mismos clientes, horarios y visitas.

## 6. Requisitos no funcionales

| Área | Requisito |
|---|---|
| Plataforma | Android 8.0 (API 26) o superior |
| Conectividad | Todo funciona sin internet, salvo la geocodificación al importar y la descarga de mapas |
| Rendimiento | 500 clientes y 1.000 lugares sin pérdida de fluidez; importar 500 filas en menos de 30 s, sin contar la geocodificación |
| Almacenamiento | Base cifrada (SQLCipher) desde el hito 4 |
| Accesibilidad | Zonas pulsables de 44 dp o más, contraste mínimo 4,5:1, etiquetas para TalkBack |
| Idioma | Español (Chile) |
| Costo | Sin servidores ni servicios pagados |

## 7. Decisiones pendientes

- ¿Los sábados cuentan como días hábiles en algún ciclo? Hoy no.
- Tiempo de visita por defecto: 15 minutos (a validar con visitadores en el hito 2).

## Anexo A — Formatos de horario soportados (versión inicial)

| Texto en el kardex | Interpretación |
|---|---|
| `Lun-Vie 9:00-13:00` | Lunes a viernes, 09:00–13:00 |
| `L a V 15 a 19` | Lunes a viernes, 15:00–19:00 |
| `Lun, Mié 9-13 / 15-19` | Lunes y miércoles, dos tramos |
| `Martes 08:30 - 12:30` | Martes, 08:30–12:30 |

Cualquier otro formato queda "por revisar". La lista se amplía con los kardex reales de la beta.
