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

    /** The player's base cast time; casting into a lane takes longer, see [castTime]. */
    const val PLAYER_CAST_TIME = 2.7f

    /** Length of a bot's visible, repeating spell order. */
    const val BOT_SEQUENCE_LENGTH = 5

    /**
     * Bot shots never backfire: a bot shot into a full row still pushes it, and the globe pushed
     * off the player's edge flies at the player for this much damage.
     */
    const val PUSH_OUT_DAMAGE = 10

    /**
     * Size of a field cell in the screen's layout units (GameView.FIELD_SCALE): the field is 25%
     * larger than the 5 units it used to be.
     */
    const val FIELD_CELL_UNITS = 1.25f

    /** Speed of a shot on screen, in layout units per second: 25% slower than the 9 it used to be. */
    const val SHOT_SCREEN_SPEED = 6.75f

    /**
     * Speed of a shot in field cells per second (5.4), the same flying from the caster to the field
     * and sliding through its row (pushing the globes ahead).
     */
    const val SHOT_SPEED = SHOT_SCREEN_SPEED / FIELD_CELL_UNITS

    /**
     * Length of a shot's flight from the caster to the field's edge, in field cells: 0.625 layout
     * units, from 0.5 units inside the screen edge to half a field cell before the field
     * (GameView.FLIGHT_ZONE is built from it).
     */
    const val FLIGHT_CELLS = 0.5f

    /** Seconds a shot flies before it reaches the field (the same for every lane). */
    const val FLIGHT_TIME = FLIGHT_CELLS / SHOT_SPEED

    /**
     * Extra time per lane, from lane 1 (top, slowest) to lane 5 (bottom, fastest), between the end of
     * a caster's base cast and the shot reaching the field; it is spent casting (see [castTime]) and
     * the shot flies fast. The spread is 50% wider than the original lane flight times (6.0, 5.16,
     * 4.32, 3.47, 2.63 s) around the unchanged middle lane: lane 1 − lane 5 is 5.06 s instead of 3.37 s.
     */
    val LANE_TIME = floatArrayOf(6.84f, 5.58f, 4.32f, 3.045f, 1.785f)

    /**
     * How long casting into [lane] takes for a caster whose base cast time is [base]. The time from
     * starting the cast to the shot reaching the field is still `base + LANE_TIME[lane]`, as it was
     * when shots flew slowly after a fixed cast.
     */
    fun castTime(base: Float, lane: Int): Float = base + LANE_TIME[lane] - FLIGHT_TIME

    /** The caster's quickest spell cast (into the bottom lane); avatars play their cast clips at this length. */
    fun shortestCastTime(base: Float): Float = LANE_TIME.indices.minOf { castTime(base, it) }

    /**
     * Conjuring walls takes this many times as long as the caster's slowest spell (into lane 1):
     * 1.25 × 9.45 s ≈ 11.8 s for the player, clearly longer than any spell cast.
     */
    const val WALL_CAST_FACTOR = 1.25f

    fun wallCastTime(base: Float): Float = castTime(base, 0) * WALL_CAST_FACTOR

    /** The chance that a move the bot plans is a wall (in a random column) instead of a shot. */
    const val BOT_WALL_CHANCE = 0.2f

    /** The field columns walls can be cast into: the middle three (2–4), not the edge columns. */
    val WALL_COLUMNS = 1 until COLS - 1

    // Board animation steps, in seconds.
    const val FALL_TIME = 0.22f
    const val POP_TIME = 0.38f
    const val BACKFIRE_TIME = 0.4f

    /** A landed shot freezes into a crystal globe over this long; a shot that will fall waits for it. */
    const val FREEZE_TIME = 0.8f

    /**
     * Damage for [globes] exploding at step [chain] of a chain reaction:
     * the first explosion counts ×1, the second ×2, the third ×3, and so on.
     */
    fun matchDamage(globes: Int, chain: Int): Int = globes * DAMAGE_PER_GLOBE * chain
}
