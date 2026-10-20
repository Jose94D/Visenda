package cl.visenda.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Empresa::class, Ciclo::class, Categoria::class,
        Cliente::class, Cartera::class, ClienteCampo::class,
        Lugar::class, Atencion::class, Horario::class,
        Visita::class, VisitaEmpresa::class, MapeoImport::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VisendaDatabase : RoomDatabase() {
    abstract fun dao(): VisendaDao

    companion object {
        @Volatile private var instancia: VisendaDatabase? = null

        fun get(context: Context): VisendaDatabase = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(context.applicationContext, VisendaDatabase::class.java, "visenda.db")
                .build().also { instancia = it }
        }
    }
}
