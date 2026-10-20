package cl.visenda.app.importar

import cl.visenda.app.data.Atencion
import cl.visenda.app.data.Cartera
import cl.visenda.app.data.Categoria
import cl.visenda.app.data.Ciclo
import cl.visenda.app.data.Cliente
import cl.visenda.app.data.ClienteCampo
import cl.visenda.app.data.Empresa
import cl.visenda.app.data.Horario
import cl.visenda.app.data.Lugar
import cl.visenda.app.data.MapeoImport
import cl.visenda.app.dominio.Datos
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class Rol(val texto: String) {
    NOMBRE("Nombre"), RUT("RUT"), CODIGO("Código"), ESPECIALIDAD("Especialidad"), DIRECCION("Dirección"),
    COMUNA("Comuna"), LUGAR("Nombre del lugar"), HORARIO("Horario"), CATEGORIA("Categoría"),
    CAMPO("Campo del perfil"), NO_IMPORTAR("No importar"),
}

/** Lo que cambia en la base al importar. Los ids nuevos ya vienen asignados para enlazar las tablas. */
data class Cambios(
    val empresa: Empresa,
    val cicloNuevo: Ciclo?,
    val categorias: List<Categoria>,
    val clientes: List<Cliente>,
    val cartera: List<Cartera>,
    /** Clientes cuyos campos de esta empresa se reemplazan por [campos]. */
    val camposDe: List<Long>,
    val campos: List<ClienteCampo>,
    val lugares: List<Lugar>,
    val atenciones: List<Atencion>,
    /** Atenciones cuyos horarios se reemplazan por [horarios]. */
    val horariosDe: List<Long>,
    val horarios: List<Horario>,
    val mapeo: MapeoImport,
    val resumen: Resumen,
)

data class Resumen(
    val nuevos: Int,
    val actualizados: Int,
    val sinCambios: Int,
    val filasSinNombre: Int,
    /** "Nombre: texto original" de los horarios que el intérprete no entendió. */
    val porRevisar: List<String>,
    val lugaresNuevos: Int,
    val posiblesDuplicados: List<String>,
    /** Clientes de la cartera de esta empresa que ya no vienen en el kardex. Nunca se borran solos (RN-09). */
    val yaNoEstan: List<Cliente>,
)

private val PISTAS = listOf(
    Rol.RUT to listOf("rut"),
    Rol.CODIGO to listOf("codigo", "cod", "id"),
    Rol.DIRECCION to listOf("direccion", "domicilio"), // antes que LUGAR: "Dirección consulta" es la dirección
    Rol.LUGAR to listOf("lugar", "centro", "clinica", "consulta", "establecimiento", "institucion", "edificio"),
    Rol.ESPECIALIDAD to listOf("especialidad"),
    Rol.COMUNA to listOf("comuna", "ciudad"),
    Rol.HORARIO to listOf("horario"),
    Rol.CATEGORIA to listOf("categoria", "cat", "clasificacion", "segmento"),
    Rol.NOMBRE to listOf("nombre", "medico", "doctor", "cliente", "profesional"),
)

/** Propone un rol por columna: el mapeo guardado de la empresa (por nombre de columna) o una pista por el encabezado. */
fun proponerMapeo(encabezados: List<String>, guardado: Map<String, Rol> = emptyMap()): List<Rol> {
    val usados = mutableSetOf<Rol>()
    return encabezados.map { e ->
        val palabras = normalizar(e).split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }
        val rol = guardado[e] ?: PISTAS.firstOrNull { (rol, pistas) -> rol !in usados && palabras.any { it in pistas } }?.first ?: Rol.CAMPO
        if (rol != Rol.CAMPO && rol != Rol.NO_IMPORTAR && !usados.add(rol)) Rol.CAMPO else rol
    }
}

fun mapeoATexto(encabezados: List<String>, roles: List<Rol>) = encabezados.zip(roles).joinToString("\n") { (e, r) -> "${e.replace('\n', ' ')}\t${r.name}" }

