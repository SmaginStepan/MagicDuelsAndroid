package com.an0obis.magicduel.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.text.InputFilter
import android.view.WindowManager
import android.widget.EditText
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
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
import com.an0obis.magicduel.game.Caster
import com.an0obis.magicduel.game.Element
import com.an0obis.magicduel.game.Globe
import com.an0obis.magicduel.game.Lesson
import com.an0obis.magicduel.R
import com.an0obis.magicduel.game.Opponent
import com.an0obis.magicduel.game.Opponents
import com.an0obis.magicduel.game.Projectile
import com.an0obis.magicduel.game.Rules
import com.an0obis.magicduel.game.Side
import com.an0obis.magicduel.game.Spell
import com.an0obis.magicduel.game.Tutorial
import com.an0obis.magicduel.game.WallSpell
import com.an0obis.magicduel.game.WallTutorial
import java.util.Locale
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

    /**
     * Magic of [element] flying from a burst globe at ([x0], [y0]) to ([x1], [y1]) along a curve bent
     * through ([cx], [cy]); air and lightning zap there in a straight line.
     */
    private class Spark(
        val x0: Float, val y0: Float, val cx: Float, val cy: Float, val x1: Float, val y1: Float,
        val element: Element, val delay: Float, val duration: Float, val hit: HitGroup?, val seed: Int,
    ) {
        var t = 0f
    }

    /** An effect to start [t] seconds from now. */
    private class Later(var t: Float, val run: () -> Unit)

    /** A Midjourney hit burst playing on [side]'s mage ([t] seconds in, [HIT_TIME] long), mirrored if [flip]. */
    private class HitBurst(val side: Side, val element: Element, val flip: Boolean, val spin: Float) { var t = 0f }

    private class FloatText(val text: String, val x: Float, val y: Float, val color: Int, val size: Float, val duration: Float = 1.1f) {
        var t = 0f
    }

    private class Ring(val x: Float, val y: Float, val color: Int, val r0: Float, val r1: Float, val duration: Float) {
        var t = 0f
    }

    /** A wall that just broke at ([x], [y]): it bursts apart over [WALL_BREAK_TIME]. */
    private class BrokenWall(val x: Float, val y: Float, val look: Int) { var t = 0f }

    private class Bolt(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val color: Int) {
        var t = 0f
    }

    private val prefs = context.getSharedPreferences("magic_duel", Context.MODE_PRIVATE)
    private var level = prefs.getInt(KEY_LEVEL, 0).coerceAtLeast(0)
    private var tutorialDone = prefs.getBoolean(KEY_TUTORIAL_DONE, level > 0)

    /** The walls lesson (level 2) has been seen. */
    private var wallTutorialDone = prefs.getBoolean(KEY_WALL_TUTORIAL_DONE, level > 1)
    private var played = prefs.getBoolean(KEY_PLAYED, level > 0)
    private var battle = Battle(Opponents.forLevel(level))
    private var tutorial: Lesson? = null
    private val art = Art(context.resources, context.packageName)
    private val sounds = Sounds(context)

    // ---- settings (Options) ----
    /** "en", "ru" or "es"; first time: the phone's language if the game has it, else English. */
    private var language = prefs.getString(KEY_LANGUAGE, null)
        ?: Locale.getDefault().language.takeIf { it in LANGUAGES.map { l -> l.first } } ?: "en"
    private var soundOn = prefs.getBoolean(KEY_SOUND, true)
    private var playerName = prefs.getString(KEY_NAME, "") ?: ""

    /** The game's texts in [language], whatever language the phone uses. */
    private var str: Resources = localized(language)

    private fun localized(lang: String): Resources {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale(lang))
        return context.createConfigurationContext(config).resources
    }

    private fun s(id: Int, vararg args: Any): String = str.getString(id, *args)

    /** The opponent's name in the chosen language. */
    private fun opponentName(o: Opponent): String = when {
        o.dummyIndex >= 0 -> str.getStringArray(R.array.dummy_names)[o.dummyIndex]
        o.titleIndex >= 0 -> s(R.string.opponent_fmt, str.getStringArray(R.array.mage_titles)[o.titleIndex], str.getStringArray(R.array.mage_names)[o.nameIndex])
        else -> o.name
    }

    /** Your name as shown in the duel: what you typed in Options, or "You". */
    private fun displayName(): String = playerName.ifBlank { s(R.string.default_name) }

    // ---- screens: the start menu (with small dialogs) and the duel ----
    private enum class Screen { MENU, BATTLE }
    private enum class Dialog { RESTART, LEADERBOARD, PVP, OPTIONS }
    private var screen = Screen.MENU
    private var dialog: Dialog? = null
    private var dialogOpenedAt = 0f
    private val menuButtons = LinkedHashMap<String, RectF>()
    private val dialogButtons = LinkedHashMap<String, RectF>()
    private val dialogCard = RectF()
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

    /** Size of a field cell in pixels: [FIELD_SCALE] layout units. */
    private var fu = 1f
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
    private val later = ArrayList<Later>()
    private val effects = Effects()
    private val sparkPos = PointF()
    private val sparkTail = FloatArray(Effects.TAIL_POINTS * 2)
    private val freezing = ArrayList<Globe>()
    private val bursts = ArrayList<HitBurst>()
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

    /** The glyph ring style of each side's current cast; every new cast takes the next style. */
    private val ringStyle = IntArray(2) { it }

    /** Walls rising: per field cell, 0..1 as a new wall grows in (negative while it waits its turn). */
    private val wallRise = Array(Rules.ROWS) { FloatArray(Rules.COLS) { 1f } }

    /** How usable each wall button looks, 0..1: it fades to grey (disabled) while its column's top cell is taken. */
    private val wallButtonShow = FloatArray(Rules.COLS) { 1f }
    private val brokenWalls = ArrayList<BrokenWall>()

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

    /** Draws bitmaps in greys only: disabled wall buttons. */
    private val greyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    private val sky = Paint()
    private val rect = RectF()
    private val srcRect = Rect()
    private val path = Path()
    private val stars = FloatArray(STAR_COUNT * 3)

    init {
        sounds.enabled = soundOn
        val r = Random(7)
        for (i in stars.indices) stars[i] = r.nextFloat()
        startBattle()
    }

    // ================================================================ lifecycle and loop

    fun resume() {
        if (running) return
        running = true
        sounds.resume()
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun pause() {
        running = false
        sounds.pause()
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
        val contentUnits = 5f * FIELD_SCALE + 2f * FLIGHT_ZONE
        u = min(w / contentUnits, availH / (17.25f + WALL_BAND))
        fu = u * FIELD_SCALE
        effects.u = fu
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

        // The wall buttons sit in a band right above the field.
        val enemyBottom = enemyTop + 2.45f * u
        fieldTop = (enemyBottom + WALL_BAND * u + playerTop) / 2f - 2.5f * fu

        playerH = 3.7f * u
        playerX = left + (gridLeft - 0.7f * u - left) / 2f
        playerFoot = gridTop + gridH - 0.15f * u
        enemyH = 2.15f * u
        enemyX = right - 1.2f * u
        enemyFoot = enemyTop + 2.35f * u

        dashed.pathEffect = DashPathEffect(floatArrayOf(0.12f * u, 0.14f * u), 0f)
        sky.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Pal.SKY_TOP, Pal.SKY_BOTTOM, Shader.TileMode.CLAMP)
        text.setShadowLayer(0.08f * u, 0f, 0.03f * u, BLACK)
        // Decode the animation sheets of the elements in play in the background (see Art.preload);
        // as GPU-only bitmaps when this view draws with hardware acceleration.
        art.hardwareBitmaps = isHardwareAccelerated
        art.preload(battle.elements)
    }

    private fun resetEffects() {
        sparks.clear(); texts.clear(); rings.clear(); bolts.clear(); later.clear(); effects.clear(); bursts.clear()
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
        wallRise.forEach { it.fill(1f) }
        brokenWalls.clear()
        for (col in wallButtonShow.indices) wallButtonShow[col] = if (battle.canPlayerCastWall(col)) 1f else 0f
        queueShift = 0f
    }

    /** A fresh duel at the current level; level 1 runs the tutorial until it has been beaten once. */
    private fun startBattle() {
        battle = Battle(Opponents.forLevel(level))
        art.preload(battle.elements)
        tutorial = when {
            level == 0 && !tutorialDone -> Tutorial()
            level == 1 && !wallTutorialDone -> WallTutorial()
            else -> null
        }
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
            .putBoolean(KEY_WALL_TUTORIAL_DONE, wallTutorialDone)
            .putBoolean(KEY_PLAYED, played)
            .putString(KEY_LANGUAGE, language)
            .putBoolean(KEY_SOUND, soundOn)
            .putString(KEY_NAME, playerName)
            .apply()
    }

    /** "Restart game": back to level 1, tutorial included. */
    private fun restartGame() {
        level = 0
        tutorialDone = false
        wallTutorialDone = false
        played = true
        save()
        startBattle()
        dialog = null
        screen = Screen.BATTLE
    }

    /** Android back: close a dialog, or leave the duel for the menu. Returns false to let the app close. */
    fun onBack(): Boolean = when {
        dialog != null -> { dialog = if (dialog == Dialog.RESTART) Dialog.OPTIONS else null; true }
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
        if (tutorial is WallTutorial && tutorial?.done == true && !wallTutorialDone) {
            wallTutorialDone = true
            save()
        }
        queueShift = max(0f, queueShift - dt / QUEUE_SHIFT_TIME)

        // Copy first: a delayed effect may schedule another one.
        for (l in later.toList()) {
            l.t -= dt
            if (l.t <= 0f) {
                later.remove(l)
                l.run()
            }
        }
        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.t += dt
            if (s.t >= s.delay + s.duration) {
                // The first piece of magic to arrive deals the damage; each one bursts on impact.
                var first = false
                s.hit?.let { group ->
                    if (!group.landed) {
                        group.landed = true
                        first = true
                        onHit(group.target, group.damage)
                        hitBurst(group.target, s.element)
                        sounds.hit(s.element)
                    }
                }
                if (effects.travelsAsBolt(s.element)) effects.zap(s.element, s.x0, s.y0, s.x1, s.y1)
                effects.impact(s.element, s.x1, s.y1, if (first) 1f else 0.45f)
                it.remove()
            } else if (s.t >= s.delay) {
                sparkAt(s, sparkPos)
                effects.trail(s.element, sparkPos.x, sparkPos.y, dt)
            }
        }
        shedFromShots(dt)
        bursts.removeAll { b -> b.t += dt; b.t >= HIT_TIME }
        for (row in wallRise) for (c in row.indices) if (row[c] < 1f) row[c] = min(1f, row[c] + dt / WALL_RISE_TIME)
        for (col in wallButtonShow.indices) {
            // Usable: free below and not the bot's wall column; your own wall cast keeps its button lit.
            val usable = battle.player.castWallColumn == col || battle.canPlayerCastWall(col)
            val target = if (usable) 1f else 0f
            wallButtonShow[col] += (target - wallButtonShow[col]).coerceIn(-dt / WALL_BUTTON_FADE, dt / WALL_BUTTON_FADE)
        }
        brokenWalls.removeAll { b -> b.t += dt; b.t >= WALL_BREAK_TIME }
        effects.update(dt)
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
        // The bot's queue slides on whenever its first move is done with: fired, walls cast, or dropped.
        val botMoveDone = when (e) {
            is BattleEvent.Launched -> e.side == Side.ENEMY
            is BattleEvent.WallCastDone -> e.side == Side.ENEMY
            is BattleEvent.WallCastCancelled -> e.side == Side.ENEMY
            else -> false
        }
        if (botMoveDone) queueShift = 1f
        if (e is BattleEvent.GameOver && e.winner == Side.PLAYER && tutorial is Tutorial) {
            tutorialDone = true
            save()
        }
        when (e) {
            // Tapping a spell: a plain, quiet button click (the bot taps nothing, so it is silent).
            is BattleEvent.CastStarted -> {
                if (e.side == Side.PLAYER) sounds.play(Sounds.Fx.CAST, MENU_CLICK_VOLUME)
                ringStyle[e.side.ordinal] = (ringStyle[e.side.ordinal] + 1) % Art.CAST_RING_STYLES
            }
            is BattleEvent.CastCancelled -> {
                sounds.play(Sounds.Fx.UI_BACK, 0.8f)
                // The dissolved spells pop back in; the staff lowers without a shot.
                e.cells.forEach { tilePop[it.row][it.col] = 0f }
                tappedRow = -1
                tappedCol = -1
                afterglow[Side.PLAYER.ordinal] = 0f
                // Starts below where the button was, so it fades out before reaching your HP bar.
                texts += FloatText(s(R.string.cancelled), cancelCastButton.centerX(), cancelCastButton.bottom + 0.1f * u, Pal.TEXT_DIM, 0.32f * u, 0.8f)
            }
            is BattleEvent.WallCastStarted -> {
                if (e.side == Side.PLAYER) sounds.play(Sounds.Fx.CAST, MENU_CLICK_VOLUME)
                ringStyle[e.side.ordinal] = (ringStyle[e.side.ordinal] + 1) % Art.CAST_RING_STYLES
            }
            is BattleEvent.WallCastCancelled -> {
                afterglow[e.side.ordinal] = 0f
                if (e.side == Side.PLAYER) {
                    sounds.play(Sounds.Fx.UI_BACK, 0.8f)
                    texts += FloatText(s(R.string.cancelled), cancelCastButton.centerX(), cancelCastButton.bottom + 0.1f * u, Pal.TEXT_DIM, 0.32f * u, 0.8f)
                } else {
                    // The bot's wall had nowhere to go: a quiet note under its queue, by the dropped move.
                    texts += FloatText(s(R.string.cancelled), left + 0.72f * u, enemyTop + 2.05f * u, Pal.TEXT_DIM, 0.26f * u, 0.8f)
                }
            }
            is BattleEvent.WallCastDone -> Unit
            // New walls grow in from the bottom of the column up.
            is BattleEvent.WallsRaised -> {
                afterglow[e.side.ordinal] = 1f
                lastCastColor[e.side.ordinal] = WALL_COLOR
                // A shimmer of magic as the glass panes appear.
                if (e.cells.isNotEmpty()) sounds.play(Sounds.Fx.FREEZE, 0.4f)
                val lowest = e.cells.maxOfOrNull { it.row } ?: 0
                e.cells.forEach { wallRise[it.row][it.col] = -(lowest - it.row) * WALL_RISE_STAGGER / WALL_RISE_TIME }
            }
            // A wall is magic glass: it shatters into shards with a flash of its gold light.
            is BattleEvent.WallsBroken -> {
                sounds.play(Sounds.Fx.POP, 0.9f)
                e.cells.forEach {
                    val x = cellX(it.col.toFloat())
                    val y = cellY(it.row.toFloat())
                    brokenWalls += BrokenWall(x, y, battle.board.wallLook(it.row, it.col))
                    rings += Ring(x, y, mix(WALL_COLOR, WHITE, 0.4f), 0.2f * fu, 0.75f * fu, 0.3f)
                }
            }
            is BattleEvent.Launched -> {
                val from = staffTip(e.side)
                bolts += Bolt(from.x, from.y, startX(e.side), laneY(e.lane), art.color(e.element))
                afterglow[e.side.ordinal] = 1f
                lastCastColor[e.side.ordinal] = art.color(e.element)
                sounds.play(Sounds.Fx.LAUNCH, 0.55f)
            }
            // Elements with a freeze clip show the shot freezing (drawFreeze); the others get a frost ring.
            is BattleEvent.Landed -> {
                sounds.play(Sounds.Fx.FREEZE, 0.5f)
                if (art.freeze(e.element) == null) rings += Ring(
                    cellX(e.col.toFloat()), cellY(e.row.toFloat()),
                    mix(art.color(e.element), WHITE, 0.65f), 0.25f * fu, 0.8f * fu, 0.35f,
                )
            }
            is BattleEvent.Backfired -> texts += FloatText(s(R.string.backfire), endX(e.side), laneY(e.lane) - 0.45f * u, Pal.ENEMY_HP, 0.38f * u, 0.9f)
            is BattleEvent.BackfireHit -> {
                onHit(e.side, e.damage)
                hitBurst(e.side, e.element)
                sounds.hit(e.element)
                chest(e.side).let { effects.impact(e.element, it.x, it.y, 0.8f) }
            }
            is BattleEvent.Matched -> {
                spawnMatch(e)
                sounds.play(Sounds.Fx.POP, 0.8f)
            }
            // The grown symbols pop into buttons; the cast spell's own tile is swapped at once.
            is BattleEvent.SpellsRefilled -> e.cells.forEach {
                tilePop[it.row][it.col] = if (it.row == tappedRow && it.col == tappedCol) 1f else 0f
            }
            is BattleEvent.PushedOut -> texts += FloatText(
                s(R.string.pushed_out), cellX(if (e.by == Side.ENEMY) 0f else 4f), laneY(e.lane) - 0.55f * u, Pal.DAMAGE, 0.36f * u, 0.9f,
            )
            is BattleEvent.PushOutHit -> {
                onHit(e.target, e.damage)
                hitBurst(e.target, e.element)
                sounds.hit(e.element)
                chest(e.target).let { effects.impact(e.element, it.x, it.y, 0.8f) }
            }
            is BattleEvent.GameOver -> sounds.play(if (e.winner == Side.PLAYER) Sounds.Fx.WIN else Sounds.Fx.LOSE)
        }
    }

    /**
     * Matched globes burst in their element's way (flames, splash, rocks, lightning, smoke), then
     * their magic flies at the target: fireballs, water orbs, hurled rocks; air zaps as lightning.
     */
    private fun spawnMatch(e: BattleEvent.Matched) {
        val target = chest(e.target)
        val group = HitGroup(e.target, e.damage)
        val spread = 0.3f * u
        e.cells.forEachIndexed { i, cell ->
            val x = cellX(cell.col.toFloat())
            val y = cellY(cell.row.toFloat())
            val el = e.elements[i]
            // The burst comes as the swelling globe fades (see drawGlobes).
            later += Later(Rules.POP_TIME * 0.45f + i * 0.02f) { effects.explode(el, x, y) }
            val bolt = effects.travelsAsBolt(el)
            repeat(if (el == Element.EARTH) 2 else 1) { k ->
                val tx = target.x + (Random.nextFloat() * 2f - 1f) * spread
                val ty = target.y + (Random.nextFloat() * 2f - 1f) * spread
                val delay = Rules.POP_TIME * 0.6f + i * 0.05f + k * 0.07f
                if (bolt) {
                    sparks += Spark(x, y, (x + tx) / 2f, (y + ty) / 2f, tx, ty, el, delay, ZAP_TIME, group, Random.nextInt())
                } else {
                    val dx = tx - x
                    val dy = ty - y
                    val len = max(1f, hypot(dx, dy))
                    val bend = (if ((i + k) % 2 == 0) 1f else -1f) * (1.2f + Random.nextFloat()) * u
                    val mx = (x + tx) / 2f - dy / len * bend
                    val my = (y + ty) / 2f + dx / len * bend
                    sparks += Spark(x, y, mx, my, tx, ty, el, delay, 0.55f + Random.nextFloat() * 0.15f, group, Random.nextInt())
                }
            }
        }
        if (e.chain >= 2) {
            texts += FloatText(s(R.string.chain_fmt, e.chain), fieldLeft + 2.5f * fu, fieldTop + 2.3f * fu, GOLD, 0.6f * u, 1.2f)
        }
    }

    /**
     * Magic of [element] hits [side]: its Midjourney burst (a fiery blast, a splash, shattering rock,
     * a lightning strike) plays over the mage, randomly turned and mirrored so repeated hits differ.
     */
    private fun hitBurst(side: Side, element: Element) {
        if (art.hit(element) == null) return
        bursts += HitBurst(side, element, Random.nextBoolean(), Random.nextFloat() * 50f - 25f)
    }

    /** Size of a hit burst on [side]'s mage, in pixels: a bit wider than the mage is tall for the small enemy. */
    private fun hitSize(side: Side): Int =
        ((if (side == Side.PLAYER) playerH * 0.85f else enemyH * 1.1f)).toInt().coerceAtLeast(8)

    /**
     * Hit bursts over the mages: the clip starts at the blast's peak, so it pops in (growing from
     * 55% over the first tenth), keeps spreading outward (30% more by the end) and fades out over
     * the last third.
     */
    private fun drawHitBursts(c: Canvas) {
        for (b in bursts) {
            val size = hitSize(b.side)
            val anim = art.hit(b.element) ?: continue
            val k = (b.t / HIT_TIME).coerceIn(0f, 1f)
            val at = chest(b.side)
            val grow = (0.55f + 0.45f * easeOut(min(1f, k / 0.1f))) * (1f + 0.3f * k)
            c.save()
            c.translate(at.x, at.y)
            c.rotate(b.spin)
            c.scale(if (b.flip) -grow else grow, grow)
            rect.set(-size / 2f, -size / 2f, size / 2f, size / 2f)
            bitmapPaint.alpha = (255 * (1f - smooth01((k - 0.67f) / 0.33f))).toInt()
            anim.draw(c, min(anim.count - 1, (k * anim.count).toInt()), rect, bitmapPaint)
            c.restore()
        }
        bitmapPaint.alpha = 255
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
        for (col in Rules.WALL_COLUMNS) {
            if (!battle.canPlayerCastWall(col)) continue // its button is disabled
            if (!wallButtonRect(col, rect).apply { inset(-0.08f * u, -0.12f * u) }.contains(x, y)) continue
            if (battle.tryCastWall(col)) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
            val hit = dialogButtons.entries.firstOrNull { it.value.contains(x, y) }?.key
            when {
                hit == "restart" -> restartGame()
                hit == "cancel" -> openDialog(Dialog.OPTIONS) // the restart question comes from Options
                hit == "ok" || hit == "close" -> dialog = null
                hit == "sound" -> {
                    soundOn = !soundOn
                    sounds.enabled = soundOn
                    save()
                }
                hit == "name" -> editName()
                hit == "restart_open" -> openDialog(Dialog.RESTART)
                hit != null && hit.startsWith("lang_") -> setLanguage(hit.removePrefix("lang_"))
                // A tap outside the card closes it ("coming soon" cards close on any tap).
                open == Dialog.RESTART -> if (!dialogCard.contains(x, y)) openDialog(Dialog.OPTIONS)
                open == Dialog.OPTIONS -> if (!dialogCard.contains(x, y)) dialog = null
                else -> dialog = null
            }
            if (hit != null) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                sounds.play(if (hit == "sound" || hit.startsWith("lang_")) Sounds.Fx.UI_TOGGLE else Sounds.Fx.UI_CLICK, 0.8f)
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
            "leaderboard" -> dialog = Dialog.LEADERBOARD
            "pvp" -> dialog = Dialog.PVP
            "options" -> dialog = Dialog.OPTIONS
            else -> return
        }
        if (dialog != null) dialogOpenedAt = clock
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        // Main menu buttons: a quiet click, so the menu stays calm.
        sounds.play(Sounds.Fx.UI_MENU, MENU_CLICK_VOLUME)
    }

    // ================================================================ geometry

    private fun cellX(col: Float) = fieldLeft + (col + 0.5f) * fu
    private fun cellY(row: Float) = fieldTop + (row + 0.5f) * fu
    private fun laneY(lane: Int) = cellY(lane.toFloat())
    private fun startX(side: Side) = if (side == Side.PLAYER) left + 0.5f * u else right - 0.5f * u
    /** Where a shot reaches the field: one cell outside the edge, where it starts sliding in. */
    private fun endX(side: Side) = if (side == Side.PLAYER) fieldLeft - 0.5f * fu else fieldLeft + 5.5f * fu
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
        drawWallButtons(c)
        drawWalls(c)
        drawGlobes(c)
        drawRings(c)
        drawProjectiles(c)
        drawPlayerPanel(c)
        drawPlayerAvatar(c)
        drawCancelCast(c)
        drawSpellGrid(c)
        drawBolts(c)
        drawSparks(c)
        drawHitBursts(c)
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
        val nameWidth = text.measureText(opponentName(battle.opponent))
        val room = w - hpWidth - 0.25f * u
        val nameSize = if (nameWidth > room) 0.38f * u * room / nameWidth else 0.38f * u
        label(c, opponentName(battle.opponent), x, enemyTop + 0.42f * u, nameSize, Pal.TEXT, Paint.Align.LEFT)
        label(c, hpText, x + w, enemyTop + 0.42f * u, 0.3f * u, Pal.TEXT_DIM, Paint.Align.RIGHT)
        drawHpBar(c, x, enemyTop + 0.56f * u, w, 0.26f * u, Side.ENEMY, Pal.ENEMY_HP)

        val chipsTop = enemyTop + 1.08f * u
        if (battle.botQueue.isEmpty()) {
            label(c, s(R.string.dummy_info), x, chipsTop + 0.42f * u, 0.27f * u, Pal.TEXT_DIM, Paint.Align.LEFT)
            return
        }
        val chip = 0.74f * u
        val gap = 0.14f * u
        val rr = 0.14f * u
        // The bot's plan: the first spell is cast next. When it has been cast, the queue slides left
        // and a newly planned spell fades in at the end.
        val slide = 1f - easeOut(1f - queueShift)
        battle.botQueue.forEachIndexed { i, move ->
            val cx = x + (i + slide) * (chip + gap)
            val next = i == 0
            val shot = (move as? Spell)?.shot
            val col = shot?.let { art.color(it.element) } ?: WALL_COLOR
            rect.set(cx, chipsTop, cx + chip, chipsTop + chip)
            val fadeIn = if (i == battle.botQueue.lastIndex) 1f - queueShift else 1f
            // The spell being cast sits in its glyph ring (behind the chips, so the next one covers its edge).
            if (next && battle.enemy.isCasting) {
                art.drawCastRing(
                    c, ringStyle[Side.ENEMY.ordinal], rect.centerX(), rect.centerY(), chip * CAST_RING_SIZE,
                    col, battle.enemy.castProgress, clock,
                )
            }
            bitmapPaint.alpha = ((if (next) 255 else 170) * fadeIn).toInt()
            val size = chip.toInt().coerceAtLeast(8)
            c.drawBitmap(if (shot != null) art.button(shot.element, size) else art.wallButton(size), null, rect, bitmapPaint)
            bitmapPaint.alpha = 255
            if (next) {
                stroke.strokeWidth = 0.06f * u
                stroke.color = col
                c.drawRoundRect(rect, rr, rr, stroke)
            }
            // A spell shows its lane; a wall shows the field column it drops into.
            val tag = if (shot != null) "${shot.lane + 1}" else "↓${(move as WallSpell).column + 1}"
            label(c, tag, cx + chip - 0.1f * u, chipsTop + chip - 0.08f * u, 0.26f * u, WHITE, Paint.Align.RIGHT, fadeIn)
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
            val castColor = castColor(e) ?: if (afterglow[1] > 0f) lastCastColor[1] else null
            val cast = if (e.isCasting) e.castProgress else afterglow[1]
            art.drawMage(c, x, enemyFoot, enemyH, false, battle.opponent.robeColor, true, castColor, cast, clock, flash[1], DEFEAT_TILT * k, 1f - 0.6f * k)
        }
    }

    private fun drawField(c: Canvas) {
        val fr = fieldLeft + 5f * fu
        val fb = fieldTop + 5f * fu
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
            drawCountdown(c, (x0 + x1) / 2f, y + 0.4f * u, col, caster.castTime * (1f - caster.castProgress) + Rules.FLIGHT_TIME)
        }
        rect.set(fieldLeft - 0.14f * u, fieldTop - 0.14f * u, fr + 0.14f * u, fb + 0.14f * u)
        fill.color = Pal.PANEL
        c.drawRoundRect(rect, 0.3f * u, 0.3f * u, fill)
        stroke.color = Pal.PANEL_EDGE
        stroke.strokeWidth = 0.05f * u
        c.drawRoundRect(rect, 0.3f * u, 0.3f * u, stroke)
        fill.color = withAlpha(WHITE, 12)
        for (r in 0 until Rules.ROWS) for (col in 0 until Rules.COLS) {
            c.drawCircle(cellX(col.toFloat()), cellY(r.toFloat()), 0.36f * fu, fill)
        }
        // Telegraph: the column a caster is raising walls in glows.
        for (side in Side.entries) {
            val caster = battle.caster(side)
            val column = caster.castWallColumn
            if (column < 0) continue
            val pulse = 0.5f + 0.5f * sin(clock * 10f)
            val x0 = fieldLeft + column * fu
            rect.set(x0 + 0.04f * fu, fieldTop + 0.02f * fu, x0 + 0.96f * fu, fb - 0.02f * fu)
            fill.color = withAlpha(WALL_COLOR, (14 + 40 * caster.castProgress * pulse).toInt())
            c.drawRoundRect(rect, 0.2f * u, 0.2f * u, fill)
        }
    }

    /** Where the wall button of field column [col] is: centred over the column, just above the field. */
    private fun wallButtonRect(col: Int, out: RectF): RectF {
        val cx = cellX(col.toFloat())
        val half = WALL_BUTTON * u / 2f
        val cy = fieldTop - 0.3f * u - half
        out.set(cx - half, cy - half, cx + half, cy + half)
        return out
    }

    /**
     * The three wall buttons over the middle columns 2–4 ([Rules.WALL_COLUMNS]; none on the first duel).
     * A button is disabled, grey and faded, while the cell right below it is taken by a wall or a globe.
     * Like the spell grid, they dim while you cast; the wall being cast keeps its button in a glyph
     * ring. The bot's wall cast rings its column's button too.
     */
    private fun drawWallButtons(c: Canvas) {
        if (!battle.opponent.walls) return // walls are locked on the first duel
        val p = battle.player
        val locked = p.isCasting || !battle.started || battle.winner != null
        val size = (WALL_BUTTON * u).toInt().coerceAtLeast(8)
        for (col in Rules.WALL_COLUMNS) {
            // 1 = usable; fades to 0 (grey and see-through, disabled) while the cell below is taken or
            // the bot is casting walls into this column (its ring shows around the grey button).
            val show = smooth01(wallButtonShow[col])
            wallButtonRect(col, rect)
            for (side in Side.entries) {
                val caster = battle.caster(side)
                if (caster.castWallColumn != col) continue
                art.drawCastRing(
                    c, ringStyle[side.ordinal], rect.centerX(), rect.centerY(), rect.width() * CAST_RING_SIZE,
                    if (side == Side.PLAYER) WALL_COLOR else Pal.ENEMY_HP, caster.castProgress, clock,
                )
            }
            if (show < 1f) {
                greyPaint.alpha = (WALL_BUTTON_DISABLED_ALPHA * (1f - show)).toInt()
                c.drawBitmap(art.wallButton(size), null, rect, greyPaint)
            }
            if (show <= 0.01f) continue
            bitmapPaint.alpha = (show * when {
                p.castWallColumn == col -> 230
                locked -> 130
                else -> 255
            }).toInt()
            c.drawBitmap(art.wallButton(size), null, rect, bitmapPaint)
        }
        bitmapPaint.alpha = 255
    }

    /**
     * The walls on the field, arcane crystal bricks (a random variant each): a new wall grows in
     * (overshooting a little) with a flash, a standing one shimmers, and a broken one shatters into
     * four flying shards.
     */
    private fun drawWalls(c: Canvas) {
        val board = battle.board
        val size = (WALL_TILE * fu).toInt().coerceAtLeast(8)
        for (r in 0 until board.rows) for (col in 0 until board.cols) {
            if (!board.isWall(r, col)) continue
            val k = wallRise[r][col]
            if (k <= 0f) continue
            val scale = if (k < 1f) easeOutBack(k) else 1f
            val half = size * scale / 2f
            val x = cellX(col.toFloat())
            val y = cellY(battle.wallDrawRow(r, col)) // walls fall with the globes
            rect.set(x - half, y - half, x + half, y + half)
            // Standing walls shimmer gently, each in its own rhythm.
            val shimmer = 0.9f + 0.1f * sin(clock * 2.6f + r * 1.7f + col * 2.3f)
            bitmapPaint.alpha = (255 * min(1f, k * 2f) * shimmer).toInt()
            c.drawBitmap(art.wallBlock(size, board.wallLook(r, col)), null, rect, bitmapPaint)
            if (k < 1f) {
                // A flash of conjuring light as it appears.
                fill.color = withAlpha(WALL_COLOR, (110 * (1f - k)).toInt())
                c.drawRoundRect(rect, 0.06f * size, 0.06f * size, fill)
            }
        }
        // A broken wall shatters: its four quarters fly apart, spinning, and fade.
        for (b in brokenWalls) {
            val block = art.wallBlock(size, b.look)
            val q = block.width / 2
            val k = b.t / WALL_BREAK_TIME
            val fly = easeOut(k) * 0.45f * fu
            bitmapPaint.alpha = (255 * (1f - k) * (1f - k)).toInt()
            for (i in 0 until 4) {
                val sx = if (i % 2 == 0) -1f else 1f
                val sy = if (i < 2) -1f else 1f
                val cx = b.x + sx * (size / 4f + fly)
                val cy = b.y + sy * (size / 4f + fly) + k * k * 0.3f * fu
                srcRect.set((i % 2) * q, (i / 2) * q, (i % 2 + 1) * q, (i / 2 + 1) * q)
                c.save()
                c.rotate(sx * sy * 40f * k, cx, cy)
                rect.set(cx - size / 4f, cy - size / 4f, cx + size / 4f, cy + size / 4f)
                c.drawBitmap(block, srcRect, rect, bitmapPaint)
                c.restore()
            }
        }
        bitmapPaint.alpha = 255
    }

    /** The colour of what [caster] is casting: its element's, or the walls' crystal blue. */
    private fun castColor(caster: Caster): Int? = when {
        caster.castElement != null -> art.color(caster.castElement!!)
        caster.castWallColumn >= 0 -> WALL_COLOR
        else -> null
    }

    /**
     * The seconds [eta] until a shot reaches the field: a small, quiet number at the bottom edge
     * ([bottom]) of the glowing lane strip, in a soft tint of the element's colour. No background,
     * border or animation, so it informs without pulling the eye.
     */
    private fun drawCountdown(c: Canvas, cx: Float, bottom: Float, color: Int, eta: Float) {
        val size = 0.21f * u
        label(
            c, String.format(Locale.US, "%.1f", eta), cx, bottom - 0.06f * u, size,
            mix(color, WHITE, 0.55f), Paint.Align.CENTER, 0.75f,
        )
    }

    private fun drawChevrons(c: Canvas, cx: Float, cy: Float, pointRight: Boolean, color: Int, k: Float, alpha: Float = 1f) {
        val d = if (pointRight) 1f else -1f
        val spacing = 0.3f * u
        val drift = ((clock * 1.5f) % 1f) * spacing
        stroke.color = withAlpha(color, ((120 + 135 * k) * alpha).toInt())
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
        val size = (0.9f * fu).toInt().coerceAtLeast(8)
        val popping = battle.phase == Battle.Phase.POP
        freezing.clear()
        battle.board.forEachGlobe { _, _, g ->
            if (g.inFlight) {
                // Still a projectile sliding through the row; it freezes into a globe when it stops.
                drawShot(c, g.element, cellX(g.drawCol), cellY(g.drawRow), if (g.headingRight) 1f else -1f, 0f, g.look)
                return@forEachGlobe
            }
            var scale = 1f
            var alpha = 1f
            val hasFreeze = g.freeze > 0f && art.freeze(g.element) != null
            if (g.popping && popping) {
                val p = battle.phaseProgress
                scale = 1f + 0.45f * p
                alpha = 1f - p
            } else if (hasFreeze) {
                // The shot is freezing into this globe (drawn on top, below): the globe shows through
                // only at the end of the clip.
                alpha = smooth01((1f - g.freeze - 0.6f) / 0.4f)
                freezing += g
            }
            val x = cellX(g.drawCol)
            val y = cellY(g.drawRow)
            val half = size * scale / 2f
            rect.set(x - half, y - half, x + half, y + half)
            bitmapPaint.alpha = (255 * alpha).toInt()
            if (alpha > 0f) art.drawGlobe(c, g.element, rect, animFrame(g.look), g.look, bitmapPaint)
            if (g.freeze > 0f && !hasFreeze) {
                // No freeze animation for this element: a white flash fades off the new globe.
                fill.color = withAlpha(0xFFE8F6FF.toInt(), (190 * g.freeze).toInt())
                c.drawCircle(x, y, half * 0.9f, fill)
            }
        }
        // Freezing shots go over the other globes: the icy tail reaches back along the row.
        for (g in freezing) drawFreeze(c, g)
        bitmapPaint.alpha = 255
    }

    /** Height of the freeze clip; its globe matches the field's globes (see [Art.freezeFrames]). */
    private fun freezeHeight() = ((0.9f * fu).toInt().coerceAtLeast(8) / Art.FREEZE_GLOBE).toInt()

    /**
     * The Midjourney clip of shot [g] freezing into its crystal globe: frost runs over the comet, its
     * tail shatters into ice dust and the head hardens into the globe. Played once over
     * [Rules.FREEZE_TIME]; it fades out at the end as the real globe takes over. The sheet is built so
     * the comet starts at the flying comet's head size and grows smoothly into the globe in place
     * (tools/art/anim-sheets.ps1); at first the live comet is drawn over it and fades out.
     */
    private fun drawFreeze(c: Canvas, g: Globe) {
        val anim = art.freeze(g.element) ?: return
        val t = 1f - g.freeze
        val h = freezeHeight().toFloat()
        val w = h * anim.aspect
        val x = cellX(g.drawCol)
        val y = cellY(g.drawRow)
        c.save()
        c.translate(x, y)
        // The clip's globe sits half a frame height in from its right edge; enemy shots face left.
        if (!g.headingRight) c.scale(-1f, 1f)
        rect.set(h / 2f - w, -h / 2f, h / 2f, h / 2f)
        bitmapPaint.alpha = (255 * smooth01(t / 0.15f) * (1f - smooth01((t - 0.8f) / 0.2f))).toInt()
        anim.draw(c, min(anim.count - 1, (t * anim.count).toInt()), rect, bitmapPaint)
        c.restore()
        // The live comet keeps moving at first and fades out as the frost takes over (the clip starts
        // at the flying comet's head size, see tools/art/anim-sheets.ps1).
        val live = 1f - smooth01(t / 0.35f)
        if (live > 0f) drawShot(c, g.element, x, y, if (g.headingRight) 1f else -1f, 0f, g.look, live)
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
    private fun drawShot(c: Canvas, e: Element, x: Float, y: Float, dx: Float, dy: Float, look: Int, alpha: Float = 1f) {
        val anim = art.projectile(e, look)
        val h = SHOT_HEIGHT * fu
        val w = h * anim.aspect
        // The head's centre sits SHOT_HEAD_INSET image heights in from the right edge (room for spray ahead).
        val headInset = h * SHOT_HEAD_INSET
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
        anim.draw(c, animFrame(look, COMET_FPS), rect, bitmapPaint)
        bitmapPaint.alpha = 255
        c.restore()
    }

    /**
     * The current frame of a looping Midjourney animation. Each globe or shot starts at its own point
     * in the loop (from its [look]), so neighbouring globes don't flicker in sync.
     */
    private fun animFrame(look: Int, fps: Float = ANIM_FPS): Int = (clock * fps).toInt() + look % (Art.ANIM_COLS * Art.ANIM_ROWS)

    /**
     * Comets in the lanes (and sliding into the field) shed bits of their element just behind the
     * head: embers, drops, pebbles and dust, electric sparks. Drawn with the other effects.
     */
    private fun shedFromShots(dt: Float) {
        val behind = 0.3f * fu
        for (p in battle.projectiles) {
            val dir = if (p.owner.fromLeft) 1f else -1f
            val laneY = laneY(p.lane)
            val x = when (p.state) {
                Projectile.State.FLYING -> startX(p.owner) + (endX(p.owner) - startX(p.owner)) * p.progress
                Projectile.State.WAITING -> endX(p.owner)
                else -> continue
            }
            effects.trail(p.element, x - dir * behind, laneY, dt * SHED_RATE)
            effects.crackle(p.element, x, laneY, dir, dt, CRACKLE_RATE)
            effects.windStreaks(p.element, x, laneY, dir, dt, WIND_RATE)
        }
        battle.board.forEachGlobe { _, _, g ->
            if (!g.inFlight) return@forEachGlobe
            val dir = if (g.headingRight) 1f else -1f
            effects.trail(g.element, cellX(g.drawCol) - dir * behind, cellY(g.drawRow), dt * SHED_RATE)
            effects.crackle(g.element, cellX(g.drawCol), cellY(g.drawRow), dir, dt, CRACKLE_RATE)
            effects.windStreaks(g.element, cellX(g.drawCol), cellY(g.drawRow), dir, dt, WIND_RATE)
        }
    }

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
                    c, p.element, sx + (ex - sx) * p.progress, laneY, dir, 0f, p.look, min(1f, p.flown / SHOT_FADE_IN),
                )
                // The field is still moving: the shot holds at its edge until it can slide in.
                Projectile.State.WAITING -> drawShot(c, p.element, ex, laneY, dir, 0f, p.look)
                Projectile.State.BOUNCING -> {
                    // Backfire: the shot turns around and flies straight at its own caster.
                    val target = chest(p.owner)
                    val t = p.bounceProgress
                    drawShot(
                        c, p.element, ex + (target.x - ex) * t, laneY + (target.y - laneY) * t,
                        target.x - ex, target.y - laneY, p.look,
                    )
                }
                Projectile.State.EJECTED -> {
                    // A crystal globe shoved off the far edge of a full row flies straight at the opponent.
                    val target = chest(p.owner.opponent)
                    val from = cellX(if (p.owner.fromLeft) 4f else 0f)
                    val t = p.bounceProgress
                    val x = from + (target.x - from) * t
                    val y = laneY + (target.y - laneY) * t
                    val half = (0.9f * fu).toInt().coerceAtLeast(8) / 2f
                    rect.set(x - half, y - half, x + half, y + half)
                    art.drawGlobe(c, p.element, rect, animFrame(p.look), p.look, bitmapPaint)
                }
                Projectile.State.DONE -> Unit
            }
        }
    }

    private fun drawPlayerPanel(c: Canvas) {
        val x = left + 0.35f * u
        val w = gridLeft - 0.9f * u - x
        // A long name shrinks so it never runs into the HP number.
        val name = displayName()
        text.textSize = 0.38f * u
        val room = w - 1.0f * u
        val nameSize = 0.38f * u * min(1f, room / max(1f, text.measureText(name)))
        label(c, name, x, playerTop + 0.32f * u, nameSize, Pal.TEXT, Paint.Align.LEFT)
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
        label(c, s(R.string.menu), battleMenuButton.left + 0.6f * u * k, iy + 0.1f * u * k, 0.28f * u * k, Pal.TEXT, Paint.Align.LEFT)
        label(c, s(R.string.level_fmt, level + 1), battleMenuButton.left - MENU_LABEL_GAP * u, iy + 0.13f * u * k, 0.26f * u * k, Pal.TEXT_DIM, Paint.Align.RIGHT)
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
        label(c, s(R.string.cancel_cast), cancelCastButton.left + 0.8f * u, cy + 0.12f * u, 0.34f * u, Pal.TEXT, Paint.Align.LEFT, a)
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
        val hint = t.hint?.let { str.getString(str.getIdentifier(it, "string", context.packageName)) } ?: return
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
        if (t.pointsAtWalls) {
            val first = wallButtonRect(Rules.WALL_COLUMNS.first, RectF())
            val last = wallButtonRect(Rules.WALL_COLUMNS.last, RectF())
            rect.set(first.left - 0.12f * u, first.top - 0.12f * u, last.right + 0.12f * u, last.bottom + 0.12f * u)
            stroke.color = withAlpha(GOLD, (90 + 140 * pulse).toInt())
            stroke.strokeWidth = 0.06f * u
            c.drawRoundRect(rect, 0.25f * u, 0.25f * u, stroke)
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
        label(c, s(R.string.app_name), cx, titleY, 1.05f * u, GOLD, Paint.Align.CENTER)
        label(c, s(R.string.tagline), cx, titleY + 0.7f * u, 0.34f * u, Pal.TEXT_DIM, Paint.Align.CENTER)

        val elements = Element.active(Rules.START_ELEMENTS)
        val gs = (1.2f * u).toInt().coerceAtLeast(8)
        val gap = 0.3f * u
        val rowW = elements.size * gs + (elements.size - 1) * gap
        var gx = cx - rowW / 2f
        val gy = titleY + 1.3f * u
        for (e in elements) {
            val bob = sin(clock * 2f + e.ordinal) * 0.07f * u
            rect.set(gx, gy + bob, gx + gs, gy + gs + bob)
            art.drawGlobe(c, e, rect, animFrame(e.ordinal * 5), 0, bitmapPaint)
            gx += gs + gap
        }

        menuButtons.clear()
        val bw = min(width - 1.8f * u, 6.6f * u)
        val bx = cx - bw / 2f
        var by = gy + gs + 1.0f * u
        val opponent = Opponents.forLevel(level)
        val sub = s(R.string.level_fmt, level + 1) + " · " + opponentName(opponent) +
            if (level == 0 && !tutorialDone) " · " + s(R.string.tutorial_tag) else ""
        val cont = RectF(bx, by, bx + bw, by + 1.25f * u)
        drawButton(c, cont, s(if (played) R.string.continue_game else R.string.start_game), sub, primary = true, small = false)
        menuButtons["continue"] = cont
        by = cont.bottom + 0.55f * u
        val sgap = 0.25f * u
        val sw = (bw - 2 * sgap) / 3f
        listOf(
            Triple("leaderboard", R.string.leaderboard, true),
            Triple("pvp", R.string.pvp_duel, true),
            Triple("options", R.string.options, false),
        ).forEachIndexed { i, (id, title, soon) ->
            val r = RectF(bx + i * (sw + sgap), by, bx + i * (sw + sgap) + sw, by + 0.9f * u)
            drawButton(c, r, s(title), if (soon) s(R.string.soon) else null, primary = false, small = true)
            menuButtons[id] = r
        }
        dialog?.let { drawDialog(c, it) }
    }

    private fun openDialog(d: Dialog) {
        dialog = d
        dialogOpenedAt = clock
    }

    private fun setLanguage(code: String) {
        language = code
        str = localized(code)
        save()
    }

    /** Your name, typed on the phone's keyboard in a small system dialog. */
    private fun editName() {
        val input = EditText(context).apply {
            setText(playerName)
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(NAME_MAX))
            hint = s(R.string.name_hint)
            setSelection(text.length)
        }
        val box = android.widget.FrameLayout(context).apply {
            val m = (0.25f * u).toInt()
            setPadding(m, m / 2, m, 0)
            addView(input)
        }
        val d = AlertDialog.Builder(context)
            .setTitle(s(R.string.your_name))
            .setView(box)
            .setPositiveButton(s(R.string.save)) { _, _ ->
                playerName = input.text.toString().trim().take(NAME_MAX)
                save()
            }
            .setNegativeButton(s(R.string.cancel), null)
            .create()
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        d.show()
        input.requestFocus()
    }

    /**
     * Options: language, sound effects on/off, your name, and "Restart game" (with its own
     * confirmation). Changes apply and are saved at once.
     */
    private fun drawOptions(c: Canvas) {
        val w = min(width - 1.6f * u, 7f * u)
        val pad = 0.35f * u
        val rowH = 0.8f * u
        val h = 1.25f * u + 0.45f * u + rowH + 0.35f * u + 2 * (rowH + 0.3f * u) + 0.25f * u + rowH + 0.35f * u + rowH + pad
        val cx = width / 2f
        val cy = height / 2f
        dialogCard.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        val card = dialogCard
        fill.color = Pal.CARD
        c.drawRoundRect(card, 0.35f * u, 0.35f * u, fill)
        stroke.color = GOLD
        stroke.strokeWidth = 0.05f * u
        c.drawRoundRect(card, 0.35f * u, 0.35f * u, stroke)
        label(c, s(R.string.options), cx, card.top + 0.85f * u, 0.5f * u, GOLD, Paint.Align.CENTER)
        val x0 = card.left + pad
        val x1 = card.right - pad
        var y = card.top + 1.25f * u

        // Language: one button per language, the chosen one highlighted.
        label(c, s(R.string.language), x0, y + 0.3f * u, 0.28f * u, Pal.TEXT_DIM, Paint.Align.LEFT)
        y += 0.45f * u
        val gap = 0.2f * u
        val lw = (x1 - x0 - 2 * gap) / 3f
        LANGUAGES.forEachIndexed { i, (code, name) ->
            val r = RectF(x0 + i * (lw + gap), y, x0 + i * (lw + gap) + lw, y + rowH)
            drawButton(c, r, name, null, primary = code == language, small = true)
            dialogButtons["lang_$code"] = r
        }
        y += rowH + 0.35f * u

        // Sound effects on/off and your name: a label on the left, a button on the right.
        val bw = 2.6f * u
        label(c, s(R.string.sound_effects), x0, y + rowH / 2f + 0.1f * u, 0.3f * u, Pal.TEXT, Paint.Align.LEFT)
        val sound = RectF(x1 - bw, y, x1, y + rowH)
        drawButton(c, sound, s(if (soundOn) R.string.on else R.string.off), null, primary = soundOn, small = true)
        dialogButtons["sound"] = sound
        y += rowH + 0.3f * u
        label(c, s(R.string.your_name), x0, y + rowH / 2f + 0.1f * u, 0.3f * u, Pal.TEXT, Paint.Align.LEFT)
        val name = RectF(x1 - bw, y, x1, y + rowH)
        drawButton(c, name, displayName(), null, primary = false, small = true)
        dialogButtons["name"] = name
        y += rowH + 0.3f * u + 0.25f * u

        val restart = RectF(x0, y, x1, y + rowH)
        drawButton(c, restart, s(R.string.restart_game), null, primary = false, small = true, danger = true)
        dialogButtons["restart_open"] = restart
        y += rowH + 0.35f * u
        val close = RectF(cx - 1.4f * u, y, cx + 1.4f * u, y + rowH)
        drawButton(c, close, s(R.string.close), null, primary = false, small = true)
        dialogButtons["close"] = close
    }

    private fun drawDialog(c: Canvas, d: Dialog) {
        dialogButtons.clear()
        fill.color = withAlpha(BLACK, 170)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        if (d == Dialog.OPTIONS) {
            drawOptions(c)
            return
        }
        val (title, message) = when (d) {
            Dialog.RESTART -> s(R.string.restart_q) to s(R.string.restart_msg)
            Dialog.LEADERBOARD -> s(R.string.leaderboard) to s(R.string.leaderboard_msg)
            else -> s(R.string.pvp_title) to s(R.string.pvp_msg)
        }
        val w = min(width - 1.6f * u, 7f * u)
        val lines = wrap(message, 0.32f * u, w - 0.8f * u)
        val h = 1.2f * u + lines.size * 0.46f * u + 1.6f * u
        val cx = width / 2f
        val cy = height / 2f
        dialogCard.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        val card = dialogCard
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
            drawButton(c, yes, s(R.string.restart), null, primary = false, small = true, danger = true)
            drawButton(c, no, s(R.string.cancel), null, primary = false, small = true)
            dialogButtons["restart"] = yes
            dialogButtons["cancel"] = no
        } else {
            val ok = RectF(cx - 1.4f * u, bTop, cx + 1.4f * u, bTop + 0.8f * u)
            drawButton(c, ok, s(R.string.ok), null, primary = false, small = true)
            dialogButtons["ok"] = ok
        }
    }

    private fun drawPlayerAvatar(c: Canvas) {
        val lost = battle.winner == Side.ENEMY
        val k = if (lost) smooth01(endTimer / 0.8f) else 0f
        val p = battle.player
        val castColor = castColor(p) ?: if (afterglow[0] > 0f) lastCastColor[0] else null
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
                    // The new spell is already chosen: its element symbol grows slowly during the cast
                    // and becomes a button when the cast completes (SpellsRefilled pops it in).
                    val next = spells.upcoming(r, col) ?: continue
                    if (r == tappedRow && col == tappedCol && caster.isCasting) continue // the cast button, drawn below
                    drawUpcoming(c, next, rect.centerX(), rect.centerY(), caster.castProgress)
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
        // The spell being cast stays as it is in its tile while a progress ring fills around it; its
        // replacement shows only when the cast completes (no growing symbol here).
        val element = caster.castElement
        if (element != null && tappedRow >= 0) {
            val x0 = gridLeft + tappedCol * (tile + tileGap)
            val y0 = gridTop + tappedRow * (tile + tileGap)
            rect.set(x0 + 0.08f * u, y0 + 0.08f * u, x0 + tile - 0.08f * u, y0 + tile - 0.08f * u)
            art.drawCastRing(
                c, ringStyle[Side.PLAYER.ordinal], rect.centerX(), rect.centerY(), tile * CAST_RING_SIZE,
                art.color(element), caster.castProgress, clock,
            )
            bitmapPaint.alpha = 230
            c.drawBitmap(art.button(element, tile.toInt().coerceAtLeast(8)), null, rect, bitmapPaint)
            bitmapPaint.alpha = 255
        }
    }

    /**
     * A spell that is coming: just its glowing element symbol, no button, growing from a small seed
     * to most of the tile as the cast progresses ([p] 0..1).
     */
    private fun drawUpcoming(c: Canvas, e: Element, cx: Float, cy: Float, p: Float) {
        val size = tile * (0.24f + 0.62f * easeOut(p))
        bitmapPaint.alpha = (150 + 105 * p).toInt()
        art.drawSymbol(c, e, cx, cy, size, bitmapPaint)
        bitmapPaint.alpha = 255
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

    /**
     * Where spark [s] is now (or [ago] seconds ago): speeding up along its curve, or (lightning)
     * zapping straight at a steady pace.
     */
    private fun sparkAt(s: Spark, out: PointF, ago: Float = 0f) {
        val t = ((s.t - ago - s.delay) / s.duration).coerceIn(0f, 1f)
        val e = if (effects.travelsAsBolt(s.element)) t else t * t
        val a = 1f - e
        out.set(
            a * a * s.x0 + 2f * a * e * s.cx + e * e * s.x1,
            a * a * s.y0 + 2f * a * e * s.cy + e * e * s.y1,
        )
    }

    /** Element particles (bursts, trails, impacts), then the magic in flight on top. */
    private fun drawSparks(c: Canvas) {
        effects.draw(c)
        for (s in sparks) {
            if (s.t < s.delay) continue
            for (i in 0 until Effects.TAIL_POINTS) {
                sparkAt(s, sparkPos, i * Effects.TAIL_STEP)
                sparkTail[i * 2] = sparkPos.x
                sparkTail[i * 2 + 1] = sparkPos.y
            }
            effects.drawTravel(c, s.element, s.x0, s.y0, sparkTail, s.t - s.delay, s.seed)
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
        val cx = fieldLeft + 2.5f * fu
        val cy = fieldTop + 2.5f * fu
        rect.set(left + 0.4f * u, cy - 1.1f * u, right - 0.4f * u, cy + 1.0f * u)
        fill.color = withAlpha(0xFF0B0820.toInt(), (215 * a).toInt())
        c.drawRoundRect(rect, 0.3f * u, 0.3f * u, fill)
        label(c, opponentName(battle.opponent), cx, cy - 0.15f * u, 0.62f * u, GOLD, Paint.Align.CENTER, a)
        label(c, if (t < intro * 0.6f) s(R.string.get_ready) else s(R.string.duel), cx, cy + 0.62f * u, 0.42f * u, Pal.TEXT, Paint.Align.CENTER, a)
    }

    private fun drawEndOverlay(c: Canvas) {
        val winner = battle.winner ?: return
        val k = smooth01((endTimer - 0.7f) / 0.5f)
        if (k <= 0f) return
        fill.color = withAlpha(BLACK, (150 * k).toInt())
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val cx = width / 2f
        val cy = fieldTop + 2.5f * fu
        rect.set(left + 0.6f * u, cy - 1.6f * u, right - 0.6f * u, cy + 1.6f * u)
        fill.color = withAlpha(Pal.CARD, (240 * k).toInt())
        c.drawRoundRect(rect, 0.35f * u, 0.35f * u, fill)
        stroke.color = withAlpha(GOLD, (200 * k).toInt())
        stroke.strokeWidth = 0.05f * u
        c.drawRoundRect(rect, 0.35f * u, 0.35f * u, stroke)
        val won = winner == Side.PLAYER
        label(c, if (won) s(R.string.victory) else s(R.string.defeated), cx, cy - 0.45f * u, 0.8f * u, if (won) GOLD else Pal.ENEMY_HP, Paint.Align.CENTER, k)
        val subtitle = when {
            won && tutorial is Tutorial -> s(R.string.tutorial_complete_next, opponentName(Opponents.forLevel(level + 1)))
            won -> s(R.string.next_fmt, opponentName(Opponents.forLevel(level + 1)))
            else -> s(R.string.wins_fmt, opponentName(battle.opponent))
        }
        label(c, subtitle, cx, cy + 0.35f * u, 0.36f * u, Pal.TEXT, Paint.Align.CENTER, k)
        if (endTimer > TAP_DELAY) {
            label(c, s(R.string.tap_continue), cx, cy + 1.05f * u, 0.3f * u, Pal.TEXT_DIM, Paint.Align.CENTER, k * (0.6f + 0.4f * sin(clock * 4f)))
        }
    }

    private companion object {
        const val KEY_LEVEL = "level"
        const val KEY_TUTORIAL_DONE = "tutorialDone"
        const val KEY_WALL_TUTORIAL_DONE = "wallTutorialDone"
        const val KEY_PLAYED = "played"
        const val KEY_LANGUAGE = "language"
        const val KEY_SOUND = "sound"
        const val KEY_NAME = "playerName"

        /** Languages in Options: code and name in that language. */
        val LANGUAGES = listOf("en" to "English", "ru" to "Русский", "es" to "Español")

        /** Longest player name. */
        const val NAME_MAX = 14

        /** Volume of the main menu buttons' click (Options buttons use 0.8). */
        const val MENU_CLICK_VOLUME = 0.3f

        /** The glyph ring around a spell being cast, as a multiple of its button's size. */
        const val CAST_RING_SIZE = 1.45f

        /** Height of the band above the field that holds the wall buttons, in layout units. */
        const val WALL_BAND = 1.1f

        /** Size of a wall button, in layout units. */
        const val WALL_BUTTON = 0.78f

        /** A wall on the field, as a share of the field cell (globes are 0.9). */
        const val WALL_TILE = 0.94f

        /** A new wall grows in over this long; each wall above the lowest one starts a little later. */
        const val WALL_RISE_TIME = 0.32f
        const val WALL_RISE_STAGGER = 0.07f
        const val WALL_BREAK_TIME = 0.35f

        /** A wall button fades to disabled (or back) over this long when its column's top cell fills (or frees up). */
        const val WALL_BUTTON_DISABLED_ALPHA = 110
        const val WALL_BUTTON_FADE = 0.25f

        /**
         * Walls' colour: the blue-violet light of the crystal walls, shared by the wall button, its cast
         * ring, the column glow, a wall's flash as it rises and the ring when it shatters.
         */
        val WALL_COLOR = 0xFF7F8CFF.toInt()
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

        /** How long air (and lightning) magic takes to zap from a burst globe to its target. */
        const val ZAP_TIME = 0.14f
        const val TAP_DELAY = 1.5f
        const val DEFEAT_TILT = 32f

        /** Height of a flying projectile image, in field cells. */
        const val SHOT_HEIGHT = 1.35f

        /** Where the comet's head centre is in its image, in image heights in from the right (tools/art/anim-sheets.ps1 HeadInset). */
        const val SHOT_HEAD_INSET = 0.75f

        /** Comets shed element particles at this share of the match-magic trail rate (see shedFromShots). */
        const val SHED_RATE = 0.6f

        /** Lightning arcs a second flickering around each air (and lightning) comet. */
        const val CRACKLE_RATE = 9f

        /** Wind streaks a second peeling off each air comet. */
        const val WIND_RATE = 18f

        /** Comet loops play faster than the globes' loops, so their tails stream and crackle. */
        const val COMET_FPS = 16f

        /** How long a Midjourney hit burst plays on a mage (16 frames). */
        const val HIT_TIME = 0.75f

        /** A new shot fades in over this time (seconds) at the start of its lane. */
        const val SHOT_FADE_IN = 0.05f

        /** Playback speed of the Midjourney loops: 24 frames at 10 fps ≈ a 2.4 s cycle. */
        const val ANIM_FPS = 10f

        /**
         * Size of a field cell in layout units [u]: the field is 25% larger than the 5 units it used
         * to be, while the mages, panels and spell grid keep their size. Globes and comets scale with it.
         */
        const val FIELD_SCALE = Rules.FIELD_CELL_UNITS

        /**
         * Width of each side flight zone in layout units. Shots fly from 0.5 units inside the screen
         * edge to half a field cell before the field: Rules.FLIGHT_CELLS field cells. The whole width,
         * 5 field cells plus both zones, stays 9.75 units as before the field grew (1.75 + 6.25 + 1.75).
         */
        const val FLIGHT_ZONE = 0.5f + FIELD_SCALE * (Rules.FLIGHT_CELLS + 0.5f)
    }
}
