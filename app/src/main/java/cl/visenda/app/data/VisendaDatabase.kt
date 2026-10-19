package cl.visenda.app.data

import androidx.room.Database
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
abstract class VisendaDatabase : RoomDatabase()
