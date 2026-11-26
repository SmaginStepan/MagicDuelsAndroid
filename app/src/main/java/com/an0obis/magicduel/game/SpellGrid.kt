package com.an0obis.magicduel.game

import kotlin.random.Random

/**
 * The player's 5×3 grid of spells. Row r fires into lane r.
 * Taking a spell dissolves its whole row and column. Their new spells are chosen at once (see
 * [upcoming], so the screen can show them growing during the cast) and become usable when the cast
 * completes ([refill]); a cancelled cast brings the old spells back instead ([restore]).
 */
class SpellGrid(private val random: Random, private val elements: List<Element>) {
    val rows = Rules.GRID_ROWS
    val cols = Rules.GRID_COLS
    private val spells = Array(rows) { Array(cols) { elements.random(random) } }
    private val refreshing = Array(rows) { BooleanArray(cols) }
    private val next = Array(rows) { arrayOfNulls<Element>(cols) }

    operator fun get(row: Int, col: Int): Element = spells[row][col]
    operator fun set(row: Int, col: Int, element: Element) {
        spells[row][col] = element
    }

    fun isAvailable(row: Int, col: Int): Boolean = !refreshing[row][col]

    /** The new spell coming at a dissolved cell when the cast completes, or null if the cell is usable. */
    fun upcoming(row: Int, col: Int): Element? = if (refreshing[row][col]) next[row][col] else null

    /** Takes the spell at (row, col); its row and column dissolve, and their new spells are chosen. */
    fun take(row: Int, col: Int): Element {
        for (c in 0 until cols) refreshing[row][c] = true
        for (r in 0 until rows) refreshing[r][col] = true
        for (r in 0 until rows) for (c in 0 until cols) {
            if (refreshing[r][c] && next[r][c] == null) next[r][c] = elements.random(random)
        }
        return spells[row][col]
    }

    /** The cast was cancelled: the dissolved spells come back unchanged. Returns those cells. */
    fun restore(): List<Cell> {
        val restored = ArrayList<Cell>()
        for (r in 0 until rows) for (c in 0 until cols) {
            if (refreshing[r][c]) {
                refreshing[r][c] = false
                next[r][c] = null
                restored += Cell(r, c)
            }
        }
        return restored
    }

    /** Every dissolved cell gets the new spell chosen for it when the cast started; returns those cells. */
    fun refill(): List<Cell> {
        val refilled = ArrayList<Cell>()
        for (r in 0 until rows) for (c in 0 until cols) {
            if (refreshing[r][c]) {
                spells[r][c] = next[r][c] ?: elements.random(random)
                next[r][c] = null
                refreshing[r][c] = false
                refilled += Cell(r, c)
            }
        }
        return refilled
    }
}
