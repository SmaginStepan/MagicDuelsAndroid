package com.an0obis.magicduel.game

/** Every tunable number of the duel, in one place. */
object Rules {
    const val ROWS = 5
    const val COLS = 5

    /** Spell grid: one row per lane, three spells per row. */
    const val GRID_ROWS = 5
    const val GRID_COLS = 3

    /** Elements available from the start; more unlock later through levelling up. */
    const val START_ELEMENTS = 4

    const val PLAYER_HP = 100
    const val DAMAGE_PER_GLOBE = 5
    const val BACKFIRE_DAMAGE = 10

    /** Casting takes a fixed time, so shots can't be spammed. */
    const val PLAYER_CAST_TIME = 2.7f

    /** Length of a bot's visible, repeating spell order. */
    const val BOT_SEQUENCE_LENGTH = 5

    /**
     * Bot shots never backfire: a bot shot into a full row still pushes it, and the globe pushed
     * off the player's edge flies at the player for this much damage.
     */
    const val PUSH_OUT_DAMAGE = 10

    /** Flight time in seconds, from lane 1 (top, slowest) to lane 5 (bottom, fastest). */
    val FLIGHT_TIME = floatArrayOf(6.0f, 5.16f, 4.32f, 3.47f, 2.63f)

    /** Speed (cells per second) of a shot sliding through its row and pushing globes ahead. */
    const val PUSH_SPEED = 9f

    // Board animation steps, in seconds.
    const val FALL_TIME = 0.22f
    const val POP_TIME = 0.38f
    const val BACKFIRE_TIME = 0.4f

    /**
     * Damage for [globes] exploding at step [chain] of a chain reaction:
     * the first explosion counts ×1, the second ×2, the third ×3, and so on.
     */
    fun matchDamage(globes: Int, chain: Int): Int = globes * DAMAGE_PER_GLOBE * chain
}
