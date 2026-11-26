package com.an0obis.magicduel.game

import kotlin.random.Random

data class Cell(val row: Int, val col: Int)

/** Range of the drawing-only `look` numbers of globes and projectiles. */
internal const val LOOKS = 1 shl 16

/**
 * A crystal globe on the field. Draw positions are in cell units and are animated by [Battle].
 * [look] only affects drawing: it picks the animation variant and where in its loop the globe starts.
 * A shot keeps the look of the projectile it was, so it doesn't change when it lands.
 */
class Globe(val element: Element, val look: Int = Random.nextInt(LOOKS)) {
    var drawCol = 0f
    var drawRow = 0f
    internal var fromCol = 0f
    internal var fromRow = 0f

    /** True while a shot is still sliding through its row; it becomes a crystal globe when it stops. */
    var inFlight = false

    /** Direction of the sliding shot: true for the player's shots (left to right). */
    var headingRight = true

    /** 1 right after the shot freezes into a globe, fading to 0. */
    var freeze = 0f

    /** True while the globe is part of a match that is exploding. */
    var popping = false
}

/** The shared 5×5 field. Row 0 is the top lane, column 0 is the player's (left) edge. */
class Board(val rows: Int = Rules.ROWS, val cols: Int = Rules.COLS) {
    private val cells = Array(rows) { arrayOfNulls<Globe>(cols) }

    operator fun get(row: Int, col: Int): Globe? = cells[row][col]
    operator fun set(row: Int, col: Int, globe: Globe?) {
        cells[row][col] = globe
    }

    fun canInsert(row: Int): Boolean = cells[row].any { it == null }

    /**
     * Sends a shot ([globe]) into [row] from the left edge (if [fromLeft]) or the right edge.
     * It travels as far as it can, pushing every globe ahead of it until they are packed
     * against the far edge, and freezes right behind them. In an empty row it reaches the far edge.
     * Returns the column where the shot froze, or -1 (changing nothing) when the row is full.
     */
    fun insert(row: Int, fromLeft: Boolean, globe: Globe): Int {
        val line = cells[row]
        val globes = line.filterNotNull()
        if (globes.size >= cols) return -1
        line.fill(null)
        return if (fromLeft) {
            val first = cols - globes.size
            globes.forEachIndexed { i, g -> line[first + i] = g }
            line[first - 1] = globe
            first - 1
        } else {
            globes.forEachIndexed { i, g -> line[i] = g }
            line[globes.size] = globe
            globes.size
        }
    }

    /**
     * A shot into a full [row] that pushes anyway: every globe moves one cell toward the far edge,
     * the shot takes the entry edge cell, and the globe that was at the far edge is pushed out of
     * the field and returned.
     */
    fun pushThroughFullRow(row: Int, fromLeft: Boolean, globe: Globe): Globe {
        val line = cells[row]
        check(line.all { it != null }) { "row $row is not full" }
        return if (fromLeft) {
            val out = line[cols - 1]!!
            for (c in cols - 1 downTo 1) line[c] = line[c - 1]
            line[0] = globe
            out
        } else {
            val out = line[0]!!
            for (c in 0 until cols - 1) line[c] = line[c + 1]
            line[cols - 1] = globe
            out
        }
    }

    /** Lets every globe fall into the empty cells below it. Returns true if anything moved. */
    fun applyGravity(): Boolean {
        var moved = false
        for (c in 0 until cols) {
            var target = rows - 1
            for (r in rows - 1 downTo 0) {
                val g = cells[r][c] ?: continue
                if (r != target) {
                    cells[target][c] = g
                    cells[r][c] = null
                    moved = true
                }
                target--
            }
        }
        return moved
    }

    /**
     * All cells that explode now. A horizontal or vertical line of 3+ equal elements explodes,
     * and so does every globe of the same element connected to it through shared sides
     * (directly or through other such globes).
     */
    fun findMatches(): Set<Cell> {
        val lines = findLines()
        if (lines.isEmpty()) return lines
        val exploding = HashSet(lines)
        val queue = ArrayDeque(lines)
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            val element = cells[cell.row][cell.col]?.element ?: continue
            for ((dr, dc) in NEIGHBOURS) {
                val r = cell.row + dr
                val c = cell.col + dc
                if (r !in 0 until rows || c !in 0 until cols) continue
                if (cells[r][c]?.element == element) {
                    val next = Cell(r, c)
                    if (exploding.add(next)) queue.addLast(next)
                }
            }
        }
        return exploding
    }

    /** Cells that are part of a horizontal or vertical line of 3+ equal elements. */
    private fun findLines(): Set<Cell> {
        val found = HashSet<Cell>()
        for (r in 0 until rows) {
            scanLine(cols, { cells[r][it]?.element }) { from, to -> for (c in from until to) found += Cell(r, c) }
        }
        for (c in 0 until cols) {
            scanLine(rows, { cells[it][c]?.element }) { from, to -> for (r in from until to) found += Cell(r, c) }
        }
        return found
    }

    private inline fun scanLine(length: Int, at: (Int) -> Element?, onRun: (Int, Int) -> Unit) {
        var start = 0
        while (start < length) {
            val e = at(start)
            var end = start + 1
            if (e != null) {
                while (end < length && at(end) == e) end++
                if (end - start >= 3) onRun(start, end)
            }
            start = end
        }
    }

    fun remove(targets: Collection<Cell>) {
        for (cell in targets) cells[cell.row][cell.col] = null
    }

    inline fun forEachGlobe(action: (row: Int, col: Int, globe: Globe) -> Unit) {
        for (r in 0 until rows) for (c in 0 until cols) get(r, c)?.let { action(r, c, it) }
    }

    /** Rows as text such as "F.WAE" (used by tests and for debugging). */
    fun describe(): List<String> = (0 until rows).map { r ->
        (0 until cols).joinToString("") { c -> cells[r][c]?.let { code(it.element).toString() } ?: "." }
    }

    companion object {
        private val NEIGHBOURS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        private val codes = mapOf(
            'F' to Element.FIRE, 'W' to Element.WATER, 'E' to Element.EARTH,
            'A' to Element.AIR, 'L' to Element.LIGHTNING, 'S' to Element.SHADOW,
        )

        fun code(e: Element): Char = codes.entries.first { it.value == e }.key

        /** Builds a board from rows of text, e.g. parse(".....", "..F..", ...). */
        fun parse(vararg lines: String): Board {
            val board = Board(lines.size, lines[0].length)
            lines.forEachIndexed { r, line ->
                line.forEachIndexed { c, ch ->
                    codes[ch]?.let { board[r, c] = Globe(it).apply { drawCol = c.toFloat(); drawRow = r.toFloat() } }
                }
            }
            return board
        }

        /** A partly filled starting field: 1–3 globes per column, resting on the floor, with no matches. */
        fun randomStart(random: Random, elements: List<Element>, rows: Int = Rules.ROWS, cols: Int = Rules.COLS): Board {
            val board = Board(rows, cols)
            val heights = IntArray(cols) { 1 + random.nextInt(3) }
            for (r in rows - 1 downTo 0) for (c in 0 until cols) {
                if (r < rows - heights[c]) continue
                val element = elements.shuffled(random).first { e ->
                    val lineLeft = c >= 2 && board[r, c - 1]?.element == e && board[r, c - 2]?.element == e
                    val lineBelow = r <= rows - 3 && board[r + 1, c]?.element == e && board[r + 2, c]?.element == e
                    !lineLeft && !lineBelow
                }
                board[r, c] = Globe(element).apply { drawCol = c.toFloat(); drawRow = r.toFloat() }
            }
            return board
        }
    }
}
