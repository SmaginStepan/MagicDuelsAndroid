package com.an0obis.magicduel.game

/**
 * Short hints that follow what the player actually does. Feed it the battle's events and the frame
 * time; show [hint] when it's not null.
 */
interface Lesson {
    /** The hint to show right now (a string resource name), or null when there is nothing to say. */
    val hint: String?

    /** True while the hint is about the spell grid, so the screen can point at it. */
    val pointsAtSpells: Boolean get() = false

    /** True while the hint is about the wall buttons, so the screen can point at them. */
    val pointsAtWalls: Boolean get() = false

    /** True while the hint is about the Refresh all button, so the screen can point at it. */
    val pointsAtRefresh: Boolean get() = false

    /** True once everything has been taught. */
    val done: Boolean

    fun update(dt: Float)
    fun onEvent(e: BattleEvent)
}

/**
 * The walls lesson, on level 2 (the first duel where walls can be cast): what the wall buttons do,
 * then, once the player has cast walls, how walls break; then Refresh all, which unlocks with them.
 * With [walls] false it teaches only Refresh all (for a player who learned walls before it existed).
 */
class WallTutorial(walls: Boolean = true) : Lesson {
    enum class Step { TAP_WALL, WATCH_WALL, HOW_THEY_BREAK, TAP_REFRESH, WATCH_REFRESH, DONE }

    var step = if (walls) Step.TAP_WALL else Step.TAP_REFRESH
        private set
    private var stepTime = 0f

    /** True once the player has done a whole Refresh all during the lesson. */
    var refreshTaught = false
        private set

    override val hint: String?
        get() = when (step) {
            Step.TAP_WALL, Step.WATCH_WALL -> TAP_WALL
            Step.HOW_THEY_BREAK -> HOW_THEY_BREAK
            Step.TAP_REFRESH, Step.WATCH_REFRESH -> TAP_REFRESH
            Step.DONE -> null
        }

    override val pointsAtWalls: Boolean get() = step == Step.TAP_WALL
    override val pointsAtRefresh: Boolean get() = step == Step.TAP_REFRESH
    override val done: Boolean get() = step == Step.DONE

    override fun update(dt: Float) {
        stepTime += dt
        if (step == Step.HOW_THEY_BREAK && stepTime >= HOW_THEY_BREAK_TIME) advance(Step.TAP_REFRESH)
    }

    override fun onEvent(e: BattleEvent) {
        when (e) {
            is BattleEvent.WallCastStarted -> if (e.side == Side.PLAYER && step == Step.TAP_WALL) advance(Step.WATCH_WALL)
            is BattleEvent.WallCastCancelled -> if (e.side == Side.PLAYER && step == Step.WATCH_WALL) advance(Step.TAP_WALL)
            is BattleEvent.WallsRaised -> if (e.side == Side.PLAYER && step == Step.WATCH_WALL) advance(Step.HOW_THEY_BREAK)
            BattleEvent.RefreshStarted -> if (step == Step.TAP_REFRESH) advance(Step.WATCH_REFRESH)
            is BattleEvent.RefreshCancelled -> if (step == Step.WATCH_REFRESH) advance(Step.TAP_REFRESH)
            BattleEvent.RefreshDone -> if (step == Step.TAP_REFRESH || step == Step.WATCH_REFRESH) {
                refreshTaught = true
                advance(Step.DONE)
            }
            is BattleEvent.GameOver -> advance(Step.DONE)
            else -> Unit
        }
    }

    private fun advance(next: Step) {
        step = next
        stepTime = 0f
    }

    companion object {
        const val HOW_THEY_BREAK_TIME = 9f
        const val TAP_WALL = "tut_walls_tap"
        const val HOW_THEY_BREAK = "tut_walls_break"
        const val TAP_REFRESH = "tut_refresh"
    }
}

/**
 * A light tutorial for the first duel (the target dummy): a few short hints that follow what the
 * player actually does.
 */
class Tutorial : Lesson {
    enum class Step { TAP_SPELL, WATCH_SHOT, MAKE_LINE, DAMAGE, DONE }

    var step = Step.TAP_SPELL
        private set
    private var stepTime = 0f
    private var backfireTime = 0f

    /** The hint to show right now (a string resource name), or null when there is nothing to say. */
    override val hint: String?
        get() = when {
            backfireTime > 0f -> BACKFIRE
            step == Step.TAP_SPELL -> TAP_SPELL
            step == Step.WATCH_SHOT -> WATCH_SHOT
            step == Step.MAKE_LINE -> MAKE_LINE
            step == Step.DAMAGE -> DAMAGE
            else -> null
        }

    /** True while the hint is about the spell grid, so the screen can point at it. */
    override val pointsAtSpells: Boolean get() = step == Step.TAP_SPELL && backfireTime <= 0f
    override val done: Boolean get() = step == Step.DONE

    override fun update(dt: Float) {
        stepTime += dt
        if (backfireTime > 0f) backfireTime = (backfireTime - dt).coerceAtLeast(0f)
        if (step == Step.DAMAGE && stepTime >= DAMAGE_HINT_TIME) advance(Step.DONE)
    }

    override fun onEvent(e: BattleEvent) {
        when (e) {
            is BattleEvent.CastStarted ->
                if (e.side == Side.PLAYER && step == Step.TAP_SPELL) advance(Step.WATCH_SHOT)
            is BattleEvent.CastCancelled ->
                if (e.side == Side.PLAYER && step == Step.WATCH_SHOT) advance(Step.TAP_SPELL)
            is BattleEvent.Landed ->
                if (e.side == Side.PLAYER && step == Step.WATCH_SHOT) advance(Step.MAKE_LINE)
            is BattleEvent.Backfired -> if (e.side == Side.PLAYER) {
                backfireTime = BACKFIRE_HINT_TIME
                if (step == Step.WATCH_SHOT) advance(Step.MAKE_LINE)
            }
            is BattleEvent.Matched ->
                if (e.target == Side.ENEMY && step < Step.DAMAGE) advance(Step.DAMAGE)
            is BattleEvent.GameOver -> {
                backfireTime = 0f
                advance(Step.DONE)
            }
            else -> Unit
        }
    }

    private fun advance(next: Step) {
        step = next
        stepTime = 0f
    }

    companion object {
        const val DAMAGE_HINT_TIME = 7f
        const val BACKFIRE_HINT_TIME = 4f

        // Hints are string resource names; the screen shows them in the chosen language.
        const val TAP_SPELL = "tut_tap_spell"
        const val WATCH_SHOT = "tut_watch_shot"
        const val MAKE_LINE = "tut_make_line"
        const val DAMAGE = "tut_damage"
        const val BACKFIRE = "tut_backfire"
    }
}
