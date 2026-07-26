package com.azeroth.companion.core.loot

import com.azeroth.companion.core.catalog.LootRules
import com.azeroth.companion.core.model.Confidence

/**
 * Probabilidad de conseguir un objeto concreto en un intento.
 *
 * Blizzard no publica tasas de caída. Lo único público es la tabla de botín de
 * cada jefe (Game Data journal), así que la app calcula lo que se puede calcular
 * y dice de dónde sale cada número en lugar de fingir precisión.
 */
sealed interface DropChance {
    val confidence: Confidence

    /** Recompensa garantizada (misión con recompensa fija, primera vez, etc.). */
    data object Guaranteed : DropChance {
        override val confidence = Confidence.CONFIRMED
    }

    /** El jugador elige entre [options] recompensas: cae la que elija. */
    data class Choice(val options: Int) : DropChance {
        override val confidence = Confidence.CONFIRMED
    }

    /**
     * Estimación a partir de la tabla del jefe: [itemsPerKill] objetos por
     * intento repartidos entre [tableSize] posibles.
     */
    data class FromTable(val itemsPerKill: Double, val tableSize: Int) : DropChance {
        override val confidence = Confidence.ESTIMATED
    }

    /** Tirada propia y baja (monturas): Blizzard no la publica. */
    data class RareRoll(val approximatePercent: Double) : DropChance {
        override val confidence = Confidence.ESTIMATED
    }

    /** Tasa medida por la comunidad, con prioridad sobre las estimaciones. */
    data class Measured(val percent: Double) : DropChance {
        override val confidence = Confidence.CONFIRMED
    }

    /** No hay forma honesta de estimarlo. */
    data class Unknown(val reason: String) : DropChance {
        override val confidence = Confidence.PREDICTED
    }
}

object DropChanceCalculator {

    /** Porcentaje, o null cuando no se puede estimar. */
    fun percent(chance: DropChance): Double? = when (chance) {
        DropChance.Guaranteed -> 100.0
        is DropChance.Choice -> if (chance.options <= 0) null else 100.0 / chance.options
        is DropChance.FromTable ->
            if (chance.tableSize <= 0) null
            else (chance.itemsPerKill / chance.tableSize * 100).coerceAtMost(100.0)
        is DropChance.RareRoll -> chance.approximatePercent
        is DropChance.Measured -> chance.percent
        is DropChance.Unknown -> null
    }

    /** Explicación de una línea: el usuario debe poder juzgar el número. */
    fun explain(chance: DropChance): String = when (chance) {
        DropChance.Guaranteed -> "Recompensa garantizada."
        is DropChance.Choice -> "Eliges 1 de ${chance.options} recompensas."
        is DropChance.FromTable ->
            "Estimado: la tabla del jefe tiene ${chance.tableSize} objetos y " +
                "caen ~${trim(chance.itemsPerKill)} por intento."
        is DropChance.RareRoll ->
            "Tirada aparte de la tabla del jefe. Blizzard no publica la tasa; " +
                "el orden de magnitud observado es ~${trim(chance.approximatePercent)} %."
        is DropChance.Measured -> "Tasa medida por la comunidad."
        is DropChance.Unknown -> chance.reason
    }

    /**
     * Probabilidad de un objeto de la tabla de un jefe. Las monturas no salen de
     * la tabla: son una tirada independiente, así que mezclarlas daría un número
     * absurdamente alto (1/13 en vez de ~1 %).
     */
    fun forBossItem(
        itemId: Int,
        tableSize: Int,
        isMount: Boolean,
        isRaid: Boolean,
        isFinalBoss: Boolean,
        rules: LootRules,
    ): DropChance {
        rules.knownDropRates[itemId.toString()]?.let { return DropChance.Measured(it) }
        if (isMount) return DropChance.RareRoll(rules.mountDropPercentEstimate)
        if (tableSize <= 0) return DropChance.Unknown("Sin tabla de botín publicada para este jefe.")
        val perKill = if (isRaid) {
            rules.raidItemsPerKill
        } else {
            rules.dungeonItemsPerKill + if (isFinalBoss) rules.dungeonFinalBossBonusItems else 0.0
        }
        return DropChance.FromTable(perKill, tableSize)
    }

    /**
     * Probabilidad de haber conseguido ya el objeto tras [attempts] intentos:
     * 1 - (1 - p)^n. Sirve para poner en contexto la mala suerte ("llevas 40
     * intentos, el 33 % de la gente ya lo tendría"). Es el único número de
     * probabilidad que la app puede calcular sobre datos REALES del jugador,
     * porque los intentos salen de sus propias muertes de jefe.
     */
    fun cumulative(chance: DropChance, attempts: Int): Double? {
        val p = percent(chance)?.div(100.0) ?: return null
        if (attempts <= 0 || p <= 0.0) return null
        return (1 - Math.pow(1 - p, attempts.toDouble())) * 100
    }

    private fun trim(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format("%.1f", value)
}
