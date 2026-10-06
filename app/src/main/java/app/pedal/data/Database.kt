package app.pedal.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startTime: Long,
    val endTime: Long? = null,
    val distanceM: Double = 0.0,
    val movingTimeMs: Long = 0,
    val elapsedMs: Long = 0,
    val maxSpeedMps: Double = 0.0,
    val elevGainM: Double = 0.0,
    val elevLossM: Double = 0.0,
    /** Downsampled, encoded polyline used for list thumbnails. */
    val preview: String = "",
) {
    val avgSpeedMps: Double get() = if (movingTimeMs > 0) distanceM / (movingTimeMs / 1000.0) else 0.0
}

@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(
        entity = RideEntity::class,
        parentColumns = ["id"],
        childColumns = ["rideId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("rideId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    /** Increments on every manual pause/resume so the track is split into segments. */
    val segment: Int,
    val lat: Double,
    val lon: Double,
    val ele: Double?,
    val time: Long,
    val speed: Float,
    val accuracy: Float,
)

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val distanceM: Double,
    val elevGainM: Double,
    val elevLossM: Double,
    val pointCount: Int,
    val hasElevation: Boolean,
    val preview: String,
)

@Entity(
    tableName = "route_points",
    foreignKeys = [ForeignKey(
        entity = RouteEntity::class,
        parentColumns = ["id"],
        childColumns = ["routeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("routeId")],
)
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val idx: Int,
    val lat: Double,
    val lon: Double,
    val ele: Double?,
)

@Dao
interface RideDao {
    @Insert
    suspend fun insert(ride: RideEntity): Long

    @Update
    suspend fun update(ride: RideEntity)

    @Query("SELECT * FROM rides WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    fun observeFinished(): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides WHERE id = :id")
    fun observe(id: Long): Flow<RideEntity?>

    @Query("SELECT * FROM rides WHERE id = :id")
    suspend fun get(id: Long): RideEntity?

    @Query("SELECT * FROM rides WHERE endTime IS NULL")
    suspend fun unfinished(): List<RideEntity>

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE rides SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Insert
    suspend fun insertPoint(point: TrackPointEntity)

    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY time, id")
    suspend fun points(rideId: Long): List<TrackPointEntity>
}

@Dao
interface RouteDao {
    @Insert
    suspend fun insert(route: RouteEntity): Long

    @Insert
    suspend fun insertPoints(points: List<RoutePointEntity>)

    @Transaction
    suspend fun insertWithPoints(route: RouteEntity, points: List<RoutePointEntity>): Long {
        val id = insert(route)
        insertPoints(points.map { it.copy(routeId = id) })
        return id
    }

    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    fun observe(id: Long): Flow<RouteEntity?>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun get(id: Long): RouteEntity?

    @Query("SELECT * FROM route_points WHERE routeId = :routeId ORDER BY idx")
    suspend fun points(routeId: Long): List<RoutePointEntity>

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE routes SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
}

@Database(
    entities = [RideEntity::class, TrackPointEntity::class, RouteEntity::class, RoutePointEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rides(): RideDao
    abstract fun routes(): RouteDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pedal.db").build()
    }
}
