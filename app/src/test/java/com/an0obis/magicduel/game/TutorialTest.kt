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
}
