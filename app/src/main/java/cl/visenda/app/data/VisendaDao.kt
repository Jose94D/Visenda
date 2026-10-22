package cl.visenda.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import cl.visenda.app.dominio.Datos
import cl.visenda.app.importar.Cambios

@Dao
interface VisendaDao {
    @Query("SELECT * FROM empresas") suspend fun empresas(): List<Empresa>
    @Query("SELECT * FROM ciclos") suspend fun ciclos(): List<Ciclo>
    @Query("SELECT * FROM categorias") suspend fun categorias(): List<Categoria>
    @Query("SELECT * FROM clientes ORDER BY nombre") suspend fun clientes(): List<Cliente>
    @Query("SELECT * FROM cartera") suspend fun cartera(): List<Cartera>
    @Query("SELECT * FROM cliente_campos") suspend fun campos(): List<ClienteCampo>
    @Query("SELECT * FROM lugares") suspend fun lugares(): List<Lugar>
    @Query("SELECT * FROM atenciones") suspend fun atenciones(): List<Atencion>
    @Query("SELECT * FROM horarios") suspend fun horarios(): List<Horario>
    @Query("SELECT * FROM visitas ORDER BY fechaHora DESC") suspend fun visitas(): List<Visita>
    @Query("SELECT * FROM visita_empresa") suspend fun visitaEmpresa(): List<VisitaEmpresa>

    @Transaction
    suspend fun todo() = Datos(
        empresas(), ciclos(), categorias(), clientes(), cartera(), campos(),
        lugares(), atenciones(), horarios(), visitas(), visitaEmpresa(),
    )

    @Insert suspend fun insertarVisita(visita: Visita): Long
    @Insert suspend fun insertarVisitaEmpresa(filas: List<VisitaEmpresa>)

    @Transaction
    suspend fun marcarVisita(visita: Visita, empresas: List<Long>) {
        val id = insertarVisita(visita)
        insertarVisitaEmpresa(empresas.map { VisitaEmpresa(id, it) })
    }

    // Solo para cargar los datos de ejemplo del prototipo.
    @Insert suspend fun empresas(f: List<Empresa>)
    @Insert suspend fun ciclos(f: List<Ciclo>)
    @Insert suspend fun categorias(f: List<Categoria>)
    @Insert suspend fun clientes(f: List<Cliente>)
    @Insert suspend fun cartera(f: List<Cartera>)
    @Insert suspend fun campos(f: List<ClienteCampo>)
    @Insert suspend fun lugares(f: List<Lugar>)
    @Insert suspend fun atenciones(f: List<Atencion>)
    @Insert suspend fun horarios(f: List<Horario>)
    @Insert suspend fun visitas(f: List<Visita>)
    @Insert suspend fun visitaEmpresa(f: List<VisitaEmpresa>)

    @Transaction
    suspend fun cargar(d: Datos) {
        empresas(d.empresas); ciclos(d.ciclos); categorias(d.categorias); clientes(d.clientes)
        cartera(d.cartera); campos(d.campos); lugares(d.lugares); atenciones(d.atenciones)
        horarios(d.horarios); visitas(d.visitas); visitaEmpresa(d.visitaEmpresa)
    }

    @Upsert suspend fun guardarEmpresa(e: Empresa)
    @Upsert suspend fun guardarCategorias(f: List<Categoria>)
    @Upsert suspend fun guardarClientes(f: List<Cliente>)
    @Upsert suspend fun guardarCartera(f: List<Cartera>)
    @Upsert suspend fun guardarLugares(f: List<Lugar>)
    @Upsert suspend fun guardarAtenciones(f: List<Atencion>)
    @Upsert suspend fun guardarMapeo(m: MapeoImport)
    @Query("SELECT * FROM mapeos_import WHERE empresaId = :empresa") suspend fun mapeo(empresa: Long): MapeoImport?
    @Query("DELETE FROM cliente_campos WHERE empresaId = :empresa AND clienteId IN (:clientes)") suspend fun borrarCampos(empresa: Long, clientes: List<Long>)
    @Query("DELETE FROM horarios WHERE atencionId IN (:atenciones)") suspend fun borrarHorarios(atenciones: List<Long>)
    @Query("DELETE FROM empresas WHERE id IN (:ids)") suspend fun borrarEmpresas(ids: List<Long>)
    @Query("DELETE FROM cartera WHERE empresaId = :empresa AND clienteId IN (:clientes)") suspend fun borrarDeCartera(empresa: Long, clientes: List<Long>)
    @Query("DELETE FROM clientes WHERE id NOT IN (SELECT clienteId FROM cartera)") suspend fun borrarClientesSinCartera()
    @Query("DELETE FROM lugares WHERE id NOT IN (SELECT lugarId FROM atenciones)") suspend fun borrarLugaresSinAtencion()
    /** Nunca pisa una ubicación ajustada a mano (RN-09). */
    @Query("UPDATE lugares SET lat = :lat, lng = :lng WHERE id = :id AND NOT corregidoManual") suspend fun ubicar(id: Long, lat: Double, lng: Double)

