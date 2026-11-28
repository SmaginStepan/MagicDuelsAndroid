package com.an0obis.magicduel.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyTest {
    @Test
    fun aNewPlayerStartsWithThreeAndGetsDayOne() {
        val e = Economy()
        assertEquals(3, e.essence)
        val gift = e.claim(100)!!
        assertEquals(1, gift.streakDay)
        assertEquals(1, gift.total)
        assertEquals(4, e.essence)
    }

    @Test
    fun theGiftComesOnceADay() {
        val e = Economy()
        e.claim(100)
        assertFalse(e.giftDue(100))
        assertNull(e.claim(100))
        assertEquals(4, e.essence)
        assertTrue(e.giftDue(101))
    }

    @Test
    fun aStreakGrowsOverSevenDaysThenStartsAgain() {
        val e = Economy(essence = 0)
        val totals = (100L..108L).map { e.claim(it)!!.total }
        // Days 1..7: 1 + bonus 0, 1, 1, 2, 2, 3, 5; then day 1 and 2 again.
        assertEquals(listOf(1, 2, 2, 3, 3, 4, 6, 1, 2), totals)
        assertEquals(totals.sum(), e.essence)
    }

    @Test
    fun missingADayResetsTheStreak() {
        val e = Economy(essence = 0)
        e.claim(100); e.claim(101); e.claim(102)
        assertEquals(3, e.streakDay)
        val gift = e.claim(104)!! // skipped day 103
        assertEquals(1, gift.streakDay)
        assertEquals(1, gift.total)
    }

    @Test
    fun turningTheClockBackGivesNothing() {
        val e = Economy(essence = 0)
        e.claim(100)
        assertNull(e.claim(99))
        assertEquals(1, e.essence)
        // The next real day still continues the streak.
        assertEquals(2, e.claim(101)!!.streakDay)
    }

    @Test
    fun spendingNeedsEnoughEssence() {
        val e = Economy(essence = 2)
        assertFalse(e.spend(3))
        assertEquals(2, e.essence)
        assertTrue(e.spend(2))
        assertEquals(0, e.essence)
    }
}