fun mapeoDeTexto(texto: String): Map<String, Rol> = texto.lines().mapNotNull { l ->
    val (e, r) = l.split('\t').takeIf { it.size == 2 } ?: return@mapNotNull null
    Rol.entries.firstOrNull { it.name == r }?.let { e to it }
}.toMap()

fun normalizarRut(rut: String) = rut.filter { it.isLetterOrDigit() }.uppercase().trimStart('0').ifEmpty { null }

/** RN-10: mismo edificio = misma dirección sin tildes, mayúsculas, espacios dobles ni el sufijo de local u oficina. */
fun claveLugar(direccion: String) = normalizar(limpiarDireccion(direccion)).replace(Regex("""\s+"""), " ")

/** Quita en memoria las empresas indicadas y lo que queda huérfano, igual que el borrado en cascada (RN-12). */
fun Datos.sinEmpresas(ids: Set<Long>): Datos {
    if (ids.isEmpty()) return this
    val cartera = cartera.filter { it.empresaId !in ids }
    val clientes = clientes.filter { c -> cartera.any { it.clienteId == c.id } }
    val idsClientes = clientes.map { it.id }.toSet()
    val atenciones = atenciones.filter { it.clienteId in idsClientes }
    val idsAtenciones = atenciones.map { it.id }.toSet()
    val visitas = visitas.filter { it.clienteId in idsClientes }
    val idsVisitas = visitas.map { it.id }.toSet()
    return Datos(
        empresas.filter { it.id !in ids }, ciclos.filter { it.empresaId !in ids }, categorias.filter { it.empresaId !in ids },
        clientes, cartera, campos.filter { it.empresaId !in ids && it.clienteId in idsClientes },
        lugares.filter { l -> atenciones.any { it.lugarId == l.id } }, atenciones, horarios.filter { it.atencionId in idsAtenciones },
        visitas, visitaEmpresa.filter { it.empresaId !in ids && it.visitaId in idsVisitas },
    )
}

private class Fila(val celdas: List<String>, val roles: List<Rol>) {
    fun de(rol: Rol) = roles.indexOf(rol).takeIf { it >= 0 }?.let { celdas.getOrNull(it)?.trim() }?.ifEmpty { null }
}

/**
 * Prepara la importación del kardex de [empresa] (id 0 = empresa nueva) sin tocar la base.
 * filas[0] son los encabezados. Una fila por cliente y oficina se agrupa por RUT, código o nombre (CA-6.1).
 */
