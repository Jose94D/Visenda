package cl.visenda.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import cl.visenda.app.dominio.Datos

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
}
