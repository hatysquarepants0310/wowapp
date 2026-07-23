package com.azeroth.companion.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}

@Entity(tableName = "task_state", primaryKeys = ["taskId", "characterId"])
data class TaskStateEntity(
    val taskId: String,
    val characterId: Long,
    val completions: Int,
    val confidence: String,
    val manualOverride: Boolean,
    val updatedAt: Instant,
    /** Reset (epoch) al que pertenece este estado; al cambiar de semana se ignora. */
    val periodStart: Instant,
)

@Entity(tableName = "calibration_observation")
data class CalibrationObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val observedAt: Instant,
)

@Entity(tableName = "character")
data class CharacterEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val realmSlug: String,
    val realmName: String,
    val region: String,
    val faction: String,
    val playableClass: String,
    val activeSpec: String?,
    val level: Int,
    val averageItemLevel: Int,
    val equippedItemLevel: Int,
    val isMain: Boolean,
    val isInactive: Boolean = false,
    val lastLogin: Instant?,
    val lastSyncedAt: Instant?,
)

/** Objetivos de temporada marcados por el usuario (§8.2). */
@Entity(tableName = "seasonal_goal")
data class SeasonalGoalEntity(
    @PrimaryKey val rewardId: String,
    val targeted: Boolean,
    val obtained: Boolean = false,
    val updatedAt: Instant = Instant.EPOCH,
)

/** Estado de progresión editable por personaje (§7.3): Folio, Presas, Delves, monedas. */
@Entity(tableName = "progression_state")
data class ProgressionStateEntity(
    @PrimaryKey val characterId: Long,
    val folioUnlockedRows: Int = 0,
    val folioCatchUpPending: Int = 0,
    /** JSON: mapa zona -> % de progreso de la presa (0..100). */
    val preyProgressJson: String = "{}",
    val delveKeysAvailable: Int = 0,
    val delvesDoneThisWeek: Int = 0,
    val crestsThisWeek: Int = 0,
    val crestsTotal: Int = 0,
    val updatedAt: Instant = Instant.EPOCH,
)

/** Snapshot inmutable por sync (§6): permite detectar "hecho esta semana" por delta. */
@Entity(tableName = "snapshot")
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: Long,
    val takenAt: Instant,
    /** JSON: lista de quest IDs completadas. */
    val completedQuestIdsJson: String,
    /** JSON: mapa factionId -> standing total. */
    val reputationsJson: String,
    /** Runs de M+ de la semana según la API. */
    val mythicPlusRunsThisWeek: Int,
    /** JSON: mapa instanceId -> kills desde el último reset. */
    val raidKillsJson: String,
)

@Dao
interface TaskStateDao {
    @Upsert
    suspend fun upsert(state: TaskStateEntity)

    @Query("SELECT * FROM task_state WHERE characterId = :characterId")
    fun observeForCharacter(characterId: Long): Flow<List<TaskStateEntity>>

    @Query("SELECT * FROM task_state WHERE characterId = :characterId AND taskId = :taskId")
    suspend fun get(characterId: Long, taskId: String): TaskStateEntity?
}

@Dao
interface CalibrationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(observation: CalibrationObservationEntity)

    @Query("SELECT * FROM calibration_observation WHERE eventId = :eventId ORDER BY observedAt DESC LIMIT 10")
    suspend fun latestFor(eventId: String): List<CalibrationObservationEntity>
}

@Dao
interface CharacterDao {
    @Upsert
    suspend fun upsert(character: CharacterEntity)

    @Query("SELECT * FROM character WHERE isInactive = 0 ORDER BY isMain DESC, name")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("UPDATE character SET isInactive = 1 WHERE id = :id")
    suspend fun markInactive(id: Long)
}

@Dao
interface SeasonalGoalDao {
    @Upsert
    suspend fun upsert(goal: SeasonalGoalEntity)

    @Query("SELECT * FROM seasonal_goal")
    fun observeAll(): Flow<List<SeasonalGoalEntity>>

    @Query("SELECT * FROM seasonal_goal WHERE targeted = 1 AND obtained = 0")
    suspend fun pendingTargets(): List<SeasonalGoalEntity>
}

@Dao
interface ProgressionDao {
    @Upsert
    suspend fun upsert(state: ProgressionStateEntity)

    @Query("SELECT * FROM progression_state WHERE characterId = :characterId")
    fun observe(characterId: Long): Flow<ProgressionStateEntity?>

    @Query("SELECT * FROM progression_state WHERE characterId = :characterId")
    suspend fun get(characterId: Long): ProgressionStateEntity?
}

@Dao
interface SnapshotDao {
    @Insert
    suspend fun insert(snapshot: SnapshotEntity)

    @Query("SELECT * FROM snapshot WHERE characterId = :characterId AND takenAt >= :after ORDER BY takenAt ASC LIMIT 1")
    suspend fun firstAfter(characterId: Long, after: Instant): SnapshotEntity?

    @Query("SELECT * FROM snapshot WHERE characterId = :characterId ORDER BY takenAt DESC LIMIT 1")
    suspend fun latest(characterId: Long): SnapshotEntity?

    @Query("DELETE FROM snapshot WHERE takenAt < :before")
    suspend fun pruneOlderThan(before: Instant)
}

@Database(
    entities = [
        TaskStateEntity::class,
        CalibrationObservationEntity::class,
        CharacterEntity::class,
        SnapshotEntity::class,
        ProgressionStateEntity::class,
        SeasonalGoalEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskStateDao(): TaskStateDao
    abstract fun calibrationDao(): CalibrationDao
    abstract fun characterDao(): CharacterDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun progressionDao(): ProgressionDao
    abstract fun seasonalGoalDao(): SeasonalGoalDao
}
