package dev.ykro.trailaid.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** The incident journal: what happened, what the agent did, and the times that matter for rescuers. */
@Entity(tableName = "incidents")
data class IncidentEntity(
  @PrimaryKey val sessionId: String,
  val startedAtEpochMs: Long,
  val closedAtEpochMs: Long,
  val protocols: String, // comma separated skill names
  val timers: String, // "name@HH:mm" lines
  val location: String?,
  val called: Boolean,
  val smsSent: Boolean,
  val summary: String,
)

@Dao
interface IncidentDao {
  @Insert suspend fun insert(item: IncidentEntity)
  @Query("SELECT * FROM incidents ORDER BY startedAtEpochMs DESC") fun observeAll(): Flow<List<IncidentEntity>>
  @Query("DELETE FROM incidents") suspend fun clear()
}

@Database(entities = [IncidentEntity::class], version = 1, exportSchema = true)
abstract class IncidentDatabase : RoomDatabase() {
  abstract fun incidents(): IncidentDao

  companion object {
    fun create(context: Context): IncidentDatabase = Room.databaseBuilder(context, IncidentDatabase::class.java, "trail_aid.db").build()
  }
}
