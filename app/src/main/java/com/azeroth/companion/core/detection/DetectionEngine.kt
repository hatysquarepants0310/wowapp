package com.azeroth.companion.core.detection

import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.DetectionRule

/**
 * Datos normalizados de un personaje para evaluar reglas de detección (§6).
 * [baseline] es el primer snapshot posterior al último reset; [current] el más
 * reciente. La comparación entre ambos permite inferir "hecho esta semana"
 * aunque la API solo entregue estado acumulado.
 */
data class SnapshotView(
    val completedQuestIds: Set<Int>,
    val reputations: Map<Int, Int>,
    val mythicPlusRunsThisWeek: Int,
    val raidKills: Map<Int, Int>,
    val currencies: Map<Int, Int> = emptyMap(),
    val achievementIds: Set<Int> = emptySet(),
    val mountIds: Set<Int> = emptySet(),
    /** Jefes de banda derrotados DESPUÉS del reset (fechados por la API). */
    val raidBossKillsThisWeek: Int = 0,
    /** Delves de la semana, por diferencia con el snapshot previo al reset. */
    val delvesThisWeek: Int = 0,
    /** Misiones repetibles APRENDIDAS que ahora figuran completadas. */
    val repeatableQuestsDoneThisWeek: Int = 0,
    /** Jefes de mundo derrotados tras el reset (instancias de un solo jefe). */
    val worldBossKillsThisWeek: Int = 0,
    /** Estadísticas acumuladas y su valor en el snapshot previo al reset. */
    val statistics: Map<Int, Int> = emptyMap(),
    val statisticsBeforeReset: Map<Int, Int> = emptyMap(),
    /**
     * Misiones completadas según el último snapshot ANTERIOR al reset semanal.
     * `null` significa que no existe tal snapshot (instalación reciente), no
     * que el personaje no tuviera ninguna: la diferencia importa, porque sin
     * esa lectura no se puede afirmar que algo se hizo esta semana.
     */
    val questsBeforeReset: Set<Int>? = null,
)

data class DetectionResult(val completions: Int, val confidence: Confidence)

class DetectionEngine {

