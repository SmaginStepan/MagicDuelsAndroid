package com.an0obis.magicduel.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class Side {
    PLAYER, ENEMY;

    val opponent: Side get() = if (this == PLAYER) ENEMY else PLAYER

    /** Player shots enter the field from the left edge, enemy shots from the right edge. */
    val fromLeft: Boolean get() = this == PLAYER

    /**
     * Bot shots never backfire: into a full row they push anyway, and the globe pushed off the
     * far edge hits the player. Player shots into a full row backfire.
     */
    val pushesFullRows: Boolean get() = this == ENEMY
}

data class Shot(val element: Element, val lane: Int)

/** A duellist: hit points plus the spell currently being cast (casting takes a fixed time). */
class Caster(val side: Side, val maxHp: Int, val castTime: Float) {
    var hp = maxHp
        private set
    var castElement: Element? = null
        private set
    var castLane = -1
        private set
    private var castT = 0f

    val isCasting: Boolean get() = castElement != null
    val castProgress: Float get() = if (isCasting) (castT / castTime).coerceIn(0f, 1f) else 0f

    internal fun startCast(element: Element, lane: Int) {
        castElement = element
        castLane = lane
        castT = 0f
    }

    internal fun cancelCast() {
        castElement = null
        castLane = -1
        castT = 0f
    }

    /** Advances the cast and returns the shot once casting completes. */
    internal fun advance(dt: Float): Shot? {
        val element = castElement ?: return null
        castT += dt
        if (castT < castTime) return null
        val shot = Shot(element, castLane)
        cancelCast()
        return shot
    }

    internal fun takeDamage(amount: Int) {
        hp = (hp - amount).coerceAtLeast(0)
    }
}

class Projectile internal constructor(
    val owner: Side,
    val element: Element,
    val lane: Int,
    val flightTime: Float,
) {
    /**
     * FLYING to the field, WAITING at its edge while the field is busy, BOUNCING back at its own
     * caster (backfire), EJECTED: a globe pushed out of a full row flying at the caster's opponent.
     */
    enum class State { FLYING, WAITING, BOUNCING, EJECTED, DONE }

    var state = State.FLYING
        internal set
    var flown = 0f
        internal set
    var bounceT = 0f
        internal set
    internal var arrivalOrder = 0L

    val progress: Float get() = (flown / flightTime).coerceIn(0f, 1f)
    val bounceProgress: Float get() = (bounceT / Rules.BACKFIRE_TIME).coerceIn(0f, 1f)
}

/** Things that happened during [Battle.update], for the screen to animate. */
sealed interface BattleEvent {
    data class CastStarted(val side: Side, val element: Element, val lane: Int) : BattleEvent

    /** The player cancelled a cast: nothing was fired and the dissolved spells in [cells] came back. */
    data class CastCancelled(val side: Side, val element: Element, val lane: Int, val cells: List<Cell>) : BattleEvent
    data class Launched(val side: Side, val element: Element, val lane: Int) : BattleEvent
    data class Landed(val side: Side, val element: Element, val row: Int, val col: Int) : BattleEvent
    data class Backfired(val side: Side, val lane: Int) : BattleEvent
    data class BackfireHit(val side: Side, val damage: Int) : BattleEvent
    data class Matched(
        val cells: List<Cell>,
        val elements: List<Element>,
        val target: Side,
        val damage: Int,
        val chain: Int,
    ) : BattleEvent
    data class SpellsRefilled(val cells: List<Cell>) : BattleEvent

    /** A shot by [by] pushed through a full row; the globe at the far edge flies out at the other side. */
    data class PushedOut(val by: Side, val lane: Int) : BattleEvent

    /** The globe pushed out of the field hit [target]. */
    data class PushOutHit(val target: Side, val damage: Int) : BattleEvent
    data class GameOver(val winner: Side) : BattleEvent
}

