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
    /** Render de cuerpo entero que publica Blizzard, si lo tiene. */
    val renderUrl: String? = null,
    /** Recorte de cara, para listas. */
    val avatarUrl: String? = null,
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
    /** JSON: lista de achievement IDs completados. */
    val achievementIdsJson: String = "[]",
    /** JSON: lista de mount IDs de la colección. */
    val mountIdsJson: String = "[]",
    /**
     * JSON: lista de dificultades ("MYTHIC", "HEROIC", …) de los jefes de banda
     * matados DESPUÉS del último reset, según `last_kill_timestamp`. Es exacto:
     * no necesita snapshot previo para saber qué cayó esta semana.
     */
    val raidKillsThisWeekJson: String = "[]",
    /** JSON: niveles de llave de las M+ completadas desde el último reset. */
    val mythicLevelsThisWeekJson: String = "[]",
    /** Estadística "Delves totales completadas" (acumulada, no semanal). */
    val delvesCompletedTotal: Int = 0,
    /** JSON: estadísticas acumuladas que el catálogo pide seguir (id -> valor). */
    val statisticsJson: String = "{}",
    /**
     * El perfil de Blizzard todavía no refleja la semana en curso: o anuncia un
     * periodo de mítica+ anterior al vigente, o el personaje no se ha conectado
     * desde el último reset. Con esto puesto, un 0 no significa "no has hecho
     * nada" sino "Blizzard aún no lo sabe", y la UI tiene que decirlo.
     */
    val profileStale: Boolean = false,
)

/**
 * Misión que se ha demostrado REPETIBLE: estaba completada en un snapshot y
 * dejó de estarlo en otro posterior, así que Blizzard la reinició. Es la forma
 * de saber qué misiones son semanales sin depender de una lista curada a mano:
 * las que se aprenden aquí y aparecen completadas hoy son, necesariamente, cosas
 * hechas en el periodo actual.
 */
@Entity(tableName = "repeatable_quest")
data class RepeatableQuestEntity(
    @PrimaryKey val questId: Int,
    val learnedAt: Instant,
)

@Dao
interface RepeatableQuestDao {
    @Upsert
    suspend fun upsertAll(quests: List<RepeatableQuestEntity>)

    @Query("SELECT questId FROM repeatable_quest")
    suspend fun ids(): List<Int>

    @Query("SELECT COUNT(*) FROM repeatable_quest")
    suspend fun count(): Int
}

/**
 * Precio agregado de un objeto en la casa de subastas. El volcado que publica
 * Blizzard son 24 MB por región; aquí solo queda el resumen por objeto, que es
 * lo único que se consulta.
 *
 * [scope] es 0 para las mercancías de toda la región y el ID del reino
 * conectado para el resto: son dos mercados distintos y mezclarlos daría
 * precios que no existen en ninguno de los dos.
 */
@Entity(tableName = "auction_price", primaryKeys = ["scope", "itemId"])
data class AuctionPriceEntity(
    val scope: Int,
    val itemId: Int,
    val minUnitPrice: Long,
    val quantity: Long,
    val listings: Int,
    val updatedAt: Instant,
)

@Dao
interface AuctionPriceDao {
    @Upsert
    suspend fun upsertAll(prices: List<AuctionPriceEntity>)

    @Query("DELETE FROM auction_price WHERE scope = :scope")
    suspend fun clearScope(scope: Int)

    @Query("SELECT * FROM auction_price WHERE scope = :scope AND itemId IN (:itemIds)")
    suspend fun forItems(scope: Int, itemIds: List<Int>): List<AuctionPriceEntity>

    /**
     * Lo más caro que de verdad está en venta.
     *
     * Sin filtrar, esta lista era inútil: decenas de objetos empatados en el
     * tope de precio de la casa (9.999.999,99 de oro), que es lo que se pone
     * cuando se lista algo sin intención de venderlo. Se descartan los que
     * tocan el tope y los que tienen menos de tres subastas, para que un solo
     * precio absurdo no encabece la lista.
     */
    @Query(
        "SELECT * FROM auction_price WHERE scope = :scope AND minUnitPrice < :cap " +
            "AND listings >= :minListings ORDER BY minUnitPrice DESC LIMIT :limit",
    )
    suspend fun mostExpensive(
        scope: Int,
        limit: Int,
        cap: Long = PRICE_CAP_COPPER,
        minListings: Int = 3,
    ): List<AuctionPriceEntity>

    companion object {
        /** Tope de precio de una subasta: 9.999.999,99 de oro = 99.999.999.900 de cobre. */
        const val PRICE_CAP_COPPER = 99_999_999_900L
    }

    @Query("SELECT * FROM auction_price WHERE scope = :scope ORDER BY quantity DESC LIMIT :limit")
    suspend fun mostTraded(scope: Int, limit: Int): List<AuctionPriceEntity>

    @Query("SELECT MAX(updatedAt) FROM auction_price WHERE scope = :scope")
    suspend fun updatedAt(scope: Int): Instant?

    @Query("SELECT COUNT(*) FROM auction_price WHERE scope = :scope")
    suspend fun count(scope: Int): Int
}

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

    @Query("SELECT * FROM seasonal_goal WHERE rewardId = :rewardId")
    suspend fun get(rewardId: String): SeasonalGoalEntity?
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

    /**
     * Último snapshot ANTES de un instante. Es la línea base correcta para las
     * estadísticas acumuladas (Delves): el primer snapshot de la semana ya
     * incluiría lo hecho antes de sincronizar.
     */
    @Query("SELECT * FROM snapshot WHERE characterId = :characterId AND takenAt < :before ORDER BY takenAt DESC LIMIT 1")
    suspend fun lastBefore(characterId: Long, before: Instant): SnapshotEntity?

    /** Todos los snapshots del personaje, del más antiguo al más nuevo. */
    @Query("SELECT * FROM snapshot WHERE characterId = :characterId ORDER BY takenAt ASC")
    suspend fun allFor(characterId: Long): List<SnapshotEntity>

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
        RepeatableQuestEntity::class,
        AuctionPriceEntity::class,
    ],
    version = 10,
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
    abstract fun repeatableQuestDao(): RepeatableQuestDao
    abstract fun auctionPriceDao(): AuctionPriceDao
}
