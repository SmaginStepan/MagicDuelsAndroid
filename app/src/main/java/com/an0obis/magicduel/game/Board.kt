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

/**
 * The shared 5×5 field. Row 0 is the top lane, column 0 is the player's (left) edge.
 * A cell holds a globe, a wall or nothing. Walls are conjured magic: they fall like globes (and
 * globes rest on them), and they break when a shot's push reaches them or a globe next to them
 * (left, right, above or below) explodes.
 */
class Board(val rows: Int = Rules.ROWS, val cols: Int = Rules.COLS) {
    private val cells = Array(rows) { arrayOfNulls<Globe>(cols) }
    private val walls = Array(rows) { BooleanArray(cols) }

    // Drawing only: which look (art variant) each wall has; it moves with the wall when it falls.
    private val wallLooks = Array(rows) { IntArray(cols) }

    /** The look of the wall at ([row], [col]): picks its art variant. Kept after the wall breaks, for the shatter. */
    fun wallLook(row: Int, col: Int): Int = wallLooks[row][col]

    operator fun get(row: Int, col: Int): Globe? = cells[row][col]
    operator fun set(row: Int, col: Int, globe: Globe?) {
        cells[row][col] = globe
    }

    fun isWall(row: Int, col: Int): Boolean = walls[row][col]

    fun isEmpty(row: Int, col: Int): Boolean = cells[row][col] == null && !walls[row][col]

    /**
     * Conjures walls in every empty cell of [col]. Returns those cells (top to bottom); none if the
     * column is already full.
     */
    fun raiseWalls(col: Int): List<Cell> {
        val raised = ArrayList<Cell>()
        for (r in 0 until rows) if (isEmpty(r, col)) {
            walls[r][col] = true
            wallLooks[r][col] = Random.nextInt(LOOKS)
            raised += Cell(r, col)
        }
        return raised
    }

    fun removeWall(row: Int, col: Int) {
        walls[row][col] = false
    }

    /**
     * Walls right next to exploding globes (left, right, above or below), which the explosion breaks;
     * diagonal neighbours stay. Removes them and returns them.
     */
    fun breakWallsAround(exploding: Collection<Cell>): List<Cell> {
        val broken = LinkedHashSet<Cell>()
        for (cell in exploding) for ((dr, dc) in NEIGHBOURS) {
            val r = cell.row + dr
            val c = cell.col + dc
            if (r in 0 until rows && c in 0 until cols && walls[r][c]) broken += Cell(r, c)
        }
        broken.forEach { walls[it.row][it.col] = false }
        return broken.toList()
    }

    /** The first wall a shot entering [row] from that edge runs into, or -1 if there is none. */
    fun firstWall(row: Int, fromLeft: Boolean): Int {
        val range = if (fromLeft) 0 until cols else (cols - 1 downTo 0)
        return range.firstOrNull { walls[row][it] } ?: -1
    }

    /** The cells a shot entering [row] from that edge can move through: up to the far edge or the first wall. */
    private fun span(row: Int, fromLeft: Boolean): IntRange {
        val wall = firstWall(row, fromLeft)
        return when {
            wall < 0 -> 0 until cols
            fromLeft -> 0 until wall
            else -> wall + 1 until cols
        }
    }

    /**
     * True if a shot entering [row] from that edge has somewhere to go: an empty cell before the
     * first wall, or the wall itself with nothing in front of it (the shot breaks it).
     */
    fun canInsert(row: Int, fromLeft: Boolean): Boolean {
        val span = span(row, fromLeft)
        val free = span.any { cells[row][it] == null }
        return free || (firstWall(row, fromLeft) >= 0 && span.none { cells[row][it] != null })
    }

    /**
     * Sends a shot ([globe]) into [row] from the left edge (if [fromLeft]) or the right edge.
     * It travels as far as it can, pushing every globe ahead of it until they are packed against the
     * far edge, and freezes right behind them. In an empty row it reaches the far edge.
     * A wall works like the far edge: the globes are packed against it and it holds. Only a shot that
     * reaches a wall itself (nothing in front of it) breaks it and takes its cell, going no further;
     * the wall stays in [walls] until the screen has shown the hit, so call [removeWall] then.
     * Returns the column where the shot froze, or -1 (changing nothing) when it can't enter.
     */
    fun insert(row: Int, fromLeft: Boolean, globe: Globe): Int {
        if (!canInsert(row, fromLeft)) return -1
        val line = cells[row]
        val span = span(row, fromLeft)
        val globes = span.mapNotNull { line[it] }
        if (globes.isEmpty() && firstWall(row, fromLeft) >= 0) {
            val wall = firstWall(row, fromLeft)
            line[wall] = globe
            return wall
        }
        span.forEach { line[it] = null }
        return if (fromLeft) {
            val first = span.last + 1 - globes.size
            globes.forEachIndexed { i, g -> line[first + i] = g }
            line[first - 1] = globe
            first - 1
        } else {
            val first = span.first
            globes.forEachIndexed { i, g -> line[first + i] = g }
            line[first + globes.size] = globe
            first + globes.size
        }
    }

