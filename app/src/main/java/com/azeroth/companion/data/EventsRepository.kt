package com.azeroth.companion.data

import com.azeroth.companion.core.catalog.CatalogRepository
import com.azeroth.companion.core.database.CalibrationDao
import com.azeroth.companion.core.database.CalibrationObservationEntity
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.EventCadence
import com.azeroth.companion.core.model.EventOccurrence
import com.azeroth.companion.core.model.WorldEventDefinition
import com.azeroth.companion.core.notifications.AlarmScheduler
import com.azeroth.companion.core.notifications.NotificationChannels
import com.azeroth.companion.core.notifications.NotificationId
import com.azeroth.companion.core.time.AnchorCalibrator
import com.azeroth.companion.core.time.DefaultEventScheduler
import com.azeroth.companion.core.time.ResetClock
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventsRepository @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val calibrationDao: CalibrationDao,
    private val alarmScheduler: AlarmScheduler,
    private val calibrator: AnchorCalibrator,
) {

    suspend fun events(): List<WorldEventDefinition> =
        catalogRepository.load().worldEvents.map(::applyCalibration)

    suspend fun resetClock(): ResetClock {
        val settings = settingsRepository.settings.first()
        catalogRepository.load()
        val rules = catalogRepository.resetRulesFor(settings.region)
            ?: error("El catálogo no define resets para ${settings.region}")
        return ResetClock(rules)
    }

    suspend fun scheduler(): DefaultEventScheduler =
        DefaultEventScheduler(realmZone())

    suspend fun realmZone(): ZoneId = resetClock().realmZone

    suspend fun upcoming(from: Instant, hours: Long = 24): List<EventOccurrence> {
        val scheduler = scheduler()
        return events()
            .flatMap { scheduler.occurrencesInRange(it, from..from.plus(Duration.ofHours(hours))) }
            .sortedBy { it.startsAt }
    }

    suspend fun nextOccurrence(from: Instant): Pair<WorldEventDefinition, EventOccurrence>? {
        val scheduler = scheduler()
        return events()
            .mapNotNull { def -> scheduler.nextOccurrence(def, from)?.let { def to it } }
            .minByOrNull { it.second.startsAt }
    }

    /** "El evento acaba de empezar" (§4.4): registra observación y recalibra. */
    suspend fun recordEventStartObservation(eventId: String, at: Instant = Instant.now()) {
        calibrationDao.insert(CalibrationObservationEntity(eventId = eventId, observedAt = at))
    }

    /** Reprograma los avisos del próximo evento según preferencias (§5.2). */
    suspend fun rescheduleEventAlarms() {
        val settings = settingsRepository.settings.first()
        val now = Instant.now()
        val scheduler = scheduler()
        events().forEach { def ->
            val occ = scheduler.nextOccurrence(def, now) ?: return@forEach
            val name = def.name["es_MX"] ?: def.name.values.firstOrNull() ?: def.id
            alarmScheduler.schedule(
                NotificationId("prewarn_long", def.id),
                occ.startsAt.minus(Duration.ofMinutes(settings.prewarnLongMinutes.toLong())),
                name,
                "Empieza en ${settings.prewarnLongMinutes} min · ${def.zone}",
                NotificationChannels.EVENTS,
            )
            alarmScheduler.schedule(
                NotificationId("prewarn_short", def.id),
                occ.startsAt.minus(Duration.ofMinutes(settings.prewarnShortMinutes.toLong())),
                name,
                "¡Empieza en ${settings.prewarnShortMinutes} min! Colócate en posición.",
                NotificationChannels.EVENTS,
            )
            alarmScheduler.schedule(
                NotificationId("event_start", def.id),
                occ.startsAt,
                name,
                "El evento acaba de empezar.",
                NotificationChannels.EVENTS,
            )
        }
        val reset = resetClock().nextWeeklyReset(now)
        alarmScheduler.schedule(
            NotificationId("weekly_reset_soon", "reset"),
            reset.minus(Duration.ofHours(settings.resetWarnHours.toLong())),
            "Reset semanal",
            "El reset es en ${settings.resetWarnHours} h. Revisa tus pendientes.",
            NotificationChannels.RESETS,
        )
        alarmScheduler.schedule(
            NotificationId("weekly_reset_now", "reset"),
            reset,
            "Reset semanal",
            "Nueva semana de juego. Bóveda lista para reclamar.",
            NotificationChannels.RESETS,
        )
    }

    private fun applyCalibration(def: WorldEventDefinition): WorldEventDefinition {
        val cadence = def.cadence as? EventCadence.FixedInterval ?: return def
        val observations = calibrationCache[def.id] ?: return def
        val result = calibrator.calibrate(cadence, observations)
        return if (result.calibratedAnchor != null) {
            def.copy(
                cadence = cadence.copy(anchorUtc = result.calibratedAnchor),
                defaultConfidence = Confidence.CONFIRMED,
            )
        } else def
    }

    private var calibrationCache: Map<String, List<Instant>> = emptyMap()

    /** Refresca la caché de observaciones (llamar antes de calcular ocurrencias). */
    suspend fun refreshCalibrations() {
        calibrationCache = catalogRepository.load().worldEvents.associate { def ->
            def.id to calibrationDao.latestFor(def.id).map { it.observedAt }
        }
    }
}