/**
 * One duel. Call [update] every frame. Impacts are resolved one at a time:
 * a projectile that arrives while the field is still moving waits at the edge.
 */
class Battle(
    val opponent: Opponent,
    seed: Long = System.nanoTime(),
    startBoard: Board? = null,
    val introTime: Float = 1.6f,
) {
    enum class Phase { IDLE, PUSH, FALL, POP }

    private val random = Random(seed)
    val elements: List<Element> = Element.active(opponent.elementCount)
    val board: Board = startBoard ?: Board.randomStart(random, elements)
    val spells = SpellGrid(random, elements)
    val player = Caster(Side.PLAYER, Rules.PLAYER_HP, Rules.PLAYER_CAST_TIME)
    val enemy = Caster(Side.ENEMY, opponent.hp, opponent.castTime)

    private val plannedShots = ArrayDeque<Shot>()

    /**
     * The bot's visible plan: its next [Rules.BOT_SEQUENCE_LENGTH] shots, generated randomly in advance.
     * The first one is cast next; once it has been cast it leaves the queue and a newly planned
     * shot joins at the end.
     */
    val botQueue: List<Shot> get() = plannedShots

    init {
        if (opponent.fires) repeat(Rules.BOT_SEQUENCE_LENGTH) { plannedShots.addLast(randomShot()) }
    }

    private fun randomShot() = Shot(elements.random(random), random.nextInt(Rules.ROWS))

    private var botWait = opponent.idleTime * 0.5f

    private val flying = ArrayList<Projectile>()
    val projectiles: List<Projectile> get() = flying

    var time = 0f
        private set
    val started: Boolean get() = time >= introTime
    var winner: Side? = null
        private set

    var phase = Phase.IDLE
        private set
    var phaseProgress = 0f
        private set
    private var phaseTime = 0f
    private var phaseDuration = 0f
    private var resolvingSide = Side.PLAYER
    private var chain = 0
    private var popping: Set<Cell> = emptySet()
    private var arrivals = 0L

    // The shot currently sliding through its row (PUSH phase).
    private var pushShot: Globe? = null
    private var pushRow = 0
    private var pushCol = 0
    private var pushFromLeft = true

    private val events = ArrayList<BattleEvent>()

    fun caster(side: Side): Caster = if (side == Side.PLAYER) player else enemy

    fun drainEvents(): List<BattleEvent> {
        if (events.isEmpty()) return emptyList()
        val out = ArrayList(events)
        events.clear()
        return out
    }

    /** The player taps the spell at (row, col). Returns false if it can't be cast right now. */
    fun tryCast(row: Int, col: Int): Boolean {
        if (!started || winner != null || player.isCasting) return false
        if (row !in 0 until spells.rows || col !in 0 until spells.cols) return false
        if (!spells.isAvailable(row, col)) return false
        val element = spells.take(row, col)
        player.startCast(element, lane = row)
        events += BattleEvent.CastStarted(Side.PLAYER, element, row)
        return true
    }

    /**
     * The player cancels the spell being cast: nothing is fired, and the spells that dissolved when it
     * was tapped come back unchanged, so another spell can be cast right away.
     * Returns false if there was nothing to cancel.
     */
    fun cancelCast(): Boolean {
        if (winner != null) return false
        val element = player.castElement ?: return false
        val lane = player.castLane
        player.cancelCast()
        events += BattleEvent.CastCancelled(Side.PLAYER, element, lane, spells.restore())
        return true
    }

    fun update(dt: Float) {
        time += dt
        if (winner == null && started) {
            player.advance(dt)?.let { shot ->
                launch(Side.PLAYER, shot)
                events += BattleEvent.SpellsRefilled(spells.refill())
            }
            updateBot(dt)
            updateProjectiles(dt)
        } else if (winner != null) {
            flying.removeAll { it.state == Projectile.State.DONE }
        }
        updateBoard(dt)
        animateGlobes(dt)
    }

    private fun launch(side: Side, shot: Shot) {
        flying += Projectile(side, shot.element, shot.lane, Rules.FLIGHT_TIME[shot.lane])
        events += BattleEvent.Launched(side, shot.element, shot.lane)
    }

    private fun updateBot(dt: Float) {
        if (plannedShots.isEmpty()) return
        if (enemy.isCasting) {
            enemy.advance(dt)?.let { shot ->
                launch(Side.ENEMY, shot)
                plannedShots.removeFirst()
                plannedShots.addLast(randomShot())
                botWait = opponent.idleTime
            }
        } else {
            botWait -= dt
            if (botWait <= 0f) {
                val shot = plannedShots.first()
                enemy.startCast(shot.element, shot.lane)
                events += BattleEvent.CastStarted(Side.ENEMY, shot.element, shot.lane)
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        for (p in flying) {
            when (p.state) {
                Projectile.State.FLYING -> {
                    p.flown += dt
                    if (p.flown >= p.flightTime) {
                        p.state = Projectile.State.WAITING
                        p.arrivalOrder = arrivals++
                    }
                }
                Projectile.State.BOUNCING -> {
                    p.bounceT += dt
                    if (p.bounceT >= Rules.BACKFIRE_TIME) {
                        p.state = Projectile.State.DONE
                        events += BattleEvent.BackfireHit(p.owner, Rules.BACKFIRE_DAMAGE)
                        hurt(p.owner, Rules.BACKFIRE_DAMAGE)
                    }
                }
                Projectile.State.EJECTED -> {
                    p.bounceT += dt
                    if (p.bounceT >= Rules.BACKFIRE_TIME) {
                        p.state = Projectile.State.DONE
                        val target = p.owner.opponent
                        events += BattleEvent.PushOutHit(target, Rules.PUSH_OUT_DAMAGE)
                        hurt(target, Rules.PUSH_OUT_DAMAGE)
                    }
                }
                else -> Unit
            }
        }
        while (phase == Phase.IDLE && winner == null) {
            val next = flying.filter { it.state == Projectile.State.WAITING }.minByOrNull { it.arrivalOrder } ?: break
            resolveImpact(next)
        }
        flying.removeAll { it.state == Projectile.State.DONE }
    }

    private fun resolveImpact(p: Projectile) {
        val full = !board.canInsert(p.lane)
        if (full && !p.owner.pushesFullRows) {
            // No free space in the row: the spell strikes its own caster.
            p.state = Projectile.State.BOUNCING
            events += BattleEvent.Backfired(p.owner, p.lane)
            return
        }
        p.state = Projectile.State.DONE
        val fromLeft = p.owner.fromLeft
        snapshotPositions()
        // The shot enters one cell outside the field edge and slides until it is blocked.
        val shot = Globe(p.element).apply {
            fromCol = if (fromLeft) -1f else board.cols.toFloat()
            fromRow = p.lane.toFloat()
            drawCol = fromCol
            drawRow = fromRow
            inFlight = true
            headingRight = fromLeft
        }
        val col = if (full) {
            // Full row, but this caster pushes anyway: the far globe flies out at the opponent.
            val out = board.pushThroughFullRow(p.lane, fromLeft, shot)
            flying += Projectile(p.owner, out.element, p.lane, 0f).apply { state = Projectile.State.EJECTED }
            events += BattleEvent.PushedOut(p.owner, p.lane)
            if (fromLeft) 0 else board.cols - 1
        } else {
            board.insert(p.lane, fromLeft, shot)
        }
        pushShot = shot
        pushRow = p.lane
        pushCol = col
        pushFromLeft = fromLeft
        resolvingSide = p.owner
        chain = 0
        startPhase(Phase.PUSH, max(0.1f, abs(col - shot.fromCol) / Rules.PUSH_SPEED))
    }

    /** The sliding shot stops and freezes into a crystal globe. */
    private fun freezeShot() {
        val shot = pushShot ?: return
        shot.inFlight = false
        shot.freeze = 1f
        events += BattleEvent.Landed(resolvingSide, shot.element, pushRow, pushCol)
        pushShot = null
    }

    private fun startPhase(next: Phase, duration: Float) {
        phase = next
        phaseTime = 0f
        phaseDuration = duration
        phaseProgress = 0f
    }

    private fun updateBoard(dt: Float) {
        if (phase == Phase.IDLE) return
        phaseTime += dt
        phaseProgress = (phaseTime / phaseDuration).coerceAtMost(1f)
        if (phaseTime < phaseDuration) return
        when (phase) {
            Phase.PUSH -> {
                freezeShot()
                settle()
            }
            Phase.FALL -> checkMatches()
            Phase.POP -> {
                board.remove(popping)
                popping = emptySet()
                settle()
            }
            Phase.IDLE -> Unit
        }
    }

    /** Gravity step: anything with empty cells below falls. */
    private fun settle() {
        snapshotPositions()
        if (board.applyGravity()) startPhase(Phase.FALL, Rules.FALL_TIME) else checkMatches()
    }

    private fun checkMatches() {
        val matches = board.findMatches()
        if (matches.isEmpty()) {
            phase = Phase.IDLE
            phaseProgress = 0f
            return
        }
        chain++
        val damage = Rules.matchDamage(matches.size, chain)
        val ordered = matches.sortedWith(compareBy({ it.row }, { it.col }))
        val elementsHit = ordered.map { board[it.row, it.col]!!.element }
        ordered.forEach { board[it.row, it.col]!!.popping = true }
        popping = matches
        events += BattleEvent.Matched(ordered, elementsHit, resolvingSide.opponent, damage, chain)
        hurt(resolvingSide.opponent, damage)
        startPhase(Phase.POP, Rules.POP_TIME)
    }

    private fun hurt(side: Side, amount: Int) {
        if (winner != null) return
        val target = caster(side)
        target.takeDamage(amount)
        if (target.hp <= 0) {
            winner = side.opponent
            player.cancelCast()
            enemy.cancelCast()
            flying.forEach { it.state = Projectile.State.DONE }
            events += BattleEvent.GameOver(side.opponent)
        }
    }

    private fun snapshotPositions() {
        board.forEachGlobe { r, c, g ->
            g.fromCol = c.toFloat()
            g.fromRow = r.toFloat()
            g.drawCol = g.fromCol
            g.drawRow = g.fromRow
        }
    }

    private fun animateGlobes(dt: Float) {
        val p = phaseProgress
        val shot = pushShot
        if (phase == Phase.PUSH && shot != null) {
            // The shot slides at constant speed; each globe ahead starts moving when the
            // shot (or the globes it already pushes) reaches it, like a train.
            val s = shot.fromCol + (pushCol - shot.fromCol) * p
            board.forEachGlobe { r, c, g ->
                g.drawRow = r.toFloat()
                g.drawCol = when {
                    r != pushRow -> c.toFloat()
                    g === shot -> s
                    pushFromLeft -> max(g.fromCol, s + (c - pushCol))
                    else -> min(g.fromCol, s + (c - pushCol))
                }
                if (g.freeze > 0f) g.freeze = (g.freeze - dt * 2.5f).coerceAtLeast(0f)
            }
            return
        }
        val t = if (phase == Phase.FALL) p * p else 1f // accelerate like falling
        val moving = phase == Phase.FALL
        board.forEachGlobe { r, c, g ->
            if (moving) {
                g.drawCol = g.fromCol + (c - g.fromCol) * t
                g.drawRow = g.fromRow + (r - g.fromRow) * t
            } else {
                g.drawCol = c.toFloat()
                g.drawRow = r.toFloat()
            }
            if (g.freeze > 0f) g.freeze = (g.freeze - dt * 2.5f).coerceAtLeast(0f)
        }
    }
}
