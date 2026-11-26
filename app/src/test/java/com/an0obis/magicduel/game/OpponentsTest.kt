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
    fun firstMageCasts12sAndWaits28s() {
        val first = Opponents.forLevel(2)
        assertEquals(12f, first.castTime, 0.001f)
        assertEquals(28f, first.idleTime, 0.001f)
    }

    @Test
    fun theSpeedUpIsBiggestAtTheBottomOfTheLadder() {
        // Mage 2 is 20% faster than mage 1; each next step is smaller, approaching 5%.
        assertEquals(0.8f, Opponents.speed(1), 0.0001f)
        var previousStep = 0f
        for (k in 1..40) {
            val step = Opponents.speed(k) / Opponents.speed(k - 1)
            assertTrue("step $k", step > previousStep)
            assertTrue("step $k", step < Opponents.LAST_SPEED_STEP)
            previousStep = step
        }
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
