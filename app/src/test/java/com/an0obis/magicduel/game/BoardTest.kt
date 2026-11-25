package com.an0obis.magicduel.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BoardTest {

    private fun lastRow(vararg rows: String) = Board.parse(*rows)

    @Test
    fun shotPushesEverythingToTheFarEnd() {
        val b = lastRow(".....", ".....", ".....", ".....", "..W.E")
        assertEquals(2, b.insert(4, fromLeft = true, globe = Globe(Element.FIRE)))
        assertEquals("..FWE", b.describe()[4])
    }

    @Test
    fun shotPushesGlobesAtTheEntryEdgeToo() {
        val b = lastRow(".....", ".....", ".....", ".....", "W.E..")
        assertEquals(2, b.insert(4, fromLeft = true, globe = Globe(Element.AIR)))
        assertEquals("..AWE", b.describe()[4])
    }

    @Test
    fun shotCrossesAnEmptyRowToTheFarEdge() {
        val left = lastRow(".....", ".....", ".....", ".....", ".....")
        assertEquals(4, left.insert(0, fromLeft = true, globe = Globe(Element.WATER)))
        assertEquals("....W", left.describe()[0])

        val right = lastRow(".....", ".....", ".....", ".....", ".....")
        assertEquals(0, right.insert(0, fromLeft = false, globe = Globe(Element.WATER)))
        assertEquals("W....", right.describe()[0])
    }

    @Test
    fun enemyShotPushesToTheLeftEnd() {
        val b = lastRow(".....", ".....", ".....", ".....", "W.E..")
        assertEquals(2, b.insert(4, fromLeft = false, globe = Globe(Element.FIRE)))
        assertEquals("WEF..", b.describe()[4])
    }

    @Test
    fun fullRowRejectsTheShot() {
        val b = lastRow(".....", ".....", ".....", ".....", "FWEAF")
        assertFalse(b.canInsert(4))
        assertEquals(-1, b.insert(4, fromLeft = true, globe = Globe(Element.WATER)))
        assertEquals("FWEAF", b.describe()[4])
    }

    @Test
    fun pushThroughFullRowShovesTheFarGlobeOut() {
        val b = lastRow(".....", ".....", ".....", ".....", "FWEAF")
        val out = b.pushThroughFullRow(4, fromLeft = false, globe = Globe(Element.EARTH))
        assertEquals(Element.FIRE, out.element)
        assertEquals("WEAFE", b.describe()[4])
        val out2 = b.pushThroughFullRow(4, fromLeft = true, globe = Globe(Element.AIR))
        assertEquals(Element.EARTH, out2.element)
        assertEquals("AWEAF", b.describe()[4])
    }

    @Test
    fun gravityDropsGlobesIntoGaps() {
        val b = Board.parse("F....", ".....", "W....", ".....", "E....")
        assertTrue(b.applyGravity())
        assertEquals(listOf(".....", ".....", "F....", "W....", "E...."), b.describe())
        assertFalse(b.applyGravity())
    }

    @Test
    fun horizontalAndVerticalLinesMatch() {
        val b = Board.parse(".....", ".....", "A....", "A....", "AFFFW")
        val expected = setOf(Cell(2, 0), Cell(3, 0), Cell(4, 0), Cell(4, 1), Cell(4, 2), Cell(4, 3))
        assertEquals(expected, b.findMatches())
    }

    @Test
    fun crossShapeMatches() {
        val b = Board.parse(".....", "..F..", ".FFF.", "..F..", "WEAWE")
        assertEquals(5, b.findMatches().size)
    }

    @Test
    fun rectangleMatches() {
        val b = Board.parse(".....", ".....", ".....", "EEE..", "EEEW.")
        assertEquals(6, b.findMatches().size)
    }

    @Test
    fun twoByTwoSquareAloneDoesNotMatch() {
        val b = Board.parse(".....", ".....", ".....", "WW...", "WWF..")
        assertTrue(b.findMatches().isEmpty())
    }

    @Test
    fun sameElementsConnectedToALineExplodeToo() {
        // WWW on the floor is the line; the snake of W above it is connected through shared sides.
        // The W at the far right touches only E and an empty cell, so it stays.
        val b = Board.parse(".....", ".....", "...W.", "..WW.", "WWWEW")
        val expected = setOf(Cell(4, 0), Cell(4, 1), Cell(4, 2), Cell(3, 2), Cell(3, 3), Cell(2, 3))
        assertEquals(expected, b.findMatches())
    }

    @Test
    fun squareTouchingALineExplodesWithIt() {
        val b = Board.parse(".....", ".....", ".....", "EE...", "EEEW.")
        assertEquals(5, b.findMatches().size)
    }

    @Test
    fun randomStartHasNoMatchesAndRestsOnTheFloor() {
        repeat(200) { seed ->
            val b = Board.randomStart(Random(seed), Element.active(4))
            assertTrue(b.findMatches().isEmpty())
            assertFalse(b.applyGravity())
            assertTrue(b.describe()[4].none { it == '.' })
        }
    }
}
