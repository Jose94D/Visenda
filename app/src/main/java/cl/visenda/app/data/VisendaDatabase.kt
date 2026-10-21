package cl.visenda.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Subirla junto con cada migración: un respaldo de un esquema más nuevo que este se rechaza (RN-13). */
const val VERSION_ESQUEMA = 1
const val NOMBRE_BASE = "visenda.db"

@Database(
    entities = [
        Empresa::class, Ciclo::class, Categoria::class,
        Cliente::class, Cartera::class, ClienteCampo::class,
        Lugar::class, Atencion::class, Horario::class,
        Visita::class, VisitaEmpresa::class, MapeoImport::class,
    ],
    version = VERSION_ESQUEMA,
    exportSchema = true,
)
abstract class VisendaDatabase : RoomDatabase() {
    abstract fun dao(): VisendaDao

    companion object {
        @Volatile private var instancia: VisendaDatabase? = null

        fun get(context: Context): VisendaDatabase = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(context.applicationContext, VisendaDatabase::class.java, NOMBRE_BASE)
                .build().also { instancia = it }
        }

        /** Para reemplazar el archivo al restaurar un respaldo. El próximo get() la vuelve a abrir. */
        fun cerrar() = synchronized(this) { instancia?.close(); instancia = null }
    }
}
