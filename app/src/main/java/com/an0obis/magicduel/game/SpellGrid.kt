package com.an0obis.magicduel.game

import kotlin.random.Random

/**
 * The player's 5×3 grid of spells. Row r fires into lane r.
 * Taking a spell dissolves its whole row and column; they are refilled when the cast completes.
 */
class SpellGrid(private val random: Random, private val elements: List<Element>) {
    val rows = Rules.GRID_ROWS
    val cols = Rules.GRID_COLS
    private val spells = Array(rows) { Array(cols) { elements.random(random) } }
    private val refreshing = Array(rows) { BooleanArray(cols) }

    operator fun get(row: Int, col: Int): Element = spells[row][col]
    operator fun set(row: Int, col: Int, element: Element) {
        spells[row][col] = element
    }

    fun isAvailable(row: Int, col: Int): Boolean = !refreshing[row][col]

    /** Takes the spell at (row, col); its row and column dissolve until [refill]. */
    fun take(row: Int, col: Int): Element {
        for (c in 0 until cols) refreshing[row][c] = true
        for (r in 0 until rows) refreshing[r][col] = true
        return spells[row][col]
    }

    /** The cast was cancelled: the dissolved spells come back unchanged. Returns those cells. */
    fun restore(): List<Cell> {
        val restored = ArrayList<Cell>()
        for (r in 0 until rows) for (c in 0 until cols) {
            if (refreshing[r][c]) {
                refreshing[r][c] = false
                restored += Cell(r, c)
            }
        }
        return restored
    }

    /** Fills every dissolved cell with a new random spell and returns those cells. */
    fun refill(): List<Cell> {
        val refilled = ArrayList<Cell>()
        for (r in 0 until rows) for (c in 0 until cols) {
            if (refreshing[r][c]) {
                spells[r][c] = elements.random(random)
                refreshing[r][c] = false
                refilled += Cell(r, c)
            }
        }
        return refilled
    }
}