    @Query("UPDATE lugares SET lat = :lat, lng = :lng, corregidoManual = 1 WHERE id = :id")
    suspend fun corregirUbicacion(id: Long, lat: Double, lng: Double)

    /** Importación completa en una transacción: si algo falla no queda nada a medias (CA-6.5). */
    @Transaction
    suspend fun importar(c: Cambios, empresasABorrar: List<Long>) {
        if (empresasABorrar.isNotEmpty()) {
            borrarEmpresas(empresasABorrar)
            borrarClientesSinCartera()
            borrarLugaresSinAtencion()
        }
        guardarEmpresa(c.empresa)
        c.cicloNuevo?.let { ciclos(listOf(it)) }
        guardarCategorias(c.categorias)
        guardarClientes(c.clientes)
        guardarCartera(c.cartera)
        // De a 500: SQLite acepta como máximo 999 parámetros por consulta.
        c.camposDe.chunked(500).forEach { borrarCampos(c.empresa.id, it) }
        campos(c.campos)
        guardarLugares(c.lugares)
        guardarAtenciones(c.atenciones)
        c.horariosDe.chunked(500).forEach { borrarHorarios(it) }
        horarios(c.horarios)
        guardarMapeo(c.mapeo)
    }

    @Insert suspend fun insertarEmpresa(e: Empresa): Long
    @Query("DELETE FROM ciclos WHERE empresaId = :empresa") suspend fun borrarCiclos(empresa: Long)
    @Query("DELETE FROM categorias WHERE empresaId = :empresa AND id NOT IN (:conservar)") suspend fun borrarCategoriasMenos(empresa: Long, conservar: List<Long>)

    /**
     * Ajustes > Empresas y ciclos. Los ciclos se reemplazan (nada depende de ellos); las categorías se actualizan
     * por id para no perder la categoría de cada cliente, y las quitadas dejan a sus clientes con la frecuencia por defecto.
     */
    @Transaction
    suspend fun guardarEmpresaCompleta(e: Empresa, ciclos: List<Ciclo>, categorias: List<Categoria>): Long {
        val id = if (e.id == 0L) insertarEmpresa(e) else e.id.also { guardarEmpresa(e) }
        borrarCiclos(id)
        ciclos(ciclos.map { it.copy(id = 0, empresaId = id) })
        borrarCategoriasMenos(id, categorias.map { it.id }.filter { it != 0L })
        guardarCategorias(categorias.map { it.copy(empresaId = id) })
        return id
    }

    /** RN-12: la empresa con sus ciclos, categorías, cartera y campos, y los clientes que quedan sin ninguna cartera. */
    @Transaction
    suspend fun eliminarEmpresa(id: Long) {
        borrarEmpresas(listOf(id))
        borrarClientesSinCartera()
        borrarLugaresSinAtencion()
    }

    @Query("DELETE FROM clientes WHERE id = :id") suspend fun borrarCliente(id: Long)

    /** RN-12 / CA-5.4: en cascada se van cartera, campos, atenciones, horarios y visitas; luego los lugares que nadie más usa. */
    @Transaction
    suspend fun eliminarCliente(id: Long) {
        borrarCliente(id)
        borrarLugaresSinAtencion()
    }

    /** RN-09: el usuario decide quitar de la cartera a los que ya no vienen en el kardex; los que quedan sin cartera se borran (RN-12). */
    @Transaction
    suspend fun quitarDeCartera(empresa: Long, clientes: List<Long>) {
        clientes.chunked(500).forEach { borrarDeCartera(empresa, it) }
        borrarClientesSinCartera()
        borrarLugaresSinAtencion()
    }
}
