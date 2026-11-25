package com.an0obis.magicduel.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import com.an0obis.magicduel.game.AvatarKind
import com.an0obis.magicduel.game.Battle
import com.an0obis.magicduel.game.BattleEvent
import com.an0obis.magicduel.game.Element
import com.an0obis.magicduel.game.Opponents
import com.an0obis.magicduel.game.Projectile
import com.an0obis.magicduel.game.Rules
import com.an0obis.magicduel.game.Side
import com.an0obis.magicduel.game.Tutorial
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private object Pal {
    val SKY_TOP = 0xFF0B0A24.toInt()
    val SKY_BOTTOM = 0xFF2A1442.toInt()
    val PANEL = 0xE6130E2A.toInt()
    val PANEL_EDGE = 0xFF3E3170.toInt()
    val TILE = 0xFF231B42.toInt()
    val TILE_EMPTY = 0x991A1433.toInt()
    val CARD = 0xFF1A1236.toInt()
    val TEXT = 0xFFEDE7FF.toInt()
    val TEXT_DIM = 0xFF9C92C8.toInt()
    val PLAYER_HP = 0xFF39D3B0.toInt()
    val ENEMY_HP = 0xFFFF5A6E.toInt()
    val DAMAGE = 0xFFFF8A96.toInt()
    val HP_BACK = 0xFF2A2045.toInt()
    val HP_TRAIL = 0xFFFFF1C9.toInt()
    val PLAYER_ROBE = 0xFF2F6FD0.toInt()
}

/**
 * The whole game screen: draws the duel, runs the frame loop and turns taps into casts.
 * Layout is portrait and scales from one unit [u] (the size of a field cell).
 */
@SuppressLint("ViewConstructor")
class GameView(context: Context) : View(context), Choreographer.FrameCallback {

    private class HitGroup(val target: Side, val damage: Int) {
        var landed = false
    }

    private class Spark(
        val x0: Float, val y0: Float, val cx: Float, val cy: Float, val x1: Float, val y1: Float,
        val color: Int, val delay: Float, val duration: Float, val hit: HitGroup?,
    ) {
        var t = 0f
    }

    private class FloatText(val text: String, val x: Float, val y: Float, val color: Int, val size: Float, val duration: Float = 1.1f) {
        var t = 0f
    }

    private class Ring(val x: Float, val y: Float, val color: Int, val r0: Float, val r1: Float, val duration: Float) {
        var t = 0f
    }

    private class Bolt(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val color: Int) {
        var t = 0f
    }

    private val prefs = context.getSharedPreferences("magic_duel", Context.MODE_PRIVATE)
    private var level = prefs.getInt(KEY_LEVEL, 0).coerceAtLeast(0)
    private var tutorialDone = prefs.getBoolean(KEY_TUTORIAL_DONE, level > 0)
    private var played = prefs.getBoolean(KEY_PLAYED, level > 0)
    private var battle = Battle(Opponents.forLevel(level))
    private var tutorial: Tutorial? = null
    private val art = Art(context.resources, context.packageName)

    // ---- screens: the start menu (with small dialogs) and the duel ----
    private enum class Screen { MENU, BATTLE }
    private enum class Dialog { RESTART, LEADERBOARD, PVP, OPTIONS }
    private var screen = Screen.MENU
    private var dialog: Dialog? = null
    private var dialogOpenedAt = 0f
    private val menuButtons = LinkedHashMap<String, RectF>()
    private val dialogButtons = LinkedHashMap<String, RectF>()
    private val battleMenuButton = RectF()

    /** "Cancel cast" button above your mage; only shown (and tappable) while you are casting. */
    private val cancelCastButton = RectF()

    /** 1 right after the bot's queue moved up by one spell, easing to 0 (the chips slide left). */
    private var queueShift = 0f

    private var running = false
    private var lastFrameNanos = 0L
    private var clock = 0f
    private var endTimer = 0f

    // ---- layout, in pixels ----
    private var u = 1f
    private var left = 0f
    private var right = 0f
    private var fieldLeft = 0f
    private var fieldTop = 0f
    private var enemyTop = 0f
    private var gridLeft = 0f
    private var gridTop = 0f
    private var tile = 0f
    private var tileGap = 0f
    private var playerTop = 0f
    private var playerX = 0f
    private var playerFoot = 0f
    private var playerH = 0f
    private var enemyX = 0f
    private var enemyFoot = 0f
    private var enemyH = 0f
    private var safeTop = 0
    private var safeBottom = 0

    // ---- effects ----
    private val sparks = ArrayList<Spark>()
    private val texts = ArrayList<FloatText>()
    private val rings = ArrayList<Ring>()
    private val bolts = ArrayList<Bolt>()
    private val flash = FloatArray(2)
    private val shake = FloatArray(2)
    private val shownHp = FloatArray(2)
    private val trailHp = FloatArray(2)
    private val afterglow = FloatArray(2)
    private val lastCastColor = IntArray(2)
    private val tilePop = Array(Rules.GRID_ROWS) { FloatArray(Rules.GRID_COLS) { 1f } }
    private var tappedRow = -1
    private var tappedCol = -1

    /** Time since the training dummy was last hit (its hit animation is playing), or -1. */
    private var dummyHitT = -1f

    // ---- paints ----
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sky = Paint()
    private val rect = RectF()
    private val path = Path()
    private val stars = FloatArray(STAR_COUNT * 3)

    init {
        val r = Random(7)
        for (i in stars.indices) stars[i] = r.nextFloat()
        startBattle()
    }

    // ================================================================ lifecycle and loop

