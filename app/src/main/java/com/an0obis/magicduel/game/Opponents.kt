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
) {
    val fires: Boolean get() = kind == AvatarKind.MAGE
}

/**
 * The endless ladder: two training dummies, then mages that level up.
 * Each mage casts and waits [SPEED_UP] times as long as the previous one (never below the floors)
 * and has a little more HP.
 */
object Opponents {
    private val dummies = listOf(
        Opponent("Target dummy", AvatarKind.DUMMY, hp = 40),
        Opponent("Oak dummy", AvatarKind.DUMMY, hp = 80),
    )

    /** The first mage: 10× slower than the original first bot (which cast 1.5 s and waited 2.8 s). */
    const val FIRST_CAST_TIME = 15f
    const val FIRST_IDLE_TIME = 28f

    /** Each next mage is this factor of the previous one's cast and wait time (0.85 = 15% faster). */
    const val SPEED_UP = 0.85f
    const val MIN_CAST_TIME = 0.8f
    const val MIN_IDLE_TIME = 0.6f

    const val FIRST_MAGE_HP = 90
    const val HP_PER_LEVEL = 8
    const val MAX_HP = 250

    private val titles = listOf(
        "Novice", "Apprentice", "Initiate", "Adept", "Conjurer",
        "Magus", "Sorcerer", "Warlock", "Archmage", "Grand Archmage",
    )
    private val names = listOf(
        "Vell", "Morra", "Ozrin", "Tamsa", "Brannoc", "Ilvee",
        "Corvath", "Senna", "Druven", "Ysolde", "Harrow", "Quill",
    )
    private val robes = listOf(0xFF7A3E9D, 0xFFB0432F, 0xFF2F7D6B, 0xFF8E2C3A, 0xFF9A7A1F, 0xFF3E4FA8)

    /** The opponent at [level] (0 = first duel). */
    fun forLevel(level: Int): Opponent {
        if (level < dummies.size) return dummies[level.coerceAtLeast(0)]
        val k = level - dummies.size
        val speed = SPEED_UP.pow(k)
        return Opponent(
            name = "${titles[min(k / 2, titles.lastIndex)]} ${names[k % names.size]}",
            kind = AvatarKind.MAGE,
            hp = min(MAX_HP, FIRST_MAGE_HP + HP_PER_LEVEL * k),
            castTime = max(MIN_CAST_TIME, FIRST_CAST_TIME * speed),
            idleTime = max(MIN_IDLE_TIME, FIRST_IDLE_TIME * speed),
            robeColor = robes[k % robes.size].toInt(),
        )
    }
}