    /**
     * A shot into [row] whose stretch in front of the first wall is full ([canInsert] is false there),
     * from a caster that pushes anyway (the bot): the globes in front of the wall move one cell toward
     * it, the front one into the wall's cell, and the shot takes the entry edge cell. The push breaks
     * that wall; like [insert], it stays in [walls] until the screen has shown the hit, so call
     * [removeWall] for the column returned by [firstWall] then. Returns the shot's column.
     */
    fun pushThroughWall(row: Int, fromLeft: Boolean, globe: Globe): Int {
        val line = cells[row]
        val wall = firstWall(row, fromLeft)
        check(wall >= 0 && !canInsert(row, fromLeft)) { "row $row is not blocked by a wall" }
        return if (fromLeft) {
            for (c in wall downTo 1) line[c] = line[c - 1]
            line[0] = globe
            0
        } else {
            for (c in wall until cols - 1) line[c] = line[c + 1]
            line[cols - 1] = globe
            cols - 1
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

    /**
     * Lets every globe and every wall fall into the empty cells below it, keeping their order in the
     * column. A wall that falls remembers the row it fell from ([wallFromRow]) for the animation.
     * Returns true if anything moved.
     */
    fun applyGravity(): Boolean {
        var moved = false
        for (c in 0 until cols) {
            var target = rows - 1
            for (r in rows - 1 downTo 0) {
                val g = cells[r][c]
                val wall = walls[r][c]
                if (g == null && !wall) continue
                if (r != target) {
                    cells[target][c] = g
                    cells[r][c] = null
                    walls[target][c] = wall
                    if (wall) wallLooks[target][c] = wallLooks[r][c]
                    walls[r][c] = false
                    wallFrom[target][c] = if (wall) wallFrom[r][c] else target.toFloat()
                    moved = true
                }
                target--
            }
        }
        return moved
    }

    // Where each wall was before the last fall (in rows), for drawing it falling.
    private val wallFrom = Array(rows) { r -> FloatArray(cols) { r.toFloat() } }

    /** The row the wall now at ([row], [col]) fell from in the last [applyGravity], or [row] itself. */
    fun wallFromRow(row: Int, col: Int): Float = wallFrom[row][col]

    /** Every wall is where it is drawn: forget where walls fell from (before a new board step). */
    fun settleWalls() {
        for (r in 0 until rows) for (c in 0 until cols) wallFrom[r][c] = r.toFloat()
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

    /** Rows as text such as "F.W#E", # a wall (used by tests and for debugging). */
    fun describe(): List<String> = (0 until rows).map { r ->
        (0 until cols).joinToString("") { c ->
            cells[r][c]?.let { code(it.element).toString() } ?: if (walls[r][c]) "#" else "."
        }
    }

    companion object {
        private val NEIGHBOURS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        private val codes = mapOf(
            'F' to Element.FIRE, 'W' to Element.WATER, 'E' to Element.EARTH,
            'A' to Element.AIR, 'L' to Element.LIGHTNING, 'S' to Element.SHADOW,
        )

        fun code(e: Element): Char = codes.entries.first { it.value == e }.key

        /** Builds a board from rows of text, e.g. parse(".....", "..F#.", ...), # a wall. */
        fun parse(vararg lines: String): Board {
            val board = Board(lines.size, lines[0].length)
            lines.forEachIndexed { r, line ->
                line.forEachIndexed { c, ch ->
                    codes[ch]?.let { board[r, c] = Globe(it).apply { drawCol = c.toFloat(); drawRow = r.toFloat() } }
                    if (ch == '#') {
                        board.walls[r][c] = true
                        board.wallLooks[r][c] = Random.nextInt(LOOKS)
                    }
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
