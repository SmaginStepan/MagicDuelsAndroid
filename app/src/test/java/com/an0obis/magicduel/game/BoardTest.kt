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
        assertFalse(b.canInsert(4, fromLeft = true))
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

    // ---------------------------------------------------------------- walls

    @Test
    fun aShotThatReachesAWallBreaksItAndTakesItsCell() {
        val b = lastRow(".....", ".....", ".....", ".....", "..#.E")
        assertEquals(2, b.firstWall(4, fromLeft = true))
        assertEquals(2, b.insert(4, fromLeft = true, globe = Globe(Element.FIRE)))
        assertEquals("..F.E", b.describe()[4])
        b.removeWall(4, 2)
        assertFalse(b.isWall(4, 2))
    }

    @Test
    fun aWallHoldsWhenTheShotPushesGlobesAgainstIt() {
        val b = lastRow(".....", ".....", ".....", ".....", ".W#..")
        assertEquals(0, b.insert(4, fromLeft = true, globe = Globe(Element.FIRE)))
        assertEquals("FW#..", b.describe()[4])
    }

    @Test
    fun globesArePackedAgainstTheWallAndNothingPassesIt() {
        val b = lastRow(".....", ".....", ".....", ".....", "W..#E")
        assertEquals(1, b.insert(4, fromLeft = true, globe = Globe(Element.FIRE)))
        assertEquals(".FW#E", b.describe()[4])
    }

    @Test
    fun aFullStretchInFrontOfAWallBlocksTheShot() {
        val b = lastRow(".....", ".....", ".....", ".....", "WE#..")
        assertFalse(b.canInsert(4, fromLeft = true))
        assertEquals(-1, b.insert(4, fromLeft = true, globe = Globe(Element.FIRE)))
        assertEquals("WE#..", b.describe()[4])
        // From the other side nothing stands in front of the wall, so the enemy shot breaks it.
        assertTrue(b.canInsert(4, fromLeft = false))
        assertEquals(2, b.insert(4, fromLeft = false, globe = Globe(Element.AIR)))
        assertEquals("WEA..", b.describe()[4])
    }

    @Test
    fun aWallRightAtTheEntryEdgeIsBrokenAtOnce() {
        val b = lastRow(".....", ".....", ".....", ".....", "#WE..")
        assertEquals(0, b.insert(4, fromLeft = true, globe = Globe(Element.FIRE)))
        assertEquals("FWE..", b.describe()[4])
    }

    @Test
    fun wallsStayPutAndGlobesRestOnThem() {
        val b = Board.parse("W....", ".....", "#....", ".....", "E....")
        assertTrue(b.applyGravity())
        assertEquals(listOf(".....", "W....", "#....", ".....", "E...."), b.describe())
        assertFalse(b.applyGravity())
    }

    @Test
    fun wallsRiseInEveryEmptyCellOfTheColumn() {
        val b = Board.parse(".....", ".....", ".....", ".F...", ".W...")
        assertEquals(listOf(Cell(0, 1), Cell(1, 1), Cell(2, 1)), b.raiseWalls(1))
        assertEquals(listOf(".#...", ".#...", ".#...", ".F...", ".W..."), b.describe())
        assertTrue(b.raiseWalls(1).isEmpty())
    }

    @Test
    fun anExplosionBreaksTheWallsBesideItInItsRowOnly() {
        val b = Board.parse(".....", ".....", ".....", ".#...", "#FFF#")
        val exploding = listOf(Cell(4, 1), Cell(4, 2), Cell(4, 3))
        assertEquals(setOf(Cell(4, 0), Cell(4, 4)), b.breakWallsBeside(exploding).toSet())
        assertTrue(b.isWall(3, 1))
        assertEquals(".FFF.", b.describe()[4])
    }

    @Test
    fun wallsBreakLinesOfEqualGlobes() {
        val b = Board.parse(".....", ".....", ".....", ".....", "FF#FF")
        assertTrue(b.findMatches().isEmpty())
    }
}
