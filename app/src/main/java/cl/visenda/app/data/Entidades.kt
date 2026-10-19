package cl.visenda.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey

// Convenciones: fechas = días desde epoch (LocalDate.toEpochDay), horas del día = minutos desde 00:00,
// instantes = milisegundos epoch. Borrar un cliente o una empresa borra en cascada sus datos (derecho de supresión).

@Entity(tableName = "empresas")
data class Empresa(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val frecuenciaDefecto: Int = 1,
)

@Entity(
    tableName = "ciclos",
    foreignKeys = [ForeignKey(entity = Empresa::class, parentColumns = ["id"], childColumns = ["empresaId"], onDelete = CASCADE)],
    indices = [Index("empresaId")],
)
data class Ciclo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val empresaId: Long,
    val nombre: String,
    val inicio: Long,
    val fin: Long,
)

@Entity(
    tableName = "categorias",
    foreignKeys = [ForeignKey(entity = Empresa::class, parentColumns = ["id"], childColumns = ["empresaId"], onDelete = CASCADE)],
    indices = [Index("empresaId")],
)
data class Categoria(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val empresaId: Long,
    val nombre: String,
    val frecuencia: Int,
)

@Entity(tableName = "clientes", indices = [Index(value = ["rut"], unique = true)])
data class Cliente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rut: String?,
    val nombre: String,
    val especialidad: String?,
)

/** Vincula un cliente con una empresa: un mismo médico puede estar en la cartera de varias. */
@Entity(
    tableName = "cartera",
    primaryKeys = ["clienteId", "empresaId"],
    foreignKeys = [
        ForeignKey(entity = Cliente::class, parentColumns = ["id"], childColumns = ["clienteId"], onDelete = CASCADE),
        ForeignKey(entity = Empresa::class, parentColumns = ["id"], childColumns = ["empresaId"], onDelete = CASCADE),
        ForeignKey(entity = Categoria::class, parentColumns = ["id"], childColumns = ["categoriaId"], onDelete = SET_NULL),
    ],
    indices = [Index("empresaId"), Index("categoriaId")],
)
data class Cartera(
    val clienteId: Long,
    val empresaId: Long,
    val categoriaId: Long?,
    val frecuenciaManual: Int?,
    /** Código del cliente en el kardex de esa empresa, para reimportar sin duplicar. */
    val codigoKardex: String?,
)

/** Columnas del kardex sin campo propio (cumpleaños, teléfono, etc.). */
@Entity(
    tableName = "cliente_campos",
    primaryKeys = ["clienteId", "empresaId", "campo"],
    foreignKeys = [
        ForeignKey(entity = Cliente::class, parentColumns = ["id"], childColumns = ["clienteId"], onDelete = CASCADE),
        ForeignKey(entity = Empresa::class, parentColumns = ["id"], childColumns = ["empresaId"], onDelete = CASCADE),
    ],
    indices = [Index("empresaId")],
)
data class ClienteCampo(
    val clienteId: Long,
    val empresaId: Long,
    val campo: String,
    val valor: String,
)

/** Un edificio o consulta. Se geocodifica una sola vez aunque atiendan varios clientes. */
@Entity(tableName = "lugares")
data class Lugar(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String?,
    val direccion: String,
    val lat: Double?,
    val lng: Double?,
    /** Si el usuario movió el pin, una reimportación no debe pisar las coordenadas. */
    val corregidoManual: Boolean = false,
)

/** "El cliente X atiende en el lugar Y". */
@Entity(
    tableName = "atenciones",
    foreignKeys = [
        ForeignKey(entity = Cliente::class, parentColumns = ["id"], childColumns = ["clienteId"], onDelete = CASCADE),
        ForeignKey(entity = Lugar::class, parentColumns = ["id"], childColumns = ["lugarId"], onDelete = CASCADE),
    ],
    indices = [Index(value = ["clienteId", "lugarId"], unique = true), Index("lugarId")],
)
data class Atencion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clienteId: Long,
    val lugarId: Long,
)

/** Un tramo de horario. Horario partido = varias filas el mismo día. */
@Entity(
    tableName = "horarios",
    foreignKeys = [ForeignKey(entity = Atencion::class, parentColumns = ["id"], childColumns = ["atencionId"], onDelete = CASCADE)],
    indices = [Index("atencionId")],
)
data class Horario(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atencionId: Long,
    /** 1 = lunes … 7 = domingo (java.time.DayOfWeek). */
    val diaSemana: Int,
    val abre: Int,
    val cierra: Int,
    /** El intérprete no pudo leer el texto del kardex; el usuario debe completarlo. */
    val porRevisar: Boolean = false,
    val textoOriginal: String? = null,
)

@Entity(
    tableName = "visitas",
    foreignKeys = [
        ForeignKey(entity = Cliente::class, parentColumns = ["id"], childColumns = ["clienteId"], onDelete = CASCADE),
        ForeignKey(entity = Lugar::class, parentColumns = ["id"], childColumns = ["lugarId"], onDelete = SET_NULL),
    ],
    indices = [Index("clienteId"), Index("lugarId"), Index("fechaHora")],
)
data class Visita(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clienteId: Long,
    val lugarId: Long?,
    val fechaHora: Long,
    val notas: String?,
)

/** Para qué empresas cuenta una visita. */
@Entity(
    tableName = "visita_empresa",
    primaryKeys = ["visitaId", "empresaId"],
    foreignKeys = [
        ForeignKey(entity = Visita::class, parentColumns = ["id"], childColumns = ["visitaId"], onDelete = CASCADE),
        ForeignKey(entity = Empresa::class, parentColumns = ["id"], childColumns = ["empresaId"], onDelete = CASCADE),
    ],
    indices = [Index("empresaId")],
)
data class VisitaEmpresa(
    val visitaId: Long,
    val empresaId: Long,
)

/** Mapeo de columnas del kardex guardado por empresa (JSON), para no repetirlo en cada importación. */
@Entity(
    tableName = "mapeos_import",
    foreignKeys = [ForeignKey(entity = Empresa::class, parentColumns = ["id"], childColumns = ["empresaId"], onDelete = CASCADE)],
)
data class MapeoImport(
    @PrimaryKey val empresaId: Long,
    val configuracion: String,
)
