package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

@Entity(tableName = "estado_programa")
data class EstadoPrograma(@PrimaryKey val id: Int = 1, val sincronizadoEn: Long)

@Dao
abstract class CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    abstract fun observar(): Flow<List<Charla>>

    @Query("SELECT EXISTS(SELECT 1 FROM estado_programa WHERE id = 1)")
    abstract fun hayEdicion(): Flow<Boolean>

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    abstract suspend fun alternar(id: String)

    @Query("UPDATE charlas SET titulo = :titulo, tema = :tema, dia = :dia, hora = :hora, " +
        "sala = :sala, ponente = :ponente, resumen = :resumen WHERE id = :id")
    protected abstract suspend fun actualizar(id: String, titulo: String, tema: String, dia: Int,
        hora: String, sala: String, ponente: String, resumen: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertar(charla: Charla)

    @Query("SELECT id FROM charlas")
    protected abstract suspend fun ids(): List<String>

    @Query("DELETE FROM charlas WHERE id IN (:ids)")
    protected abstract suspend fun borrar(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun guardarEstado(estado: EstadoPrograma)

    /** Aplica una instantánea completa del programa, todo en una sola transacción. */
    @Transaction
    open suspend fun aplicarInstantanea(fichas: List<Charla>, ahora: Long) {
        for (f in fichas) {
            val n = actualizar(f.id, f.titulo, f.tema, f.dia, f.hora, f.sala, f.ponente, f.resumen)
            if (n == 0) insertar(f.copy(favorita = false))
        }
        val vigentes = fichas.map { it.id }.toSet()
        ids().filter { it !in vigentes }.chunked(500).forEach { borrar(it) }
        guardarEstado(EstadoPrograma(1, ahora))
    }
}

@Database(entities = [Charla::class, EstadoPrograma::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}

val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `estado_programa` " +
            "(`id` INTEGER NOT NULL, `sincronizadoEn` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT OR IGNORE INTO estado_programa (id, sincronizadoEn) " +
            "SELECT 1, CAST(strftime('%s','now') AS INTEGER) * 1000 FROM charlas LIMIT 1")
    }
}