    fun resume() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastFrameNanos == 0L) 0f else ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNanos = frameTimeNanos
        step(dt)
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= 30) {
            val cut = insets.getInsets(WindowInsets.Type.displayCutout())
            val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            safeTop = max(cut.top, bars.top)
            safeBottom = max(cut.bottom, bars.bottom)
        } else if (Build.VERSION.SDK_INT >= 28) {
            safeTop = insets.displayCutout?.safeInsetTop ?: 0
            safeBottom = insets.displayCutout?.safeInsetBottom ?: 0
        }
        if (width > 0) layoutFor(width, height)
        return insets
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutFor(w, h)
    }

    private fun layoutFor(w: Int, h: Int) {
        val availH = (h - safeTop - safeBottom).toFloat()
        val contentUnits = 5f + 2f * FLIGHT_ZONE
        u = min(w / contentUnits, availH / 16f)
        val contentW = u * contentUnits
        left = (w - contentW) / 2f
        right = left + contentW
        fieldLeft = left + FLIGHT_ZONE * u
        enemyTop = safeTop + 0.3f * u

        tile = 1.19f * u
        tileGap = 0.15f * u
        val gridH = 5 * tile + 4 * tileGap
        val gridW = 3 * tile + 2 * tileGap
        gridTop = h - safeBottom - 0.4f * u - gridH
        gridLeft = right - 0.3f * u - gridW
        playerTop = gridTop - 0.95f * u

        val enemyBottom = enemyTop + 2.45f * u
        fieldTop = (enemyBottom + playerTop) / 2f - 2.5f * u

        playerH = 3.7f * u
        playerX = left + (gridLeft - 0.7f * u - left) / 2f
        playerFoot = gridTop + gridH - 0.15f * u
        enemyH = 2.15f * u
        enemyX = right - 1.2f * u
        enemyFoot = enemyTop + 2.35f * u

        dashed.pathEffect = DashPathEffect(floatArrayOf(0.12f * u, 0.14f * u), 0f)
        sky.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Pal.SKY_TOP, Pal.SKY_BOTTOM, Shader.TileMode.CLAMP)
        text.setShadowLayer(0.08f * u, 0f, 0.03f * u, BLACK)
    }

    private fun resetEffects() {
        sparks.clear(); texts.clear(); rings.clear(); bolts.clear()
        endTimer = 0f
        tappedRow = -1
        tappedCol = -1
        for (side in Side.entries) {
            val i = side.ordinal
            shownHp[i] = battle.caster(side).maxHp.toFloat()
            trailHp[i] = shownHp[i]
            flash[i] = 0f
            shake[i] = 0f
            afterglow[i] = 0f
        }
        dummyHitT = -1f
        tilePop.forEach { it.fill(1f) }
        queueShift = 0f
    }

    /** A fresh duel at the current level; level 1 runs the tutorial until it has been beaten once. */
    private fun startBattle() {
        battle = Battle(Opponents.forLevel(level))
        tutorial = if (level == 0 && !tutorialDone) Tutorial() else null
        resetEffects()
    }

    private fun nextBattle() {
        if (battle.winner == Side.PLAYER) level++
        save()
        startBattle()
    }

    private fun save() {
        prefs.edit()
            .putInt(KEY_LEVEL, level)
            .putBoolean(KEY_TUTORIAL_DONE, tutorialDone)
            .putBoolean(KEY_PLAYED, played)
            .apply()
    }

    /** "Restart game": back to level 1, tutorial included. */
    private fun restartGame() {
        level = 0
        tutorialDone = false
        played = true
        save()
        startBattle()
        dialog = null
        screen = Screen.BATTLE
    }

    /** Android back: close a dialog, or leave the duel for the menu. Returns false to let the app close. */
    fun onBack(): Boolean = when {
        dialog != null -> { dialog = null; true }
        screen == Screen.BATTLE -> { screen = Screen.MENU; true }
        else -> false
    }

    // ================================================================ simulation step

    private fun step(dt: Float) {
        clock += dt
        if (screen != Screen.BATTLE) return // the duel waits while the menu is open
        battle.update(dt)
        for (event in battle.drainEvents()) onEvent(event)
        tutorial?.update(dt)
        queueShift = max(0f, queueShift - dt / QUEUE_SHIFT_TIME)

        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.t += dt
            if (s.t >= s.delay + s.duration) {
                s.hit?.let { group ->
                    if (!group.landed) {
                        group.landed = true
                        onHit(group.target, group.damage)
                    }
                }
                it.remove()
            }
        }
        texts.removeAll { t -> t.t += dt; t.t >= t.duration }
        rings.removeAll { r -> r.t += dt; r.t >= r.duration }
        bolts.removeAll { b -> b.t += dt; b.t >= BOLT_TIME }
        for (i in 0..1) {
            flash[i] = max(0f, flash[i] - dt * 3f)
            shake[i] = max(0f, shake[i] - dt * 3.5f)
            afterglow[i] = max(0f, afterglow[i] - dt * 3f)
        }
        for (row in tilePop) for (c in row.indices) row[c] = min(1f, row[c] + dt * 4f)
        if (dummyHitT >= 0f) {
            dummyHitT += dt
            if (dummyHitT * DUMMY_HIT_FPS >= Art.DUMMY_HIT_FRAMES) dummyHitT = -1f
        }

        for (side in Side.entries) {
            val i = side.ordinal
            val hp = battle.caster(side).hp.toFloat()
            if (sparks.none { s -> s.hit?.target == side }) shownHp[i] = min(shownHp[i], hp)
            trailHp[i] = if (trailHp[i] > shownHp[i]) {
                max(shownHp[i], trailHp[i] - dt * max(25f, (trailHp[i] - shownHp[i]) * 2.5f))
            } else {
                shownHp[i]
            }
        }
        if (battle.winner != null && sparks.isEmpty()) endTimer += dt
    }

    private fun onEvent(e: BattleEvent) {
        tutorial?.onEvent(e)
        if (e is BattleEvent.Launched && e.side == Side.ENEMY) queueShift = 1f
        if (e is BattleEvent.GameOver && e.winner == Side.PLAYER && tutorial != null) {
            tutorialDone = true
            save()
        }
        when (e) {
            is BattleEvent.CastStarted -> Unit
            is BattleEvent.CastCancelled -> {
                // The dissolved spells pop back in; the staff lowers without a shot.
                e.cells.forEach { tilePop[it.row][it.col] = 0f }
                tappedRow = -1
                tappedCol = -1
                afterglow[Side.PLAYER.ordinal] = 0f
                // Starts below where the button was, so it fades out before reaching your HP bar.
                texts += FloatText("Cancelled", cancelCastButton.centerX(), cancelCastButton.bottom + 0.1f * u, Pal.TEXT_DIM, 0.32f * u, 0.8f)
            }
            is BattleEvent.Launched -> {
                val from = staffTip(e.side)
                bolts += Bolt(from.x, from.y, startX(e.side), laneY(e.lane), art.color(e.element))
                afterglow[e.side.ordinal] = 1f
                lastCastColor[e.side.ordinal] = art.color(e.element)
            }
            is BattleEvent.Landed -> rings += Ring(
                cellX(e.col.toFloat()), cellY(e.row.toFloat()),
                mix(art.color(e.element), WHITE, 0.65f), 0.25f * u, 0.8f * u, 0.35f,
            )
            is BattleEvent.Backfired -> texts += FloatText("Backfire!", endX(e.side), laneY(e.lane) - 0.45f * u, Pal.ENEMY_HP, 0.38f * u, 0.9f)
            is BattleEvent.BackfireHit -> onHit(e.side, e.damage)
            is BattleEvent.Matched -> spawnMatch(e)
            is BattleEvent.SpellsRefilled -> e.cells.forEach { tilePop[it.row][it.col] = 0f }
            is BattleEvent.PushedOut -> texts += FloatText(
                "Pushed out!", cellX(if (e.by == Side.ENEMY) 0f else 4f), laneY(e.lane) - 0.55f * u, Pal.DAMAGE, 0.36f * u, 0.9f,
            )
            is BattleEvent.PushOutHit -> onHit(e.target, e.damage)
            is BattleEvent.GameOver -> Unit
        }
    }

    private fun spawnMatch(e: BattleEvent.Matched) {
        val target = chest(e.target)
        val group = HitGroup(e.target, e.damage)
        e.cells.forEachIndexed { i, cell ->
            val x = cellX(cell.col.toFloat())
            val y = cellY(cell.row.toFloat())
            val col = art.color(e.elements[i])
            rings += Ring(x, y, col, 0.2f * u, 0.9f * u, 0.4f)
            val dx = target.x - x
            val dy = target.y - y
            val len = max(1f, hypot(dx, dy))
            repeat(2) { k ->
                val bend = (if ((i + k) % 2 == 0) 1f else -1f) * (1.2f + Random.nextFloat()) * u
                val mx = (x + target.x) / 2f - dy / len * bend
                val my = (y + target.y) / 2f + dx / len * bend
                val delay = Rules.POP_TIME * 0.6f + i * 0.03f + k * 0.05f
                sparks += Spark(x, y, mx, my, target.x, target.y, col, delay, 0.55f + Random.nextFloat() * 0.15f, group)
            }
        }
        if (e.chain >= 2) {
            texts += FloatText("Chain ×${e.chain}!", fieldLeft + 2.5f * u, fieldTop + 2.3f * u, GOLD, 0.6f * u, 1.2f)
        }
    }

    private fun onHit(side: Side, damage: Int) {
        val i = side.ordinal
        shownHp[i] = max(battle.caster(side).hp.toFloat(), shownHp[i] - damage)
        flash[i] = 1f
        // The training dummy plays its hit reaction (restarting if it is hit again).
        if (side == Side.ENEMY && battle.opponent.kind == AvatarKind.DUMMY) dummyHitT = 0f
        shake[i] = 1f
        val h = if (side == Side.PLAYER) playerH else enemyH
        val c = chest(side)
        texts += FloatText("-$damage", c.x, c.y - h * 0.45f, if (side == Side.PLAYER) Pal.DAMAGE else GOLD, 0.6f * u)
        if (side == Side.PLAYER) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    // ================================================================ input

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN) return true
        val x = event.getX(event.actionIndex)
        val y = event.getY(event.actionIndex)
        if (screen == Screen.MENU) {
            onMenuTap(x, y)
            return true
        }
        if (RectF(battleMenuButton).apply { inset(-0.2f * u, -0.2f * u) }.contains(x, y)) {
            screen = Screen.MENU
            return true
        }
        if (battle.winner != null) {
            if (endTimer > TAP_DELAY) nextBattle()
            return true
        }
        if (battle.player.isCasting && RectF(cancelCastButton).apply { inset(-0.15f * u, -0.15f * u) }.contains(x, y)) {
            if (battle.cancelCast()) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            return true
        }
        if (x < gridLeft || y < gridTop) return true
        val stepSize = tile + tileGap
        val col = ((x - gridLeft) / stepSize).toInt()
        val row = ((y - gridTop) / stepSize).toInt()
        if (row !in 0 until Rules.GRID_ROWS || col !in 0 until Rules.GRID_COLS) return true
        if (battle.tryCast(row, col)) {
            tappedRow = row
            tappedCol = col
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        return true
    }

    private fun onMenuTap(x: Float, y: Float) {
        val open = dialog
        if (open != null) {
            // The dialog's buttons sit where the menu's buttons were: ignore a quick double tap,
            // so "Restart game" can't be confirmed by accident.
            if (clock - dialogOpenedAt < DIALOG_TAP_GUARD) return
            when (dialogButtons.entries.firstOrNull { it.value.contains(x, y) }?.key) {
                "restart" -> restartGame()
                "cancel", "ok" -> dialog = null
                else -> if (open != Dialog.RESTART) dialog = null // "coming soon" closes on any tap
            }
            return
        }
        when (menuButtons.entries.firstOrNull { it.value.contains(x, y) }?.key) {
            "continue" -> {
                if (!played) {
                    played = true
                    save()
                }
                screen = Screen.BATTLE
            }
            "restart" -> dialog = Dialog.RESTART
            "leaderboard" -> dialog = Dialog.LEADERBOARD
            "pvp" -> dialog = Dialog.PVP
            "options" -> dialog = Dialog.OPTIONS
            else -> return
        }
        if (dialog != null) dialogOpenedAt = clock
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    // ================================================================ geometry

    private fun cellX(col: Float) = fieldLeft + (col + 0.5f) * u
    private fun cellY(row: Float) = fieldTop + (row + 0.5f) * u
    private fun laneY(lane: Int) = cellY(lane.toFloat())
    private fun startX(side: Side) = if (side == Side.PLAYER) left + 0.5f * u else right - 0.5f * u
    /** Where a shot reaches the field: one cell outside the edge, where it starts sliding in. */
    private fun endX(side: Side) = if (side == Side.PLAYER) fieldLeft - 0.5f * u else fieldLeft + 5.5f * u
    private fun chest(side: Side) =
        if (side == Side.PLAYER) PointF(playerX, playerFoot - playerH * 0.5f) else PointF(enemyX, enemyFoot - enemyH * 0.5f)

    private fun staffTip(side: Side): PointF =
        if (side == Side.PLAYER) art.mageOrb(playerX, playerFoot, playerH, true, 1f)
        else art.mageOrb(enemyX, enemyFoot, enemyH, false, 1f)

    // ================================================================ drawing

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sky)
        drawStars(c)
        if (screen == Screen.MENU) {
            drawMenu(c)
            return
        }
        drawEnemyPanel(c)
        drawEnemyAvatar(c)
        drawField(c)
        drawGlobes(c)
        drawRings(c)
        drawProjectiles(c)
        drawPlayerPanel(c)
        drawPlayerAvatar(c)
        drawCancelCast(c)
        drawSpellGrid(c)
        drawBolts(c)
        drawSparks(c)
        drawTexts(c)
        drawTutorial(c)
        drawBanner(c)
        drawEndOverlay(c)
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align, alpha: Float = 1f) {
        text.textSize = size
        text.color = withAlpha(color, (255 * alpha.coerceIn(0f, 1f)).toInt())
        text.textAlign = align
        c.drawText(s, x, y, text)
    }

    private fun drawStars(c: Canvas) {
        for (i in 0 until STAR_COUNT) {
            val x = stars[i * 3] * width
            val y = stars[i * 3 + 1] * height
            val k = stars[i * 3 + 2]
            val twinkle = 0.5f + 0.5f * sin(clock * (1f + k * 2f) + k * 20f)
            fill.color = withAlpha(WHITE, (30 + 110 * k * twinkle).toInt())
            c.drawCircle(x, y, (0.015f + 0.03f * k) * u, fill)
        }
    }

    private fun drawHpBar(c: Canvas, x: Float, y: Float, w: Float, h: Float, side: Side, color: Int) {
        val full = battle.caster(side).maxHp.toFloat()
        val i = side.ordinal
        val r = h / 2f
        rect.set(x, y, x + w, y + h)
        fill.color = Pal.HP_BACK
        c.drawRoundRect(rect, r, r, fill)
        if (trailHp[i] > 0f) {
            rect.set(x, y, x + w * (trailHp[i] / full), y + h)
            fill.color = Pal.HP_TRAIL
            c.drawRoundRect(rect, r, r, fill)
        }
        if (shownHp[i] > 0f) {
            rect.set(x, y, x + w * (shownHp[i] / full), y + h)
            fill.color = mix(color, WHITE, flash[i] * 0.5f)
            c.drawRoundRect(rect, r, r, fill)
        }
        rect.set(x, y, x + w, y + h)
        stroke.color = withAlpha(WHITE, 60)
        stroke.strokeWidth = 0.03f * u
        c.drawRoundRect(rect, r, r, stroke)
    }

    private fun drawEnemyPanel(c: Canvas) {
        val x = left + 0.35f * u
        val w = min(5f * u, enemyX - 1.5f * u - x)
        // Long titles ("Grand Archmage …") shrink so they never run into the HP number.
        val hpText = "${ceil(shownHp[Side.ENEMY.ordinal]).toInt()}"
        text.textSize = 0.3f * u
        val hpWidth = text.measureText(hpText)
        text.textSize = 0.38f * u
        val nameWidth = text.measureText(battle.opponent.name)
        val room = w - hpWidth - 0.25f * u
        val nameSize = if (nameWidth > room) 0.38f * u * room / nameWidth else 0.38f * u
        label(c, battle.opponent.name, x, enemyTop + 0.42f * u, nameSize, Pal.TEXT, Paint.Align.LEFT)
        label(c, hpText, x + w, enemyTop + 0.42f * u, 0.3f * u, Pal.TEXT_DIM, Paint.Align.RIGHT)
        drawHpBar(c, x, enemyTop + 0.56f * u, w, 0.26f * u, Side.ENEMY, Pal.ENEMY_HP)

        val chipsTop = enemyTop + 1.08f * u
        if (battle.botQueue.isEmpty()) {
            label(c, "Training dummy: doesn't fire back", x, chipsTop + 0.42f * u, 0.27f * u, Pal.TEXT_DIM, Paint.Align.LEFT)
            return
        }
        val chip = 0.74f * u
        val gap = 0.14f * u
        val rr = 0.14f * u
        // The bot's plan: the first spell is cast next. When it has been cast, the queue slides left
        // and a newly planned spell fades in at the end.
        val slide = 1f - easeOut(1f - queueShift)
        battle.botQueue.forEachIndexed { i, shot ->
            val cx = x + (i + slide) * (chip + gap)
            val next = i == 0
            val col = art.color(shot.element)
            rect.set(cx, chipsTop, cx + chip, chipsTop + chip)
            val fadeIn = if (i == battle.botQueue.lastIndex) 1f - queueShift else 1f
            bitmapPaint.alpha = ((if (next) 255 else 170) * fadeIn).toInt()
            c.drawBitmap(art.button(shot.element, chip.toInt().coerceAtLeast(8)), null, rect, bitmapPaint)
            bitmapPaint.alpha = 255
            if (next) {
                stroke.strokeWidth = 0.06f * u
                stroke.color = col
                c.drawRoundRect(rect, rr, rr, stroke)
            }
            label(c, "${shot.lane + 1}", cx + chip - 0.1f * u, chipsTop + chip - 0.08f * u, 0.26f * u, WHITE, Paint.Align.RIGHT, fadeIn)
            if (next && battle.enemy.isCasting) {
                rect.set(cx - 0.06f * u, chipsTop - 0.06f * u, cx + chip + 0.06f * u, chipsTop + chip + 0.06f * u)
                stroke.color = WHITE
                stroke.strokeWidth = 0.05f * u
                c.drawArc(rect, -90f, 360f * battle.enemy.castProgress, false, stroke)
            }
        }
    }

    private fun drawEnemyAvatar(c: Canvas) {
        val lost = battle.winner == Side.PLAYER
        val k = if (lost) smooth01(endTimer / 0.8f) else 0f
        if (battle.opponent.kind == AvatarKind.DUMMY) {
            // The Midjourney sprite: its own hit animation when struck, no extra shaking.
            val frame = if (dummyHitT >= 0f) (dummyHitT * DUMMY_HIT_FPS).toInt() else -1
            art.drawDummySprite(c, enemyX, enemyFoot, enemyH * DUMMY_SPRITE_SCALE, frame, flash[1], DEFEAT_TILT * k, 1f - 0.6f * k)
            return
        }
        val x = enemyX + sin(clock * 55f) * shake[1] * 0.08f * u
        run {
            val e = battle.enemy
            val castColor = e.castElement?.let(art::color) ?: if (afterglow[1] > 0f) lastCastColor[1] else null
            val cast = if (e.isCasting) e.castProgress else afterglow[1]
            art.drawMage(c, x, enemyFoot, enemyH, false, battle.opponent.robeColor, true, castColor, cast, clock, flash[1], DEFEAT_TILT * k, 1f - 0.6f * k)
        }
    }

    private fun drawField(c: Canvas) {
        val fr = fieldLeft + 5f * u
        val fb = fieldTop + 5f * u
        dashed.color = withAlpha(WHITE, 34)
        dashed.strokeWidth = 0.035f * u
        for (lane in 0 until Rules.ROWS) {
            val y = laneY(lane)
            c.drawLine(left + 0.55f * u, y, fieldLeft - 0.2f * u, y, dashed)
            c.drawLine(fr + 0.2f * u, y, right - 0.55f * u, y, dashed)
            label(c, "${lane + 1}", left + 0.2f * u, y + 0.1f * u, 0.28f * u, Pal.TEXT_DIM, Paint.Align.CENTER)
            label(c, "${lane + 1}", right - 0.2f * u, y + 0.1f * u, 0.28f * u, Pal.TEXT_DIM, Paint.Align.CENTER)
        }
        // Telegraph: the lane a caster is aiming at lights up.
        for (side in Side.entries) {
            val caster = battle.caster(side)
            val element = caster.castElement ?: continue
            val y = laneY(caster.castLane)
            val col = art.color(element)
            val pulse = 0.5f + 0.5f * sin(clock * 10f)
            val x0 = if (side == Side.PLAYER) left + 0.4f * u else fr + 0.14f * u
            val x1 = if (side == Side.PLAYER) fieldLeft - 0.14f * u else right - 0.4f * u
            rect.set(x0, y - 0.4f * u, x1, y + 0.4f * u)
            fill.color = withAlpha(col, (25 + 50 * caster.castProgress * pulse).toInt())
            c.drawRoundRect(rect, 0.2f * u, 0.2f * u, fill)
            drawChevrons(c, (x0 + x1) / 2f, y, side == Side.PLAYER, col, caster.castProgress)
        }
        rect.set(fieldLeft - 0.14f * u, fieldTop - 0.14f * u, fr + 0.14f * u, fb + 0.14f * u)
        fill.color = Pal.PANEL
        c.drawRoundRect(rect, 0.3f * u, 0.3f * u, fill)
        stroke.color = Pal.PANEL_EDGE
        stroke.strokeWidth = 0.05f * u
        c.drawRoundRect(rect, 0.3f * u, 0.3f * u, stroke)
        fill.color = withAlpha(WHITE, 12)
        for (r in 0 until Rules.ROWS) for (col in 0 until Rules.COLS) {
            c.drawCircle(cellX(col.toFloat()), cellY(r.toFloat()), 0.36f * u, fill)
        }
    }

    private fun drawChevrons(c: Canvas, cx: Float, cy: Float, pointRight: Boolean, color: Int, k: Float) {
        val d = if (pointRight) 1f else -1f
        val spacing = 0.3f * u
        val drift = ((clock * 1.5f) % 1f) * spacing
        stroke.color = withAlpha(color, (120 + 135 * k).toInt())
        stroke.strokeWidth = 0.06f * u
        for (i in -1..1) {
            val x = cx + (i * spacing + drift - spacing / 2f) * d
            path.reset()
            path.moveTo(x - 0.1f * u * d, cy - 0.16f * u)
            path.lineTo(x + 0.06f * u * d, cy)
            path.lineTo(x - 0.1f * u * d, cy + 0.16f * u)
            c.drawPath(path, stroke)
        }
    }

    private fun drawGlobes(c: Canvas) {
        val size = (0.9f * u).toInt().coerceAtLeast(8)
        val popping = battle.phase == Battle.Phase.POP
        battle.board.forEachGlobe { _, _, g ->
            if (g.inFlight) {
                // Still a projectile sliding through the row; it freezes into a globe when it stops.
                drawShot(c, g.element, cellX(g.drawCol), cellY(g.drawRow), if (g.headingRight) 1f else -1f, 0f, g)
                return@forEachGlobe
            }
            var scale = 1f
            var alpha = 255
            if (g.popping && popping) {
                val p = battle.phaseProgress
                scale = 1f + 0.45f * p
                alpha = (255 * (1f - p)).toInt()
            }
            val x = cellX(g.drawCol)
            val y = cellY(g.drawRow)
            val half = size * scale / 2f
            rect.set(x - half, y - half, x + half, y + half)
            bitmapPaint.alpha = alpha
            c.drawBitmap(art.globe(g.element, size, animFrame(g)), null, rect, bitmapPaint)
            if (g.freeze > 0f) {
                fill.color = withAlpha(0xFFE8F6FF.toInt(), (190 * g.freeze).toInt())
                c.drawCircle(x, y, half * 0.9f, fill)
            }
        }
        bitmapPaint.alpha = 255
    }

    private fun drawRings(c: Canvas) {
        for (r in rings) {
            val k = r.t / r.duration
            stroke.color = withAlpha(r.color, (220 * (1f - k)).toInt())
            stroke.strokeWidth = 0.07f * u * (1f - k) + 0.01f * u
            c.drawCircle(r.x, r.y, r.r0 + (r.r1 - r.r0) * easeOut(k), stroke)
        }
    }

    /**
     * Draws the flying-projectile image with its head at ([x], [y]), travelling in the direction
     * ([dx], [dy]): the image (head on the right, tail on the left) is mirrored and turned to face
     * that way. No extra motion is added here — the only animation is the projectile's own loop,
     * whose head is locked in place, so the head moves exactly as the caller moves it.
     */
    private fun drawShot(c: Canvas, e: Element, x: Float, y: Float, dx: Float, dy: Float, seed: Any?, alpha: Float = 1f) {
        val bmp = art.projectile(e, (SHOT_HEIGHT * u).toInt().coerceAtLeast(8), animFrame(seed))
        val h = bmp.height.toFloat()
        val w = bmp.width.toFloat()
        // The head sits at the right end of the image, about half its height in from the edge.
        val headInset = h * 0.5f
        c.save()
        c.translate(x, y)
        val degrees = if (dx < 0f) {
            c.scale(-1f, 1f)
            Math.toDegrees(atan2(dy.toDouble(), (-dx).toDouble())).toFloat()
        } else {
            Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }
        if (degrees != 0f) c.rotate(degrees)
        rect.set(headInset - w, -h / 2f, headInset, h / 2f)
        bitmapPaint.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        c.drawBitmap(bmp, null, rect, bitmapPaint)
        bitmapPaint.alpha = 255
        c.restore()
    }

    /**
     * The current frame of a looping Midjourney animation. Each object ([seed]) starts at its own
     * point in the loop, so neighbouring globes don't flicker in sync.
     */
    private fun animFrame(seed: Any?): Int =
        (clock * ANIM_FPS).toInt() + (if (seed == null) 0 else System.identityHashCode(seed) % (Art.ANIM_COLS * Art.ANIM_ROWS))

    private fun drawProjectiles(c: Canvas) {
        for (p in battle.projectiles) {
            val sx = startX(p.owner)
            val ex = endX(p.owner)
            val laneY = laneY(p.lane)
            val dir = if (p.owner.fromLeft) 1f else -1f
            // Every path is a straight line travelled at constant speed; the projectile's own loop is
            // its only animation (it fades in during the first moment, without moving or growing).
            when (p.state) {
                Projectile.State.FLYING -> drawShot(
                    c, p.element, sx + (ex - sx) * p.progress, laneY, dir, 0f, p, min(1f, p.flown / SHOT_FADE_IN),
                )
                // The field is still moving: the shot holds at its edge until it can slide in.
                Projectile.State.WAITING -> drawShot(c, p.element, ex, laneY, dir, 0f, p)
                Projectile.State.BOUNCING -> {
                    // Backfire: the shot turns around and flies straight at its own caster.
                    val target = chest(p.owner)
                    val t = p.bounceProgress
                    drawShot(
                        c, p.element, ex + (target.x - ex) * t, laneY + (target.y - laneY) * t,
                        target.x - ex, target.y - laneY, p,
                    )
                }
                Projectile.State.EJECTED -> {
                    // A crystal globe shoved off the far edge of a full row flies straight at the opponent.
                    val target = chest(p.owner.opponent)
                    val from = cellX(if (p.owner.fromLeft) 4f else 0f)
                    val t = p.bounceProgress
                    val x = from + (target.x - from) * t
                    val y = laneY + (target.y - laneY) * t
                    val half = (0.9f * u).toInt().coerceAtLeast(8) / 2f
                    rect.set(x - half, y - half, x + half, y + half)
                    c.drawBitmap(art.globe(p.element, (2 * half).toInt(), animFrame(p)), null, rect, bitmapPaint)
                }
                Projectile.State.DONE -> Unit
            }
        }
    }

    private fun drawPlayerPanel(c: Canvas) {
        val x = left + 0.35f * u
        val w = gridLeft - 0.9f * u - x
        label(c, "You", x, playerTop + 0.32f * u, 0.38f * u, Pal.TEXT, Paint.Align.LEFT)
        label(c, "${ceil(shownHp[Side.PLAYER.ordinal]).toInt()}", x + w, playerTop + 0.32f * u, 0.3f * u, Pal.TEXT_DIM, Paint.Align.RIGHT)
        drawHpBar(c, x, playerTop + 0.46f * u, w, 0.26f * u, Side.PLAYER, Pal.PLAYER_HP)

        // "Menu" button at the top right of the player panel, with the level next to it.
        // Everything here is scaled by MENU_SCALE (1.5 = 50% bigger than the original design).
        val k = MENU_SCALE
        val bw = 1.6f * u * k
        val bh = 0.56f * u * k
        val bx1 = right - 0.3f * u
        val by0 = playerTop
        battleMenuButton.set(bx1 - bw, by0, bx1, by0 + bh)
        fill.color = Pal.TILE
        c.drawRoundRect(battleMenuButton, bh / 2f, bh / 2f, fill)
        stroke.color = Pal.PANEL_EDGE
        stroke.strokeWidth = 0.035f * u * k
        c.drawRoundRect(battleMenuButton, bh / 2f, bh / 2f, stroke)
        val ix = battleMenuButton.left + 0.36f * u * k
        val iy = battleMenuButton.centerY()
        stroke.color = Pal.TEXT
        stroke.strokeWidth = 0.045f * u * k
        for (i in -1..1) c.drawLine(ix - 0.13f * u * k, iy + i * 0.1f * u * k, ix + 0.13f * u * k, iy + i * 0.1f * u * k, stroke)
        label(c, "Menu", battleMenuButton.left + 0.6f * u * k, iy + 0.1f * u * k, 0.28f * u * k, Pal.TEXT, Paint.Align.LEFT)
        label(c, "Level ${level + 1}", battleMenuButton.left - MENU_LABEL_GAP * u, iy + 0.13f * u * k, 0.26f * u * k, Pal.TEXT_DIM, Paint.Align.RIGHT)
    }

    /**
     * "Cancel cast" button above your mage's head. It exists only while a cast is in progress:
     * it fades in when you tap a spell and is gone as soon as the cast is fired or cancelled.
     */
    private fun drawCancelCast(c: Canvas) {
        val p = battle.player
        val w = 3.0f * u
        val h = 0.8f * u
        val top = playerTop + 1.15f * u
        cancelCastButton.set(playerX - w / 2f, top, playerX + w / 2f, top + h)
        if (!p.isCasting || battle.winner != null) return
        val a = min(1f, p.castProgress * p.castTime / CANCEL_FADE_IN)
        val r = h / 2f
        fill.color = withAlpha(Pal.CARD, (235 * a).toInt())
        c.drawRoundRect(cancelCastButton, r, r, fill)
        stroke.color = withAlpha(Pal.ENEMY_HP, (255 * a).toInt())
        stroke.strokeWidth = 0.045f * u
        c.drawRoundRect(cancelCastButton, r, r, stroke)
        // ✕ drawn as two lines, then the label.
        val cx = cancelCastButton.left + 0.45f * u
        val cy = cancelCastButton.centerY()
        val s = 0.14f * u
        stroke.strokeWidth = 0.06f * u
        c.drawLine(cx - s, cy - s, cx + s, cy + s, stroke)
        c.drawLine(cx + s, cy - s, cx - s, cy + s, stroke)
        label(c, "Cancel cast", cancelCastButton.left + 0.8f * u, cy + 0.12f * u, 0.34f * u, Pal.TEXT, Paint.Align.LEFT, a)
    }

    // ================================================================ tutorial, menu and dialogs

    /** Splits [s] into lines that fit [maxWidth] at text size [size]. */
    private fun wrap(s: String, size: Float, maxWidth: Float): List<String> {
        text.textSize = size
        val lines = ArrayList<String>()
        var line = ""
        for (word in s.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isEmpty() || text.measureText(candidate) <= maxWidth) line = candidate
            else {
                lines += line
                line = word
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    /** The tutorial's current hint, in a card between the field and your panel. */
    private fun drawTutorial(c: Canvas) {
        val t = tutorial ?: return
        val hint = t.hint ?: return
        if (!battle.started || battle.winner != null) return
        val pulse = 0.5f + 0.5f * sin(clock * 5f)
        if (t.pointsAtSpells) {
            val gw = 3 * tile + 2 * tileGap
            val gh = 5 * tile + 4 * tileGap
            rect.set(gridLeft - 0.12f * u, gridTop - 0.12f * u, gridLeft + gw + 0.12f * u, gridTop + gh + 0.12f * u)
            stroke.color = withAlpha(GOLD, (90 + 140 * pulse).toInt())
            stroke.strokeWidth = 0.06f * u
            c.drawRoundRect(rect, 0.3f * u, 0.3f * u, stroke)
        }
        val size = 0.28f * u
        val lineH = 0.38f * u
        val pad = 0.2f * u
        val x0 = left + 0.3f * u
        val x1 = right - 0.3f * u
        val lines = wrap(hint, size, x1 - x0 - 2 * pad)
        val bottom = playerTop - 0.1f * u
        val top = bottom - (lines.size * lineH + 2 * pad - 0.06f * u)
        rect.set(x0, top, x1, bottom)
        fill.color = withAlpha(Pal.CARD, 235)
        c.drawRoundRect(rect, 0.22f * u, 0.22f * u, fill)
        stroke.color = withAlpha(GOLD, (110 + 80 * pulse).toInt())
        stroke.strokeWidth = 0.035f * u
        c.drawRoundRect(rect, 0.22f * u, 0.22f * u, stroke)
        lines.forEachIndexed { i, s -> label(c, s, x0 + pad, top + pad + (i + 0.78f) * lineH, size, Pal.TEXT, Paint.Align.LEFT) }
    }

    private fun drawButton(c: Canvas, r: RectF, title: String, sub: String?, primary: Boolean, small: Boolean, danger: Boolean = false) {
        val rr = 0.25f * u
        fill.color = if (primary) mix(Pal.CARD, GOLD, 0.12f) else Pal.CARD
        c.drawRoundRect(r, rr, rr, fill)
        stroke.color = when {
            danger -> Pal.ENEMY_HP
            primary -> GOLD
            else -> Pal.PANEL_EDGE
        }
        stroke.strokeWidth = (if (primary) 0.05f else 0.035f) * u
        c.drawRoundRect(r, rr, rr, stroke)
        val size = when {
            small -> 0.3f * u
            primary -> 0.46f * u
            else -> 0.38f * u
        }
        val color = when {
            danger -> Pal.ENEMY_HP
            primary -> GOLD
            else -> Pal.TEXT
        }
        if (sub == null) {
            label(c, title, r.centerX(), r.centerY() + size * 0.35f, size, color, Paint.Align.CENTER)
        } else {
            label(c, title, r.centerX(), r.centerY() - 0.04f * u, size, color, Paint.Align.CENTER)
            label(c, sub, r.centerX(), r.centerY() + size * 0.85f, (if (small) 0.22f else 0.26f) * u, Pal.TEXT_DIM, Paint.Align.CENTER)
        }
    }

    /** Start menu: continue or restart, plus leaderboard, PvP and options (coming later). */
    private fun drawMenu(c: Canvas) {
        val cx = width / 2f
        val contentH = 9.4f * u
        val availH = height - safeTop - safeBottom
        val top = safeTop + max(0.6f * u, (availH - contentH) * 0.4f)
        val titleY = top + 1.1f * u
        label(c, "Magic Duel", cx, titleY, 1.05f * u, GOLD, Paint.Align.CENTER)
        label(c, "A duel of the elements", cx, titleY + 0.7f * u, 0.34f * u, Pal.TEXT_DIM, Paint.Align.CENTER)

        val elements = Element.active(Rules.START_ELEMENTS)
        val gs = (1.2f * u).toInt().coerceAtLeast(8)
        val gap = 0.3f * u
        val rowW = elements.size * gs + (elements.size - 1) * gap
        var gx = cx - rowW / 2f
        val gy = titleY + 1.3f * u
        for (e in elements) {
            val bob = sin(clock * 2f + e.ordinal) * 0.07f * u
            rect.set(gx, gy + bob, gx + gs, gy + gs + bob)
            c.drawBitmap(art.globe(e, gs, animFrame(e)), null, rect, bitmapPaint)
            gx += gs + gap
        }

        menuButtons.clear()
        val bw = min(width - 1.8f * u, 6.6f * u)
        val bx = cx - bw / 2f
        var by = gy + gs + 1.0f * u
        val opponent = Opponents.forLevel(level)
        val sub = "Level ${level + 1} · ${opponent.name}" + if (level == 0 && !tutorialDone) " · tutorial" else ""
        val cont = RectF(bx, by, bx + bw, by + 1.25f * u)
        drawButton(c, cont, if (played) "Continue" else "Start game", sub, primary = true, small = false)
        menuButtons["continue"] = cont
        by = cont.bottom + 0.3f * u
        val restart = RectF(bx, by, bx + bw, by + 0.95f * u)
        drawButton(c, restart, "Restart game", null, primary = false, small = false)
        menuButtons["restart"] = restart
        by = restart.bottom + 0.55f * u
        val sgap = 0.25f * u
        val sw = (bw - 2 * sgap) / 3f
        listOf("leaderboard" to "Leaderboard", "pvp" to "PvP duel", "options" to "Options").forEachIndexed { i, (id, title) ->
            val r = RectF(bx + i * (sw + sgap), by, bx + i * (sw + sgap) + sw, by + 0.9f * u)
            drawButton(c, r, title, "soon", primary = false, small = true)
            menuButtons[id] = r
        }
        dialog?.let { drawDialog(c, it) }
    }

    private fun drawDialog(c: Canvas, d: Dialog) {
        dialogButtons.clear()
        fill.color = withAlpha(BLACK, 170)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val (title, message) = when (d) {
            Dialog.RESTART -> "Restart game?" to "You go back to level 1 and the tutorial starts again. Your current progress is lost."
            Dialog.LEADERBOARD -> "Leaderboard" to "Coming soon: compare your best level with other duellists."
            Dialog.PVP -> "PvP duels" to "Coming soon: duel other players online."
            Dialog.OPTIONS -> "Options" to "Coming soon: sound, vibration and more."
        }
        val w = min(width - 1.6f * u, 7f * u)
        val lines = wrap(message, 0.32f * u, w - 0.8f * u)
        val h = 1.2f * u + lines.size * 0.46f * u + 1.6f * u
        val cx = width / 2f
        val cy = height / 2f
        val card = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        fill.color = Pal.CARD
        c.drawRoundRect(card, 0.35f * u, 0.35f * u, fill)
        stroke.color = GOLD
        stroke.strokeWidth = 0.05f * u
        c.drawRoundRect(card, 0.35f * u, 0.35f * u, stroke)
        label(c, title, cx, card.top + 0.9f * u, 0.5f * u, GOLD, Paint.Align.CENTER)
        lines.forEachIndexed { i, s -> label(c, s, cx, card.top + 1.55f * u + i * 0.46f * u, 0.32f * u, Pal.TEXT, Paint.Align.CENTER) }
        val bTop = card.bottom - 1.1f * u
        if (d == Dialog.RESTART) {
            val half = (w - 0.9f * u) / 2f
            val yes = RectF(card.left + 0.3f * u, bTop, card.left + 0.3f * u + half, bTop + 0.8f * u)
            val no = RectF(card.right - 0.3f * u - half, bTop, card.right - 0.3f * u, bTop + 0.8f * u)
            drawButton(c, yes, "Restart", null, primary = false, small = true, danger = true)
            drawButton(c, no, "Cancel", null, primary = false, small = true)
            dialogButtons["restart"] = yes
            dialogButtons["cancel"] = no
        } else {
            val ok = RectF(cx - 1.4f * u, bTop, cx + 1.4f * u, bTop + 0.8f * u)
            drawButton(c, ok, "OK", null, primary = false, small = true)
            dialogButtons["ok"] = ok
        }
    }

    private fun drawPlayerAvatar(c: Canvas) {
        val lost = battle.winner == Side.ENEMY
        val k = if (lost) smooth01(endTimer / 0.8f) else 0f
        val p = battle.player
        val castColor = p.castElement?.let(art::color) ?: if (afterglow[0] > 0f) lastCastColor[0] else null
        val cast = if (p.isCasting) p.castProgress else afterglow[0]
        val x = playerX + sin(clock * 55f) * shake[0] * 0.08f * u
        art.drawMage(c, x, playerFoot, playerH, true, Pal.PLAYER_ROBE, false, castColor, cast, clock, flash[0], DEFEAT_TILT * k, 1f - 0.6f * k)
    }

    private fun drawSpellGrid(c: Canvas) {
        val spells = battle.spells
        val caster = battle.player
        val locked = caster.isCasting || !battle.started || battle.winner != null
        val rr = 0.2f * u
        for (r in 0 until spells.rows) {
            val y0 = gridTop + r * (tile + tileGap)
            val cy = y0 + tile / 2f
            val bx = gridLeft - 0.36f * u
            fill.color = Pal.TILE
            c.drawCircle(bx, cy, 0.24f * u, fill)
            label(c, "${r + 1}", bx, cy + 0.1f * u, 0.28f * u, Pal.TEXT_DIM, Paint.Align.CENTER)
            for (col in 0 until spells.cols) {
                val x0 = gridLeft + col * (tile + tileGap)
                rect.set(x0, y0, x0 + tile, y0 + tile)
                if (!spells.isAvailable(r, col)) {
                    fill.color = Pal.TILE_EMPTY
                    c.drawRoundRect(rect, rr, rr, fill)
                    val p = caster.castProgress
                    fill.color = withAlpha(WHITE, (30 + 70 * p).toInt())
                    c.drawCircle(rect.centerX(), rect.centerY(), 0.05f * u + 0.1f * u * p, fill)
                    continue
                }
                val element = spells[r, col]
                val pop = tilePop[r][col]
                val scale = if (pop < 1f) easeOutBack(pop) else 1f
                c.save()
                c.scale(scale, scale, rect.centerX(), rect.centerY())
                bitmapPaint.alpha = if (locked) 130 else 255
                c.drawBitmap(art.button(element, tile.toInt().coerceAtLeast(8)), null, rect, bitmapPaint)
                bitmapPaint.alpha = 255
                c.restore()
            }
        }
        // The spell being cast shrinks inside its tile while a progress ring fills around it.
        val element = caster.castElement
        if (element != null && tappedRow >= 0) {
            val x0 = gridLeft + tappedCol * (tile + tileGap)
            val y0 = gridTop + tappedRow * (tile + tileGap)
            val col = art.color(element)
            val p = caster.castProgress
            rect.set(x0 + 0.08f * u, y0 + 0.08f * u, x0 + tile - 0.08f * u, y0 + tile - 0.08f * u)
            val k = 1f - 0.35f * p
            val half = rect.width() * k / 2f
            val cx = rect.centerX()
            val cy = rect.centerY()
            bitmapPaint.alpha = 230
            c.drawBitmap(art.button(element, tile.toInt().coerceAtLeast(8)), null, RectF(cx - half, cy - half, cx + half, cy + half), bitmapPaint)
            bitmapPaint.alpha = 255
            stroke.color = col
            stroke.strokeWidth = 0.08f * u
            c.drawArc(rect, -90f, 360f * p, false, stroke)
        }
    }

    private fun drawBolts(c: Canvas) {
        for (b in bolts) {
            val t = (b.t / BOLT_TIME).coerceIn(0f, 1f)
            val tail = max(0f, t - 0.45f)
            val hx = b.x0 + (b.x1 - b.x0) * t
            val hy = b.y0 + (b.y1 - b.y0) * t
            val tx = b.x0 + (b.x1 - b.x0) * tail
            val ty = b.y0 + (b.y1 - b.y0) * tail
            stroke.color = withAlpha(b.color, 200)
            stroke.strokeWidth = 0.14f * u
            c.drawLine(tx, ty, hx, hy, stroke)
            stroke.color = withAlpha(WHITE, 230)
            stroke.strokeWidth = 0.05f * u
            c.drawLine(tx, ty, hx, hy, stroke)
        }
    }

    private fun drawSparks(c: Canvas) {
        for (s in sparks) {
            if (s.t < s.delay) continue
            val t = ((s.t - s.delay) / s.duration).coerceIn(0f, 1f)
            val e = t * t
            val a = 1f - e
            val x = a * a * s.x0 + 2f * a * e * s.cx + e * e * s.x1
            val y = a * a * s.y0 + 2f * a * e * s.cy + e * e * s.y1
            fill.color = withAlpha(s.color, 90)
            c.drawCircle(x, y, 0.2f * u, fill)
            fill.color = mix(s.color, WHITE, 0.5f)
            c.drawCircle(x, y, 0.08f * u, fill)
        }
    }

    private fun drawTexts(c: Canvas) {
        for (t in texts) {
            val k = t.t / t.duration
            // Stay fully visible, then fade out over the last 40 %.
            val alpha = 1f - smooth01((k - 0.6f) / 0.4f)
            label(c, t.text, t.x, t.y - 0.9f * u * k, t.size * (1f + 0.15f * (1f - k)), t.color, Paint.Align.CENTER, alpha)
        }
    }

    private fun drawBanner(c: Canvas) {
        val t = battle.time
        val intro = battle.introTime
        if (t > intro + 0.5f) return
        val a = if (t < intro) 1f else 1f - (t - intro) / 0.5f
        val cx = fieldLeft + 2.5f * u
        val cy = fieldTop + 2.5f * u
        rect.set(left + 0.4f * u, cy - 1.1f * u, right - 0.4f * u, cy + 1.0f * u)
        fill.color = withAlpha(0xFF0B0820.toInt(), (215 * a).toInt())
        c.drawRoundRect(rect, 0.3f * u, 0.3f * u, fill)
        label(c, battle.opponent.name, cx, cy - 0.15f * u, 0.62f * u, GOLD, Paint.Align.CENTER, a)
        label(c, if (t < intro * 0.6f) "Get ready" else "Duel!", cx, cy + 0.62f * u, 0.42f * u, Pal.TEXT, Paint.Align.CENTER, a)
    }

    private fun drawEndOverlay(c: Canvas) {
        val winner = battle.winner ?: return
        val k = smooth01((endTimer - 0.7f) / 0.5f)
        if (k <= 0f) return
        fill.color = withAlpha(BLACK, (150 * k).toInt())
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val cx = width / 2f
        val cy = fieldTop + 2.5f * u
        rect.set(left + 0.6f * u, cy - 1.6f * u, right - 0.6f * u, cy + 1.6f * u)
        fill.color = withAlpha(Pal.CARD, (240 * k).toInt())
        c.drawRoundRect(rect, 0.35f * u, 0.35f * u, fill)
        stroke.color = withAlpha(GOLD, (200 * k).toInt())
        stroke.strokeWidth = 0.05f * u
        c.drawRoundRect(rect, 0.35f * u, 0.35f * u, stroke)
        val won = winner == Side.PLAYER
        label(c, if (won) "Victory!" else "Defeated", cx, cy - 0.45f * u, 0.8f * u, if (won) GOLD else Pal.ENEMY_HP, Paint.Align.CENTER, k)
        val subtitle = when {
            won && tutorial != null -> "Tutorial complete! Next: ${Opponents.forLevel(level + 1).name}"
            won -> "Next: ${Opponents.forLevel(level + 1).name}"
            else -> "${battle.opponent.name} wins this time"
        }
        label(c, subtitle, cx, cy + 0.35f * u, 0.36f * u, Pal.TEXT, Paint.Align.CENTER, k)
        if (endTimer > TAP_DELAY) {
            label(c, "Tap to continue", cx, cy + 1.05f * u, 0.3f * u, Pal.TEXT_DIM, Paint.Align.CENTER, k * (0.6f + 0.4f * sin(clock * 4f)))
        }
    }

    private companion object {
        const val KEY_LEVEL = "level"
        const val KEY_TUTORIAL_DONE = "tutorialDone"
        const val KEY_PLAYED = "played"
        const val QUEUE_SHIFT_TIME = 0.35f
        const val DIALOG_TAP_GUARD = 0.4f
        const val CANCEL_FADE_IN = 0.15f

        /** Training dummy sprite height relative to the enemy avatar height, and its hit animation speed. */
        const val DUMMY_SPRITE_SCALE = 1.15f
        const val DUMMY_HIT_FPS = 20f

        /** Size of the duel's Menu button and level label (1 = original design). */
        const val MENU_SCALE = 1.2f

        /** Gap between the level label and the Menu button, in field cells. */
        const val MENU_LABEL_GAP = 0.5f
        const val STAR_COUNT = 70
        const val BOLT_TIME = 0.22f
        const val TAP_DELAY = 1.5f
        const val DEFEAT_TILT = 32f

        /** Height of a flying projectile image, in field cells. */
        const val SHOT_HEIGHT = 1.0f

        /** A new shot fades in over this time (seconds) at the start of its lane. */
        const val SHOT_FADE_IN = 0.15f

        /** Playback speed of the Midjourney loops: 24 frames at 10 fps ≈ a 2.4 s cycle. */
        const val ANIM_FPS = 10f

        /**
         * Width of each side flight zone in field cells. Shots fly from 0.5 cells inside the
         * screen edge to 0.5 cells before the field, so the flight path is FLIGHT_ZONE - 1 cells.
         */
        const val FLIGHT_ZONE = 2.375f
    }
}
