package com.an0obis.magicduel.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BattleTest {
    private val dummy = Opponent("Test dummy", AvatarKind.DUMMY, hp = 100)

    private fun run(b: Battle, seconds: Float) {
        var t = 0f
        while (t < seconds) {
            b.update(0.02f)
            t += 0.02f
        }
    }

    /** Enough time for the player's shot into [lane] to be cast, fly and fully resolve. */
    private fun shotTime(lane: Int) = Rules.castTime(Rules.PLAYER_CAST_TIME, lane) + Rules.FLIGHT_TIME + 2.5f

    @Test
    fun takingASpellRefreshesItsRowAndColumn() {
        val grid = SpellGrid(Random(1), Element.active(4))
        grid.take(2, 1)
        val gone = (0 until grid.rows).flatMap { r ->
            (0 until grid.cols).filter { c -> !grid.isAvailable(r, c) }.map { c -> Cell(r, c) }
        }
        assertEquals(7, gone.size)
        assertTrue(gone.all { it.row == 2 || it.col == 1 })
        assertEquals(7, grid.refill().size)
        assertTrue((0 until grid.rows).all { r -> (0 until grid.cols).all { c -> grid.isAvailable(r, c) } })
    }

    @Test
    fun castingTakesItsLanesTimeAndBlocksAnotherCast() {
        val b = Battle(dummy, seed = 3, introTime = 0f)
        assertTrue(b.tryCast(0, 0))
        assertFalse(b.tryCast(1, 1))
        run(b, Rules.castTime(Rules.PLAYER_CAST_TIME, 0) - 0.1f)
        assertTrue(b.player.isCasting) // the top lane takes the longest
        run(b, 0.15f)
        assertFalse(b.player.isCasting)
        assertEquals(1, b.projectiles.size)
        assertTrue(b.tryCast(1, 1))
    }

    @Test
    fun fastShotsKeepTheTimeFromCastToFieldPerLane() {
        // Before: a 2.7 s cast, then a slow flight of LANE_TIME seconds. Now the cast is longer
        // and the flight short, but a shot still reaches the field at the same moment.
        for (lane in 0 until Rules.ROWS) {
            for (base in listOf(Rules.PLAYER_CAST_TIME, 0.8f, 15f)) {
                assertEquals(base + Rules.LANE_TIME[lane], Rules.castTime(base, lane) + Rules.FLIGHT_TIME, 0.0001f)
            }
            if (lane > 0) assertTrue(Rules.castTime(2.7f, lane) < Rules.castTime(2.7f, lane - 1))
        }
        assertTrue(Rules.FLIGHT_TIME < 0.2f)
    }

    @Test
    fun biggerFieldAndSlowerShotsAreWorkedOutTogether() {
        // The field is 25% larger: 5 cells of 1.25 layout units = 6.25 units. The whole width stays
        // 9.75 units, so each side lane is 1.75 units and a shot flies 1.75 - 0.5 - 0.625 = 0.625
        // units (half a field cell) before it reaches the field.
        val fieldUnits = 5f * Rules.FIELD_CELL_UNITS
        val laneUnits = (9.75f - fieldUnits) / 2f
        val flightUnits = laneUnits - 0.5f - 0.5f * Rules.FIELD_CELL_UNITS
        assertEquals(6.25f, fieldUnits, 0.0001f)
        assertEquals(flightUnits, Rules.FLIGHT_CELLS * Rules.FIELD_CELL_UNITS, 0.0001f)
        // On screen shots are 25% slower than the 9 units a second before, in and out of the field.
        assertEquals(9f * 0.75f, Rules.SHOT_SPEED * Rules.FIELD_CELL_UNITS, 0.0001f)
        assertEquals(flightUnits / Rules.SHOT_SCREEN_SPEED, Rules.FLIGHT_TIME, 0.0001f)
        // So the player's casts are 9.45 s into lane 1 down to 4.39 s into lane 5.
        assertEquals(9.447f, Rules.castTime(Rules.PLAYER_CAST_TIME, 0), 0.001f)
        assertEquals(4.392f, Rules.castTime(Rules.PLAYER_CAST_TIME, 4), 0.001f)
    }

    @Test
    fun laneCastTimesSpreadHalfAgainAsWideAsTheOriginalFlights() {
        val original = floatArrayOf(6.0f, 5.16f, 4.32f, 3.47f, 2.63f)
        // Lane 1 vs lane 5: 50% more difference than before, around the same middle lane.
        assertEquals(1.5f * (original[0] - original[4]), Rules.LANE_TIME[0] - Rules.LANE_TIME[4], 0.001f)
        assertEquals(original[2], Rules.LANE_TIME[2], 0.0001f)
        for (lane in 0 until Rules.ROWS) {
            assertEquals(original[2] + 1.5f * (original[lane] - original[2]), Rules.LANE_TIME[lane], 0.001f)
            if (lane > 0) assertTrue(Rules.LANE_TIME[lane] < Rules.LANE_TIME[lane - 1])
        }
        // Even the fastest bot (0.8 s base) still needs a moment to cast into the fastest lane.
        assertTrue(Rules.castTime(0.8f, 4) > 2f)
    }

    @Test
    fun aShotFreezesInPlaceBeforeItFalls() {
        // Lane 2 has one globe at the right edge, held up by a full column; the shot stops next to it,
        // above three empty cells.
        val board = Board.parse(".....", "....F", "....W", "....E", "....A")
        val b = Battle(dummy, seed = 3, startBoard = board, introTime = 0f)
        assertTrue(b.tryCast(1, 0))
        val arrives = Rules.castTime(Rules.PLAYER_CAST_TIME, 1) + Rules.FLIGHT_TIME
        val slide = 4f / Rules.SHOT_SPEED // from one cell outside the field to column 3
        run(b, arrives + slide + 0.1f)
        // Landed and freezing where it stopped: nothing has fallen yet.
        assertEquals(Battle.Phase.FREEZE, b.phase)
        assertTrue(b.board[1, 3] != null && !b.board[1, 3]!!.inFlight)
        assertNull(b.board[4, 3])
        run(b, Rules.FREEZE_TIME - 0.2f)
        assertTrue(b.board[1, 3] != null) // still frozen in place
        // Once frozen it falls as a globe to the bottom.
        run(b, 0.2f + Rules.FALL_TIME + 0.1f)
        assertNull(b.board[1, 3])
        assertTrue(b.board[4, 3] != null)
        assertTrue(b.board[4, 3]!!.freeze == 0f)
    }

    @Test
    fun aShotThatStaysPutDoesNotWaitForItsFreeze() {
        // The shot stops on top of a globe: the field settles straight away, as before.
        val board = Board.parse(".....", ".....", ".....", ".....", "....F")
        val b = Battle(dummy, seed = 3, startBoard = board, introTime = 0f)
        assertTrue(b.tryCast(4, 0))
        run(b, Rules.castTime(Rules.PLAYER_CAST_TIME, 4) + Rules.FLIGHT_TIME + 4f / Rules.SHOT_SPEED + 0.1f)
        assertEquals(Battle.Phase.IDLE, b.phase)
        assertTrue(b.board[4, 3]!!.freeze > 0f) // still freezing (drawn only)
    }

    @Test
    fun aShotReachesTheFieldAtTheSameMomentAsBefore() {
        val b = Battle(dummy, seed = 3, introTime = 0f)
        assertTrue(b.tryCast(3, 0))
        val before = Rules.PLAYER_CAST_TIME + Rules.LANE_TIME[3]
        run(b, before - 0.1f)
        assertTrue(b.drainEvents().none { it is BattleEvent.Landed })
        run(b, 0.2f + 5f / Rules.SHOT_SPEED)
        assertTrue(b.drainEvents().any { it is BattleEvent.Landed && it.side == Side.PLAYER })
    }

    @Test
    fun cancellingACastFiresNothingAndBringsTheSpellsBack() {
        val b = Battle(dummy, seed = 3, introTime = 0f)
        val before = (0 until b.spells.rows).map { r -> (0 until b.spells.cols).map { c -> b.spells[r, c] } }
        assertFalse(b.cancelCast()) // nothing to cancel yet
        assertTrue(b.tryCast(2, 1))
        run(b, Rules.PLAYER_CAST_TIME / 2)
        assertTrue(b.cancelCast())
        assertFalse(b.player.isCasting)
        val after = (0 until b.spells.rows).map { r -> (0 until b.spells.cols).map { c -> b.spells[r, c] } }
        assertEquals(before, after)
        assertTrue((0 until b.spells.rows).all { r -> (0 until b.spells.cols).all { c -> b.spells.isAvailable(r, c) } })
        val events = b.drainEvents()
        val cancelled = events.filterIsInstance<BattleEvent.CastCancelled>().single()
        assertEquals(7, cancelled.cells.size)
        // Nothing is ever launched, and a new cast can start straight away.
        run(b, Rules.PLAYER_CAST_TIME + 1f)
        assertTrue(b.projectiles.isEmpty())
        assertTrue(b.drainEvents().none { it is BattleEvent.Launched })
        assertTrue(b.tryCast(0, 0))
    }

    @Test
    fun shotIntoFullRowBackfiresOnCaster() {
        val board = Board.parse(".....", ".....", ".....", ".....", "FWEAF")
        val b = Battle(dummy, seed = 1, startBoard = board, introTime = 0f)
        b.spells[4, 0] = Element.WATER
        assertTrue(b.tryCast(4, 0))
        run(b, shotTime(4))
        assertEquals(Rules.PLAYER_HP - Rules.BACKFIRE_DAMAGE, b.player.hp)
        assertEquals(dummy.hp, b.enemy.hp)
        assertEquals("FWEAF", board.describe()[4])
    }

    @Test
    fun completingALineDamagesTheOpponent() {
        val board = Board.parse(".....", ".....", ".....", ".....", ".FFWE")
        val b = Battle(dummy, seed = 1, startBoard = board, introTime = 0f)
        b.spells[4, 2] = Element.FIRE
        assertTrue(b.tryCast(4, 2))
        run(b, shotTime(4))
        assertEquals(dummy.hp - Rules.matchDamage(3, 1), b.enemy.hp)
        assertEquals(Rules.PLAYER_HP, b.player.hp)
        assertEquals("...WE", board.describe()[4])
    }

    @Test
    fun damageGrowsWithGlobesAndChainStep() {
        assertEquals(15, Rules.matchDamage(3, 1))
        assertEquals(20, Rules.matchDamage(4, 1))
        assertEquals(30, Rules.matchDamage(3, 2))
        assertEquals(60, Rules.matchDamage(4, 3))
    }

    @Test
    fun chainReactionUsesMultiplier() {
        // Water pushes the floor row to "WWWWF": four waters explode, then the two fires
        // above fall next to the last fire and FFF explodes as the second step of the chain.
        val board = Board.parse(".....", ".....", ".....", "..FF.", "W.WWF")
        val b = Battle(dummy, seed = 1, startBoard = board, introTime = 0f)
        b.spells[4, 0] = Element.WATER
        assertTrue(b.tryCast(4, 0))
        run(b, shotTime(4))
        assertEquals(dummy.hp - Rules.matchDamage(4, 1) - Rules.matchDamage(3, 2), b.enemy.hp)
        assertEquals(listOf(".....", ".....", ".....", ".....", "....."), board.describe())
    }

    @Test
    fun shotCrossesTheFieldFreezesThenFalls() {
        val board = Board.parse(".....", ".....", ".....", ".....", "FW...")
        val b = Battle(dummy, seed = 1, startBoard = board, introTime = 0f)
        b.spells[0, 1] = Element.EARTH
        assertTrue(b.tryCast(0, 1))
        val landed = mutableListOf<BattleEvent.Landed>()
        var t = 0f
        while (t < shotTime(0)) {
            b.update(0.02f)
            t += 0.02f
            landed += b.drainEvents().filterIsInstance<BattleEvent.Landed>()
        }
        // It froze at the far end of lane 1, then fell to the floor.
        assertEquals(listOf(BattleEvent.Landed(Side.PLAYER, Element.EARTH, 0, 4)), landed)
        assertEquals("FW..E", board.describe()[4])
    }

    @Test
    fun enemyShotCrossesAnEmptyFieldToTheLeftEdge() {
        // The bot waits idleTime / 2 (30 s) before its first cast, then 60 s between casts.
        val mage = Opponent("Test mage", AvatarKind.MAGE, hp = 100, castTime = 0.3f, idleTime = 60f)
        val board = Board.parse(".....", ".....", ".....", ".....", ".....")
        val b = Battle(mage, seed = 9, startBoard = board, introTime = 0f)
        val shot = b.botQueue[0]
        // Slide across (up to 6 cells), freeze in place, then fall to the bottom row.
        run(b, 30f + Rules.castTime(mage.castTime, shot.lane) + Rules.FLIGHT_TIME + 6f / Rules.SHOT_SPEED + Rules.FREEZE_TIME + 1f)
        assertEquals("${Board.code(shot.element)}....", board.describe()[4])
    }

    @Test
    fun botCastsTheHeadOfItsQueueThenPlansANewShotAtTheTail() {
        val mage = Opponent("Test mage", AvatarKind.MAGE, hp = 100, castTime = 0.5f, idleTime = 0.5f)
        val b = Battle(mage, seed = 5, introTime = 0f)
        assertEquals(Rules.BOT_SEQUENCE_LENGTH, b.botQueue.size)
        var before = b.botQueue.toList()
        var launches = 0
        var t = 0f
        while (launches < 7 && t < 80f) {
            b.update(0.02f)
            t += 0.02f
            for (e in b.drainEvents()) {
                if (e !is BattleEvent.Launched || e.side != Side.ENEMY) continue
                assertEquals(before.first(), Shot(e.element, e.lane))
                val now = b.botQueue.toList()
                assertEquals(Rules.BOT_SEQUENCE_LENGTH, now.size)
                assertEquals(before.drop(1), now.dropLast(1))
                before = now
                launches++
            }
        }
        assertEquals(7, launches)
    }

    @Test
    fun botPlanIsRandomNotARepeatingLoop() {
        val mage = Opponent("Test mage", AvatarKind.MAGE, hp = 100, castTime = 0.2f, idleTime = 0.2f)
        val b = Battle(mage, seed = 11, introTime = 0f)
        // Everything the bot plans: its first queue, then the shot added at the tail after each cast.
        // (The duel itself may end before 20 shots are fired: the idle player gets pushed out.)
        val fired = b.botQueue.toMutableList()
        var t = 0f
        while (fired.size < 20 && t < 200f && b.winner == null) {
            b.update(0.02f)
            t += 0.02f
            if (b.drainEvents().any { it is BattleEvent.Launched && it.side == Side.ENEMY }) fired += b.botQueue.last()
        }
        assertTrue(fired.size >= 20)
        // A 5-shot loop would repeat exactly every 5 shots.
        assertTrue((5 until 20).any { fired[it] != fired[it - 5] })
    }

    @Test
    fun botShotIntoFullRowPushesAGlobeOutAtThePlayer() {
        // A completely full field: the bot's shot still pushes its row one cell toward the player,
        // and the globe pushed off the left edge hits the player. The bot never backfires.
        val full = Board.parse("FWEAF", "EAFWE", "FWEAF", "EAFWE", "FWEAF")
        // The bot waits idleTime / 2 (20 s) before its first cast, then 40 s between casts.
        val mage = Opponent("Test mage", AvatarKind.MAGE, hp = 100, castTime = 0.3f, idleTime = 40f)
        val b = Battle(mage, seed = 4, startBoard = full, introTime = 0f)
        val shot = b.botQueue[0]
        val events = mutableListOf<BattleEvent>()
        var t = 0f
        while (t < 20f + Rules.castTime(mage.castTime, shot.lane) + Rules.FLIGHT_TIME + 2f) {
            b.update(0.02f)
            t += 0.02f
            events += b.drainEvents()
        }
        assertEquals(1, events.count { it == BattleEvent.PushedOut(Side.ENEMY, shot.lane) })
        assertEquals(1, events.count { it is BattleEvent.PushOutHit && it.target == Side.PLAYER && it.damage == Rules.PUSH_OUT_DAMAGE })
        assertTrue(events.none { it is BattleEvent.Backfired || it is BattleEvent.BackfireHit })
        assertEquals(100, b.enemy.hp)
        assertTrue(b.player.hp <= Rules.PLAYER_HP - Rules.PUSH_OUT_DAMAGE)
    }

    @Test
    fun dummyNeverFires() {
        val b = Battle(dummy, seed = 2, introTime = 0f)
        run(b, 20f)
        assertTrue(b.projectiles.none { it.owner == Side.ENEMY })
        assertFalse(b.enemy.isCasting)
    }
}
