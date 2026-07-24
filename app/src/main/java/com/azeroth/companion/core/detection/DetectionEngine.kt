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
)

data class DetectionResult(val completions: Int, val confidence: Confidence)

class DetectionEngine {

    fun evaluate(rule: DetectionRule, baseline: SnapshotView?, current: SnapshotView?): DetectionResult =
        when (rule) {
            is DetectionRule.QuestCompleted -> {
                val done = current?.completedQuestIds?.count { it in rule.questIds } ?: 0
                val before = baseline?.completedQuestIds?.count { it in rule.questIds } ?: 0
                estimated(((done - before).coerceAtLeast(0)) * rule.countsAs)
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
