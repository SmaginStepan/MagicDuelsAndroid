package com.an0obis.magicduel.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpponentsTest {

    @Test
    fun ladderStartsWithTwoDummiesThatNeverFire() {
        assertFalse(Opponents.forLevel(0).fires)
        assertFalse(Opponents.forLevel(1).fires)
        assertTrue(Opponents.forLevel(2).fires)
    }

    @Test
    fun firstMageIsTenTimesSlowerThanTheOriginalFirstBot() {
        val first = Opponents.forLevel(2)
        assertEquals(1.5f * 10, first.castTime, 0.001f)
        assertEquals(2.8f * 10, first.idleTime, 0.001f)
    }

    @Test
    fun eachMageIsSlightlyFasterAndTougherUntilTheFloor() {
        var previous = Opponents.forLevel(2)
        for (level in 3..60) {
            val next = Opponents.forLevel(level)
            assertTrue(next.castTime <= previous.castTime)
            assertTrue(next.idleTime <= previous.idleTime)
            assertTrue(next.hp >= previous.hp)
            assertTrue(next.castTime >= Opponents.MIN_CAST_TIME)
            assertTrue(next.idleTime >= Opponents.MIN_IDLE_TIME)
            previous = next
        }
        assertEquals(Opponents.MIN_CAST_TIME, Opponents.forLevel(60).castTime, 0.001f)
        assertEquals(Opponents.MAX_HP, Opponents.forLevel(60).hp)
    }
}
