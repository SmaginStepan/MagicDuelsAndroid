package com.an0obis.magicduel.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialTest {

    @Test
    fun hintsFollowWhatThePlayerDoes() {
        val t = Tutorial()
        assertEquals(Tutorial.TAP_SPELL, t.hint)
        assertTrue(t.pointsAtSpells)

        t.onEvent(BattleEvent.CastStarted(Side.PLAYER, Element.FIRE, 2))
        assertEquals(Tutorial.WATCH_SHOT, t.hint)
        assertFalse(t.pointsAtSpells)

        t.onEvent(BattleEvent.Landed(Side.PLAYER, Element.FIRE, 2, 4))
        assertEquals(Tutorial.MAKE_LINE, t.hint)

        t.onEvent(BattleEvent.Matched(listOf(Cell(4, 0)), listOf(Element.FIRE), Side.ENEMY, 15, 1))
        assertEquals(Tutorial.DAMAGE, t.hint)

        t.update(Tutorial.DAMAGE_HINT_TIME + 0.1f)
        assertEquals(Tutorial.Step.DONE, t.step)
        assertNull(t.hint)
    }

    @Test
    fun cancellingTheFirstCastGoesBackToTheTapHint() {
        val t = Tutorial()
        t.onEvent(BattleEvent.CastStarted(Side.PLAYER, Element.FIRE, 1))
        t.onEvent(BattleEvent.CastCancelled(Side.PLAYER, Element.FIRE, 1, emptyList()))
        assertEquals(Tutorial.TAP_SPELL, t.hint)
    }

    @Test
    fun enemyCastsDoNotAdvanceTheTutorial() {
        val t = Tutorial()
        t.onEvent(BattleEvent.CastStarted(Side.ENEMY, Element.WATER, 0))
        assertEquals(Tutorial.Step.TAP_SPELL, t.step)
    }

    @Test
    fun backfireShowsItsOwnHintForAWhile() {
        val t = Tutorial()
        t.onEvent(BattleEvent.CastStarted(Side.PLAYER, Element.WATER, 4))
        t.onEvent(BattleEvent.Backfired(Side.PLAYER, 4))
        assertEquals(Tutorial.BACKFIRE, t.hint)
        t.update(Tutorial.BACKFIRE_HINT_TIME + 0.1f)
        assertEquals(Tutorial.MAKE_LINE, t.hint)
    }

    @Test
    fun aMatchBeforeTheLineHintStillTeachesDamage() {
        val t = Tutorial()
        t.onEvent(BattleEvent.CastStarted(Side.PLAYER, Element.EARTH, 4))
        t.onEvent(BattleEvent.Landed(Side.PLAYER, Element.EARTH, 4, 0))
        t.onEvent(BattleEvent.Matched(listOf(Cell(4, 0)), listOf(Element.EARTH), Side.ENEMY, 15, 1))
        assertEquals(Tutorial.DAMAGE, t.hint)
    }

    @Test
    fun endOfTheDuelEndsTheTutorial() {
        val t = Tutorial()
        t.onEvent(BattleEvent.GameOver(Side.PLAYER))
        assertNull(t.hint)
    }

    @Test
    fun wallsAreLockedOnTheFirstDuelAndUnlockOnTheSecond() {
        assertFalse(Opponents.forLevel(0).walls)
        assertTrue(Opponents.forLevel(1).walls)
        assertTrue(Opponents.forLevel(2).walls)
        val first = Battle(Opponents.forLevel(0), seed = 1, introTime = 0f)
        assertFalse(first.canRaiseWall(1))
        assertFalse(first.tryCastWall(1))
        val second = Battle(Opponents.forLevel(1), seed = 1, startBoard = Board.parse(".....", ".....", ".....", ".....", "....."), introTime = 0f)
        assertTrue(second.tryCastWall(1))
    }

    @Test
    fun theWallsLessonFollowsTheFirstWallCast() {
        val t = WallTutorial()
        assertEquals(WallTutorial.TAP_WALL, t.hint)
        assertTrue(t.pointsAtWalls)
        // Spells and the bot's walls don't count.
        t.onEvent(BattleEvent.CastStarted(Side.PLAYER, Element.FIRE, 2))
        t.onEvent(BattleEvent.WallCastStarted(Side.ENEMY, 1))
        assertEquals(WallTutorial.TAP_WALL, t.hint)
        t.onEvent(BattleEvent.WallCastStarted(Side.PLAYER, 3))
        assertFalse(t.pointsAtWalls)
        // Cancelling goes back to the first hint.
        t.onEvent(BattleEvent.WallCastCancelled(Side.PLAYER, 3))
        assertTrue(t.pointsAtWalls)
        t.onEvent(BattleEvent.WallCastStarted(Side.PLAYER, 3))
        t.onEvent(BattleEvent.WallsRaised(Side.PLAYER, 3, listOf(Cell(0, 3))))
        assertEquals(WallTutorial.HOW_THEY_BREAK, t.hint)
        assertFalse(t.done)
        // Then Refresh all, which unlocks with the walls.
        t.update(WallTutorial.HOW_THEY_BREAK_TIME + 0.1f)
        assertEquals(WallTutorial.TAP_REFRESH, t.hint)
        assertTrue(t.pointsAtRefresh)
        t.onEvent(BattleEvent.RefreshStarted)
        assertFalse(t.pointsAtRefresh)
        t.onEvent(BattleEvent.RefreshCancelled(emptyList()))
        assertTrue(t.pointsAtRefresh)
        t.onEvent(BattleEvent.RefreshStarted)
        assertFalse(t.done)
        assertFalse(t.refreshTaught)
        t.onEvent(BattleEvent.RefreshDone)
        assertTrue(t.done)
        assertTrue(t.refreshTaught)
        assertNull(t.hint)
    }

    @Test
    fun aPlayerWhoLearnedWallsGetsOnlyTheRefreshLesson() {
        val t = WallTutorial(walls = false)
        assertEquals(WallTutorial.TAP_REFRESH, t.hint)
        assertFalse(t.pointsAtWalls)
        // The duel ending first leaves Refresh all untaught (it comes back next duel).
        t.onEvent(BattleEvent.GameOver(Side.PLAYER))
        assertTrue(t.done)
        assertFalse(t.refreshTaught)
    }
}
