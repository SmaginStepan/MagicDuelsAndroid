package com.an0obis.magicduel.game

/**
 * A light tutorial for the first duel (the target dummy): a few short hints that follow what the
 * player actually does. Feed it the battle's events and the frame time; show [hint] when it's not null.
 */
class Tutorial {
    enum class Step { TAP_SPELL, WATCH_SHOT, MAKE_LINE, DAMAGE, DONE }

    var step = Step.TAP_SPELL
        private set
    private var stepTime = 0f
    private var backfireTime = 0f

    /** The hint to show right now, or null when there is nothing to say. */
    val hint: String?
        get() = when {
            backfireTime > 0f -> BACKFIRE
            step == Step.TAP_SPELL -> TAP_SPELL
            step == Step.WATCH_SHOT -> WATCH_SHOT
            step == Step.MAKE_LINE -> MAKE_LINE
            step == Step.DAMAGE -> DAMAGE
            else -> null
        }

    /** True while the hint is about the spell grid, so the screen can point at it. */
    val pointsAtSpells: Boolean get() = step == Step.TAP_SPELL && backfireTime <= 0f

    fun update(dt: Float) {
        stepTime += dt
        if (backfireTime > 0f) backfireTime = (backfireTime - dt).coerceAtLeast(0f)
        if (step == Step.DAMAGE && stepTime >= DAMAGE_HINT_TIME) advance(Step.DONE)
    }

    fun onEvent(e: BattleEvent) {
        when (e) {
            is BattleEvent.CastStarted ->
                if (e.side == Side.PLAYER && step == Step.TAP_SPELL) advance(Step.WATCH_SHOT)
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

        const val TAP_SPELL = "Tap a spell below to cast it. Its row number (1–5) is the lane it flies along."
        const val WATCH_SHOT = "Casting takes a moment. Then your spell flies down its lane and pushes that row to the far side."
        const val MAKE_LINE = "Line up 3 or more of the same element in a row or a column. They explode and hit your opponent."
        const val DAMAGE = "Each exploded globe deals 5 damage. Falling globes can set off chain explosions: ×2, ×3…"
        const val BACKFIRE = "That row was full, so your spell backfired and hit you. Aim at rows with space."
    }
}
