package com.an0obis.magicduel.game

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class AvatarKind { DUMMY, MAGE }

/**
 * An opponent on the ladder. Dummies never fire. Mages repeat a visible, random
 * sequence of [Rules.BOT_SEQUENCE_LENGTH] shots: cast for [castTime], then wait [idleTime].
 */
data class Opponent(
    val name: String,
    val kind: AvatarKind,
    val hp: Int,
    val castTime: Float = 1.4f,
    val idleTime: Float = 2.5f,
    val elementCount: Int = Rules.START_ELEMENTS,
    val robeColor: Int = 0xFF8E2C3A.toInt(),
    /**
     * For showing the name in the player's language: a dummy's index, or a mage's title and name
     * indices (string arrays dummy_names, mage_titles, mage_names); [name] is the English name.
     */
    val dummyIndex: Int = -1,
    val titleIndex: Int = -1,
    val nameIndex: Int = -1,
    /** The chance that a move this bot plans is a wall instead of a shot. */
    val wallChance: Float = Rules.BOT_WALL_CHANCE,
    /** False on the first duel (the tutorial): walls unlock from level 2, which teaches them. */
    val walls: Boolean = true,
) {
    val fires: Boolean get() = kind == AvatarKind.MAGE
}

/**
 * The endless ladder: two training dummies, then mages that level up.
 * Each mage casts and waits a little shorter than the previous one (see [speed]; never below the
 * floors) and has a little more HP.
 */
object Opponents {
    private val dummies = listOf(
        Opponent("Target dummy", AvatarKind.DUMMY, hp = 40, dummyIndex = 0, walls = false),
        Opponent("Oak dummy", AvatarKind.DUMMY, hp = 80, dummyIndex = 1),
    )

    /** How many training dummies open the ladder (the k-th mage is level dummyCount + k). */
    val dummyCount: Int get() = dummies.size

    /** The first mage's base cast (12 s, 20% under the 15 s it was) and its wait between shots. */
    const val FIRST_CAST_TIME = 12f
    const val FIRST_IDLE_TIME = 28f

    /**
     * Each next mage casts and waits this factor of the previous one's times. The steps are biggest
     * at the bottom of the ladder and shrink toward [LAST_SPEED_STEP]: 20% faster after the first
     * mage, 18.5% after the second, … about 10% by mage 11 and 5% far up, so the early levels speed
     * up quickly and the top speed comes around level 28 instead of level 21.
     */
    const val FIRST_SPEED_STEP = 0.80f
    const val LAST_SPEED_STEP = 0.95f

    /** How quickly the steps shrink: each step's gap to [LAST_SPEED_STEP] is this factor of the previous one's. */
    const val SPEED_STEP_EASING = 0.9f

    /** Mage [k]'s cast and wait times as a share of the first mage's (1 for k = 0). */
    fun speed(k: Int): Float {
        var s = 1f
        for (i in 0 until k) s *= LAST_SPEED_STEP - (LAST_SPEED_STEP - FIRST_SPEED_STEP) * SPEED_STEP_EASING.pow(i)
        return s
    }

    const val MIN_CAST_TIME = 0.8f
    const val MIN_IDLE_TIME = 0.6f

    /** The first mage's HP (half the 90 it was); each next one has [HP_PER_LEVEL] more, up to [MAX_HP]. */
    const val FIRST_MAGE_HP = 45
    const val HP_PER_LEVEL = 8
    const val MAX_HP = 250

    private val titles = listOf(
        "Novice", "Apprentice", "Initiate", "Adept", "Conjurer",
        "Magus", "Sorcerer", "Warlock", "Archmage", "Grand Archmage",
    )
    private val names = listOf(
        "Cogwin", "Rustjaw", "Ozrin", "Tamsa", "Brannoc", "Ilvee",
        "Corvath", "Senna", "Druven", "Ysolde", "Harrow", "Quill",
    )
    private val robes = listOf(0xFF7A3E9D, 0xFFB0432F, 0xFF2F7D6B, 0xFF8E2C3A, 0xFF9A7A1F, 0xFF3E4FA8)

    /** The opponent at [level] (0 = first duel). */
    fun forLevel(level: Int): Opponent {
        if (level < dummies.size) return dummies[level.coerceAtLeast(0)]
        val k = level - dummies.size
        val speed = speed(k)
        return Opponent(
            name = "${titles[min(k / 2, titles.lastIndex)]} ${names[k % names.size]}",
            kind = AvatarKind.MAGE,
            hp = min(MAX_HP, FIRST_MAGE_HP + HP_PER_LEVEL * k),
            castTime = max(MIN_CAST_TIME, FIRST_CAST_TIME * speed),
            idleTime = max(MIN_IDLE_TIME, FIRST_IDLE_TIME * speed),
            robeColor = robes[k % robes.size].toInt(),
            titleIndex = min(k / 2, titles.lastIndex),
            nameIndex = k % names.size,
        )
    }
}