fun prepararImportacion(datos: Datos, filas: List<List<String>>, roles: List<Rol>, empresa: Empresa, hoy: LocalDate): Cambios {
    require(Rol.NOMBRE in roles) { "Falta la columna con el nombre del cliente." }
    val encabezados = filas.first()
    val siguiente = object {
        var empresa = (datos.empresas.maxOfOrNull { it.id } ?: 0) + 1
        var ciclo = (datos.ciclos.maxOfOrNull { it.id } ?: 0) + 1
        var categoria = (datos.categorias.maxOfOrNull { it.id } ?: 0) + 1
        var cliente = (datos.clientes.maxOfOrNull { it.id } ?: 0) + 1
        var lugar = (datos.lugares.maxOfOrNull { it.id } ?: 0) + 1
        var atencion = (datos.atenciones.maxOfOrNull { it.id } ?: 0) + 1
    }
    val emp = if (empresa.id == 0L) empresa.copy(id = siguiente.empresa++) else empresa
    // shortcut: la empresa nueva parte con un ciclo del mes en curso; editar ciclos llega con Ajustes > Empresas y ciclos.
    val cicloNuevo = if (empresa.id == 0L) Ciclo(
        siguiente.ciclo++, emp.id, "Ciclo " + hoy.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-CL")),
        hoy.withDayOfMonth(1).toEpochDay(), hoy.withDayOfMonth(hoy.lengthOfMonth()).toEpochDay(),
    ) else null

    val todas = filas.drop(1).filter { f -> f.any { it.isNotBlank() } }.map { Fila(it, roles) }
    val conNombre = todas.filter { it.de(Rol.NOMBRE) != null }
    val grupos = conNombre.groupBy { f ->
        f.de(Rol.RUT)?.let(::normalizarRut)?.let { "rut:$it" } ?: f.de(Rol.CODIGO)?.let { "cod:$it" } ?: "nom:" + normalizar(f.de(Rol.NOMBRE)!!)
    }.values

    val categorias = datos.categorias.filter { it.empresaId == emp.id }.toMutableList()
    val categoriasNuevas = mutableListOf<Categoria>()
    val lugares = datos.lugares.associateBy { claveLugar(it.direccion) }.toMutableMap()
    val lugaresNuevos = mutableListOf<Lugar>()
    val atencionesNuevas = mutableListOf<Atencion>()
    val clientes = mutableListOf<Cliente>()
    val cartera = mutableListOf<Cartera>()
    val campos = mutableListOf<ClienteCampo>()
    val horariosDe = mutableListOf<Long>()
    val horarios = mutableListOf<Horario>()
    val porRevisar = mutableListOf<String>()
    val duplicados = mutableListOf<String>()
    val vistos = mutableSetOf<Long>()
    var nuevos = 0
    var actualizados = 0
    var sinCambios = 0

    grupos.forEach { g ->
        fun primero(rol: Rol) = g.firstNotNullOfOrNull { it.de(rol) }
        val nombre = primero(Rol.NOMBRE)!!
        val rut = primero(Rol.RUT)
        val codigo = primero(Rol.CODIGO)

        // RN-09: código en esta empresa, luego RUT, luego cliente nuevo.
        val existente = codigo?.let { c -> datos.cartera.firstOrNull { it.empresaId == emp.id && it.codigoKardex == c } }
            ?.let { cart -> datos.clientes.firstOrNull { it.id == cart.clienteId } }
            ?: rut?.let(::normalizarRut)?.let { r -> datos.clientes.firstOrNull { it.rut?.let(::normalizarRut) == r } }
        if (existente == null && datos.clientes.any { normalizar(it.nombre) == normalizar(nombre) }) duplicados += nombre
        val cliente = (existente ?: Cliente(siguiente.cliente++, null, nombre, null))
            .let { it.copy(nombre = nombre, rut = it.rut ?: rut, especialidad = primero(Rol.ESPECIALIDAD) ?: it.especialidad) }
        existente?.let { vistos += it.id }

        val categoria = primero(Rol.CATEGORIA)?.let { nom ->
            categorias.firstOrNull { normalizar(it.nombre) == normalizar(nom) }
                ?: Categoria(siguiente.categoria++, emp.id, nom, emp.frecuenciaDefecto).also { categorias += it; categoriasNuevas += it }
        }
        val anterior = datos.cartera.firstOrNull { it.clienteId == cliente.id && it.empresaId == emp.id }
        val cart = Cartera(cliente.id, emp.id, categoria?.id ?: anterior?.categoriaId, anterior?.frecuenciaManual, codigo ?: anterior?.codigoKardex)

        val susCampos = roles.indices.filter { roles[it] == Rol.CAMPO }.mapNotNull { i ->
            g.firstNotNullOfOrNull { it.celdas.getOrNull(i)?.trim()?.ifEmpty { null } }?.let { ClienteCampo(cliente.id, emp.id, encabezados[i].trim(), valorCampo(encabezados[i], it)) }
        }

        // Atenciones: una por dirección distinta (RN-10). Los horarios de un mismo lugar se suman.
        val porLugar = g.filter { it.de(Rol.DIRECCION) != null }.groupBy { claveLugar(direccionCompleta(it)) }
        val susHorariosAntes = mutableListOf<Horario>()
        val susHorarios = mutableListOf<Horario>()
        var atencionNueva = false
        porLugar.forEach { (clave, deLugar) ->
            val lugar = lugares[clave] ?: Lugar(siguiente.lugar++, deLugar.firstNotNullOfOrNull { it.de(Rol.LUGAR) }, direccionCompleta(deLugar[0]), null, null)
                .also { lugares[clave] = it; lugaresNuevos += it }
            val atencion = datos.atenciones.firstOrNull { it.clienteId == cliente.id && it.lugarId == lugar.id }
                ?: atencionesNuevas.firstOrNull { it.clienteId == cliente.id && it.lugarId == lugar.id }
                ?: Atencion(siguiente.atencion++, cliente.id, lugar.id).also { atencionesNuevas += it; atencionNueva = true }
            val textos = deLugar.mapNotNull { it.de(Rol.HORARIO) }
            if (textos.isEmpty()) return@forEach
            horariosDe += atencion.id
            susHorariosAntes += datos.horarios.filter { it.atencionId == atencion.id }
            val tramos = interpretarHorario(textos.distinct().joinToString("; "))
            susHorarios += if (tramos != null) {
                tramos.map { Horario(0, atencion.id, it.dia, it.abre, it.cierra) }
            } else {
                porRevisar += "$nombre: ${textos.joinToString(" / ")}"
                listOf(Horario(0, atencion.id, 0, 0, 0, porRevisar = true, textoOriginal = textos.joinToString(" / ")))
            }
        }

        clientes += cliente
        cartera += cart
        campos += susCampos
        horarios += susHorarios
        when {
            existente == null -> nuevos++
            existente == cliente && anterior == cart && !atencionNueva &&
                datos.campos.filter { it.clienteId == cliente.id && it.empresaId == emp.id }.toSet() == susCampos.toSet() &&
                susHorariosAntes.map { it.copy(id = 0) }.toSet() == susHorarios.toSet() -> sinCambios++
            else -> actualizados++
        }
    }

    return Cambios(
        empresa = emp, cicloNuevo = cicloNuevo, categorias = categoriasNuevas, clientes = clientes, cartera = cartera,
        camposDe = clientes.map { it.id }, campos = campos, lugares = lugaresNuevos, atenciones = atencionesNuevas,
        horariosDe = horariosDe, horarios = horarios, mapeo = MapeoImport(emp.id, mapeoATexto(encabezados, roles)),
        resumen = Resumen(
            nuevos, actualizados, sinCambios, todas.size - conNombre.size, porRevisar, lugaresNuevos.size, duplicados,
            yaNoEstan = datos.cartera.filter { it.empresaId == emp.id && it.clienteId !in vistos }.mapNotNull { c -> datos.clientes.firstOrNull { it.id == c.clienteId } },
        ),
    )
}

/** Dirección tal como viene, con la comuna al final si viene en otra columna y no está ya escrita. */
private fun direccionCompleta(f: Fila): String {
    val direccion = f.de(Rol.DIRECCION)!!
    val comuna = f.de(Rol.COMUNA) ?: return direccion
    return if (normalizar(direccion).endsWith(normalizar(comuna))) direccion else "$direccion, $comuna"
}

private val DD_MM_AAAA = DateTimeFormatter.ofPattern("dd-MM-yyyy")

/** Excel guarda las fechas como número de días: en columnas de fecha se convierten a dd-mm-aaaa. */
private fun valorCampo(encabezado: String, valor: String): String {
    val esFecha = normalizar(encabezado).let { "fecha" in it || "cumple" in it || "nacimiento" in it }
    val serie = valor.toDoubleOrNull()
    return if (esFecha && serie != null && serie in 1.0..80_000.0) LectorXlsx.fechaExcel(valor)!!.format(DD_MM_AAAA) else valor
}