    fun evaluate(rule: DetectionRule, baseline: SnapshotView?, current: SnapshotView?): DetectionResult =
        when (rule) {
            // Presencia ABSOLUTA, no diferencia contra la línea base. Blizzard
            // borra la marca de completada de las misiones repetibles en cada
            // reset: verificado con personajes reales, alguien que terminó toda
            // Dragonflight no tiene ninguna de las "Aiding the Accord" en
            // /quests/completed. Que aparezca ya significa "hecha esta semana".
            // Con el cálculo por diferencia, quien sincronizaba por primera vez
            // después de hacer la misión veía siempre 0 (el snapshot base y el
            // actual eran el mismo).
            is DetectionRule.QuestCompleted -> {
                val now = current?.completedQuestIds.orEmpty()
                val before = current?.questsBeforeReset
                when {
                    // Con una lectura anterior al reset la respuesta es exacta,
                    // valga la misión para una semana o para siempre.
                    before != null -> {
                        val done = (now - before).count { it in rule.questIds }
                        DetectionResult(
                            (done * rule.countsAs).coerceAtLeast(0),
                            if (done > 0) Confidence.CONFIRMED else Confidence.ESTIMATED,
                        )
                    }
                    // Sin esa lectura, solo las repetibles admiten conclusión:
                    // si está marcada es que se hizo tras el último reset.
                    rule.repeatable -> estimated(now.count { it in rule.questIds } * rule.countsAs)
                    // Series rotatorias sin línea base: no se puede saber si esa
                    // semana concreta se hizo hace un mes o ayer.
                    else -> DetectionResult(0, Confidence.PREDICTED)
                }
            }

            is DetectionRule.QuestDelta -> {
                val delta = newQuests(baseline, current).count { it in rule.questIds }
                estimated(delta)
            }

            is DetectionRule.ReputationGain -> {
                val gained = (current?.reputations?.get(rule.factionId) ?: 0) -
                    (baseline?.reputations?.get(rule.factionId) ?: 0)
                estimated(if (gained >= rule.minDelta) gained / rule.minDelta else 0)
            }

            is DetectionRule.AchievementCriteria -> {
                // Logro completo presente en el perfil → tarea hecha. El matching por
                // criterio individual requiere metadatos estáticos; el logro entero basta
                // para la mayoría de semanales con logro asociado.
                val done = current?.achievementIds?.contains(rule.achievementId) == true
                val before = baseline?.achievementIds?.contains(rule.achievementId) == true
                estimated(if (done && !before) 1 else 0)
            }

            is DetectionRule.MythicPlusRuns ->
                estimated((current?.mythicPlusRunsThisWeek ?: 0).takeIf { it >= rule.minRuns } ?: 0)

            is DetectionRule.RaidBossKills ->
                estimated((current?.raidKills?.get(rule.instanceId) ?: 0).takeIf { it >= rule.minKills } ?: 0)

            is DetectionRule.CurrencyThreshold ->
                estimated(if ((current?.currencies?.get(rule.currencyId) ?: 0) >= rule.amount) 1 else 0)

            is DetectionRule.ActivityThisWeek -> {
                val value = when (rule.activity) {
                    com.azeroth.companion.core.model.WeeklyActivityKind.MYTHIC_PLUS ->
                        current?.mythicPlusRunsThisWeek ?: 0
                    com.azeroth.companion.core.model.WeeklyActivityKind.RAID_BOSS ->
                        current?.raidBossKillsThisWeek ?: 0
                    com.azeroth.companion.core.model.WeeklyActivityKind.WORLD_BOSS ->
                        current?.worldBossKillsThisWeek ?: 0
                    com.azeroth.companion.core.model.WeeklyActivityKind.DELVE ->
                        current?.delvesThisWeek ?: 0
                    com.azeroth.companion.core.model.WeeklyActivityKind.REPEATABLE_QUEST ->
                        current?.repeatableQuestsDoneThisWeek ?: 0
                }
                estimated(if (value >= rule.min) value else 0)
            }

            is DetectionRule.StatisticDelta -> {
                val before = current?.statisticsBeforeReset?.get(rule.statisticId)
                val now = current?.statistics?.get(rule.statisticId)
                // Sin lectura previa al reset no se puede afirmar nada: un 0 aquí
                // sería inventarse un dato, no medirlo.
                if (before == null || now == null) {
                    DetectionResult(0, Confidence.PREDICTED)
                } else {
                    val delta = (now - before).coerceAtLeast(0)
                    estimated(if (delta >= rule.min) delta else 0)
                }
            }

            DetectionRule.ManualOnly -> DetectionResult(0, Confidence.PREDICTED)

            is DetectionRule.AnyOf -> rule.rules
                .map { evaluate(it, baseline, current) }
                .maxByOrNull { it.completions }
                ?: DetectionResult(0, Confidence.PREDICTED)

            is DetectionRule.AllOf -> {
                val results = rule.rules.map { evaluate(it, baseline, current) }
                if (results.all { it.completions > 0 }) {
                    DetectionResult(results.minOf { it.completions }, Confidence.ESTIMATED)
                } else {
                    DetectionResult(0, Confidence.PREDICTED)
                }
            }
        }

    private fun estimated(completions: Int) = DetectionResult(
        completions = completions.coerceAtLeast(0),
        confidence = if (completions > 0) Confidence.ESTIMATED else Confidence.PREDICTED,
    )

    private fun newQuests(baseline: SnapshotView?, current: SnapshotView?): Set<Int> {
        val base = baseline?.completedQuestIds ?: emptySet()
        val now = current?.completedQuestIds ?: emptySet()
        return now - base
    }
}
