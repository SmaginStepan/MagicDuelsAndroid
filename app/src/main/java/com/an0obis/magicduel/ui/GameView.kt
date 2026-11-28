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
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
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
import com.an0obis.magicduel.game.Economy
import com.an0obis.magicduel.game.Element
import com.an0obis.magicduel.game.Globe
import com.an0obis.magicduel.game.Lesson
import com.an0obis.magicduel.BuildConfig
import com.an0obis.magicduel.R
import com.an0obis.magicduel.net.ScoreClient
import com.an0obis.magicduel.game.Opponent
import com.an0obis.magicduel.game.Opponents
import com.an0obis.magicduel.game.Projectile
import com.an0obis.magicduel.game.Rules
import com.an0obis.magicduel.game.Side
import com.an0obis.magicduel.game.Spell
import com.an0obis.magicduel.game.Tutorial
import com.an0obis.magicduel.game.WallSpell
import com.an0obis.magicduel.game.WallTutorial
import java.time.LocalDate
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
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
    private class BrokenWall(val x: Float, val y: Float, val look: Int, val mine: Boolean) { var t = 0f }

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

    /** The online leaderboard: your best level and name (server settings in local.properties). */
    private val leaderboard = Leaderboard(
        ScoreClient(BuildConfig.SCORE_URL, BuildConfig.SCORE_GAME, BuildConfig.SCORE_API_KEY, BuildConfig.SCORE_SECRET),
        prefs,
    ) { post(it) }

    // ---- settings (Options) ----
    /** "en", "ru" or "es"; first time: the phone's language if the game has it, else English. */
    private var language = prefs.getString(KEY_LANGUAGE, null)
        ?: Locale.getDefault().language.takeIf { it in LANGUAGES.map { l -> l.first } } ?: "en"
    private var soundOn = prefs.getBoolean(KEY_SOUND, true)
    private var playerName = prefs.getString(KEY_NAME, "") ?: ""

    /** The paid avatars you have unlocked (by id); the free one is always yours. */
    private val ownedAvatars: MutableSet<String> = HashSet(prefs.getStringSet(KEY_OWNED_AVATARS, null) ?: emptySet())

    private fun owns(a: Avatar): Boolean = !a.paid || a.id in ownedAvatars

    /**
     * Your avatar (Options or a tap on your mage opens "Choose your avatar"). A paid one you haven't
     * unlocked (chosen before avatars had prices) goes back to the free one.
     */
    private var avatar = Avatars.byId(prefs.getString(KEY_AVATAR, null)).let { if (owns(it)) it else Avatars.all.first() }

    /** The element packs you have unlocked (by id); the classic look is always yours. */
    private val ownedPacks: MutableSet<String> = HashSet(prefs.getStringSet(KEY_OWNED_PACKS, null) ?: emptySet())

    private fun ownsPack(p: ElementPack): Boolean = !p.paid || p.id in ownedPacks

    /** The element pack in use: the look of the globes, shots and walls (chosen in the shop). */
    private var pack = ElementPacks.byId(prefs.getString(KEY_PACK, null)).let { if (ownsPack(it)) it else ElementPacks.CLASSIC }

    /** Essence (a revive costs it) and the daily gift's streak; saved with the rest. */
    private val economy = Economy(
        prefs.getInt(KEY_ESSENCE, Economy.START_ESSENCE),
        prefs.getLong(KEY_LAST_CLAIM_DAY, Economy.NEVER),
        prefs.getInt(KEY_STREAK_DAY, 0),
    )

    /** Today's gift, waiting in its popup until you collect it (null when there is none), and its day. */
    private var dailyGift: Economy.Gift? = null
    private var dailyGiftDay = 0L

    /** The essence counter as shown: after a gift or a pack it counts up to the real amount. */
    private var shownEssence = economy.essence.toFloat()

    /** 1 right after essence was added, easing to 0: the counter's crystal flares up. */
    private var essencePulse = 0f

    /** Which of the avatar's three cast animations the current cast plays (picked at random per cast). */
    private var castAnim = 0

    // The pose your avatar was in when its last cast ended, fading into the idle pose (see drawPlayerAvatar).
    private var lastCastAnim: Art.Anim? = null
    private var lastCastFrame = 0
    private var castEndedAt = -1f

    // The same for the bot's sprite (see drawEnemyAvatar).
    private var enemyLastCastAnim: Art.Anim? = null
    private var enemyLastCastFrame = 0
    private var enemyCastEndedAt = -1f

    /** The current opponent's Midjourney avatar: null for the dummies and for mages without one yet. */
    private val enemyAvatar: EnemyAvatar?
        get() = if (battle.opponent.kind == AvatarKind.DUMMY) null else EnemyAvatars.forMage(level - Opponents.dummyCount)

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
    private enum class Dialog { RESTART, LEADERBOARD, PVP, OPTIONS, AVATAR, DAILY, REVIVE, SHOP, UNLOCK }
    private var screen = Screen.MENU
    private var dialog: Dialog? = null

    /** The "Choose your avatar" popup was opened from Options (closing it goes back there). */
    private var avatarFromOptions = false

    /** The avatar in the unlock popup, and the popup it was opened from (closing goes back there). */
    private var unlocking: Avatar? = null
    private var unlockingPack: ElementPack? = null
    private var unlockFrom: Dialog? = null

    /** When the shop last said purchases are coming soon (a tap on a pack in a release build). */
    private var shopNoteAt = -10f

    /** Time since you went down (your collapse plays over it). */
    private var downTimer = 0f

    /** How far the revive in its popup has played, in seconds, or -1 while the popup offers it. */
    private var reviveT = -1f
    private var dialogOpenedAt = 0f
    private val menuButtons = LinkedHashMap<String, RectF>()
    private val dialogButtons = LinkedHashMap<String, RectF>()
    private val dialogCard = RectF()
    private val battleMenuButton = RectF()

    /** "Cancel cast" button above your mage; only shown (and tappable) while you are casting. */
    private val cancelCastButton = RectF()

    /** The × on the tutorial hint (empty while no hint is shown), and the hints closed with it this duel. */
    private val hintCloseButton = RectF()
    private val closedHints = HashSet<String>()

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

    /** Draws your avatar (alpha fades it, a colour filter flashes it). */
    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** Where a tap opens "Choose your avatar" in a duel: the middle of your avatar's frame. */
    private val playerAvatarRect = RectF()

    /** Draws bitmaps in greys only: disabled wall buttons. */
    private val greyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    private val sky = Paint()
    private val rect = RectF()
    private val path = Path()
    private val stars = FloatArray(STAR_COUNT * 3)

    init {
        sounds.enabled = soundOn
        art.pack = pack
        val r = Random(7)
        for (i in stars.indices) stars[i] = r.nextFloat()
        startBattle()
        checkDailyGift()
        // The leaderboard gets your level (players from before it existed join with theirs).
        leaderboard.reached(level + 1, displayName())
        // The first time the app opens, it asks your name (once, and only if you haven't set one;
        // you can change it in Options).
        if (!prefs.getBoolean(KEY_NAME_ASKED, false) && playerName.isBlank()) {
            prefs.edit().putBoolean(KEY_NAME_ASKED, true).apply()
            post { editName(first = true) }
        }
    }

    // ================================================================ lifecycle and loop

    fun resume() {
        if (running) return
        running = true
        sounds.resume()
        checkDailyGift() // the first time the app comes up on a new day
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
        art.prepareAvatarCast(avatar, castAnim)
        prepareSprites()
    }

    private fun resetEffects() {
        sparks.clear(); texts.clear(); rings.clear(); bolts.clear(); later.clear(); effects.clear(); bursts.clear()
        endTimer = 0f
        downTimer = 0f
        reviveT = -1f
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
        closedHints.clear()
        for (col in wallButtonShow.indices) wallButtonShow[col] = if (battle.canPlayerCastWall(col)) 1f else 0f
        queueShift = 0f
    }

    /**
     * Frees the sprite sheets of earlier duels and starts decoding the bot's cast. Your collapse is
     * decoded only once you're losing (see update), since most duels don't need it.
     */
    private fun prepareSprites() {
        val botCast = enemyAvatar?.castRes
        art.retainSheets(setOfNotNull(botCast))
        botCast?.let { art.prepareSheet(it) }
    }

    /** A fresh duel at the current level; level 1 runs the tutorial until it has been beaten once. */
    private fun startBattle() {
        battle = Battle(Opponents.forLevel(level))
        art.preload(battle.elements)
        if (battle.opponent.walls) art.prepareWallBreak()
        art.prepareAvatarCast(avatar, castAnim)
        prepareSprites()
        tutorial = when {
            level == 0 && !tutorialDone -> Tutorial()
            level == 1 && !wallTutorialDone -> WallTutorial()
            else -> null
        }
        resetEffects()
    }

    private fun nextBattle() {
        if (battle.winner == Side.PLAYER) {
            level++
            leaderboard.reached(level + 1, displayName()) // a new level for the leaderboard
        }
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
            .putString(KEY_AVATAR, avatar.id)
            .putInt(KEY_ESSENCE, economy.essence)
            .putLong(KEY_LAST_CLAIM_DAY, economy.lastClaimDay)
            .putInt(KEY_STREAK_DAY, economy.streakDay)
            .putStringSet(KEY_OWNED_AVATARS, HashSet(ownedAvatars))
            .putStringSet(KEY_OWNED_PACKS, HashSet(ownedPacks))
            .putString(KEY_PACK, pack.id)
            .apply()
    }

    /** Puts on avatar [a] (one you own) and saves the choice. */
    private fun chooseAvatar(a: Avatar) {
        avatar = a
        art.prepareAvatarCast(avatar, castAnim)
        prepareSprites()
        save()
    }

    /** Uses element pack [p] (one you own) from now on: its globes, shots and walls, also in this duel. */
    private fun choosePack(p: ElementPack) {
        pack = p
        art.pack = p
        art.preload(battle.elements)
        save()
    }

    /** A tap on an avatar you don't have yet: the unlock popup (closing it goes back to [from]). */
    private fun offerUnlock(a: Avatar, from: Dialog) {
        unlocking = a
        unlockingPack = null
        unlockFrom = from
        openDialog(Dialog.UNLOCK)
    }

    /** A tap on an element pack you don't have yet: the unlock popup (closing it goes back to [from]). */
    private fun offerUnlock(p: ElementPack, from: Dialog) {
        unlockingPack = p
        unlocking = null
        unlockFrom = from
        openDialog(Dialog.UNLOCK)
    }

    /**
     * Unlocks the avatar or element pack in the popup for its price, puts it on and goes back to where
     * the popup came from.
     */
    private fun unlockOffered() {
        val a = unlocking
        val p = unlockingPack
        if (!economy.spend(a?.price ?: p?.price ?: return)) return
        if (a != null) {
            ownedAvatars += a.id
            chooseAvatar(a)
        } else if (p != null) {
            ownedPacks += p.id
            choosePack(p)
        }
        essencePulse = 1f
        sounds.play(Sounds.Fx.FREEZE, 0.8f)
        closeDialog()
    }

    // ================================================================ essence: daily gift, revives, shop

    /** Today's gift, if it hasn't been collected: its popup opens as soon as no other popup is open. */
    private fun checkDailyGift() {
        if (dailyGift != null) return
        val today = LocalDate.now().toEpochDay()
        dailyGift = economy.giftFor(today)
        dailyGiftDay = today
    }

    /** Collects today's gift: the essence is added and the menu's counter counts up to it. */
    private fun collectGift() {
        if (dailyGift != null) {
            economy.claim(dailyGiftDay)
            dailyGift = null
            save()
            essencePulse = 1f
            sounds.play(Sounds.Fx.FREEZE, 0.8f)
        }
        dialog = null
    }

    /** Revive (free): it plays in the popup, then the duel starts over (see stepRevive). */
    private fun startRevive() {
        if (reviveT >= 0f || !battle.playerDown) return
        reviveT = 0f
        sounds.play(Sounds.Fx.CAST, 0.8f)
    }

    /**
     * After the revive has played: the duel starts over at the same level (a fresh board, both at
     * full HP, the bot's plan and the intro again), with a flash of your crystal's light.
     */
    private fun restartAfterRevive() {
        reviveT = -1f
        dialog = null
        startBattle()
        val at = chest(Side.PLAYER)
        rings += Ring(at.x, at.y, avatar.glow, 0.3f * u, 2.4f * u, 0.6f)
        // Over your avatar, but kept on screen (it starts 15% larger, see drawTexts).
        val note = s(R.string.revived)
        text.textSize = 0.42f * u
        val half = text.measureText(note) * 1.15f / 2f
        val nx = at.x.coerceIn(0.2f * u + half, width - 0.2f * u - half)
        texts += FloatText(note, nx, at.y - playerH * 0.35f, mix(avatar.glow, WHITE, 0.3f), 0.42f * u, 1.3f)
        afterglow[Side.PLAYER.ordinal] = 1f
        lastCastColor[Side.PLAYER.ordinal] = avatar.glow
        sounds.play(Sounds.Fx.FREEZE, 0.9f)
    }

    /** A pack in the shop: free in debug builds (to test with), "coming soon" until Google Play billing is in. */
    private fun buyPack(amount: Int) {
        if (!BuildConfig.DEBUG) {
            shopNoteAt = clock
            return
        }
        economy.add(amount)
        save()
        essencePulse = 1f
        sounds.play(Sounds.Fx.FREEZE, 0.8f)
    }

    /** The essence counter counts toward the real amount; today's gift pops up once nothing else is open. */
    private fun stepEssence(dt: Float) {
        val target = economy.essence.toFloat()
        val speed = max(2.5f, abs(target - shownEssence) * 1.6f) * dt
        shownEssence = if (shownEssence < target) min(target, shownEssence + speed) else max(target, shownEssence - speed)
        essencePulse = max(0f, essencePulse - dt / ESSENCE_PULSE_TIME)
        if (dailyGift != null && dialog == null) openDialog(Dialog.DAILY)
    }

    /**
     * Down at 0 HP: once your collapse has played, the revive popup opens. A revive plays in it, then
     * the popup closes and the duel starts over.
     */
    private fun stepRevive(dt: Float) {
        if (!battle.playerDown) return
        if (dialog == null && downTimer >= DEFEAT_ANIM_TIME + REVIVE_OFFER_DELAY) {
            reviveT = -1f
            openDialog(Dialog.REVIVE)
        }
        if (dialog == Dialog.REVIVE && reviveT >= 0f) {
            reviveT += dt
            if (reviveT >= REVIVE_ANIM_TIME + REVIVE_HOLD) restartAfterRevive()
        }
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
        // The revive popup steps back to the menu (it opens again in the duel), unless a revive is playing.
        dialog == Dialog.REVIVE -> {
            if (reviveT < 0f) {
                dialog = null
                screen = Screen.MENU
            }
            true
        }
        dialog != null -> { closeDialog(); true }
        screen == Screen.BATTLE -> { screen = Screen.MENU; true }
        else -> false
    }

    // ================================================================ simulation step

    private fun step(dt: Float) {
        clock += dt
        stepEssence(dt)
        if (screen == Screen.BATTLE) stepRevive(dt)
        // The duel waits while the menu, or a popup over the duel ("Choose your avatar"), is open.
        if (screen != Screen.BATTLE || dialog != null) return
        battle.update(dt)
        for (event in battle.drainEvents()) onEvent(event)
        // Your collapse is only needed if you lose: decode it once you're down to half your HP.
        if (battle.player.hp * 2 <= battle.player.maxHp) art.prepareSheet(avatar.defeatRes)
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
        // Your collapse starts as the last magic hits you.
        if ((battle.playerDown || battle.winner == Side.ENEMY) && sparks.isEmpty()) downTimer += dt
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
                if (e.side == Side.PLAYER) {
                    sounds.play(Sounds.Fx.CAST, MENU_CLICK_VOLUME)
                    castAnim = Random.nextInt(avatar.castRes.size) // one of the avatar's three casts
                    art.prepareAvatarCast(avatar, castAnim)
                }
                ringStyle[e.side.ordinal] = (ringStyle[e.side.ordinal] + 1) % Art.CAST_RING_STYLES
            }
            is BattleEvent.CastCancelled -> {
                sounds.play(Sounds.Fx.UI_BACK, 0.8f)
                // The dissolved spells pop back in; the staff lowers without a shot.
                e.cells.forEach { tilePop[it.row][it.col] = 0f }
                tappedRow = -1
                tappedCol = -1
                afterglow[Side.PLAYER.ordinal] = 0f
                // Starts below where the button was, so it fades out before reaching your HP bar
                // (no note when the cast ended because you went down).
                if (!battle.playerDown) texts += FloatText(s(R.string.cancelled), cancelCastButton.centerX(), cancelCastButton.bottom + 0.1f * u, Pal.TEXT_DIM, 0.32f * u, 0.8f)
            }
            is BattleEvent.WallCastStarted -> {
                if (e.side == Side.PLAYER) {
                    sounds.play(Sounds.Fx.CAST, MENU_CLICK_VOLUME)
                    castAnim = Random.nextInt(avatar.castRes.size)
                    art.prepareAvatarCast(avatar, castAnim)
                }
                ringStyle[e.side.ordinal] = (ringStyle[e.side.ordinal] + 1) % Art.CAST_RING_STYLES
            }
            is BattleEvent.WallCastCancelled -> {
                afterglow[e.side.ordinal] = 0f
                if (e.side == Side.PLAYER) {
                    if (!battle.playerDown) {
                        sounds.play(Sounds.Fx.UI_BACK, 0.8f)
                        texts += FloatText(s(R.string.cancelled), cancelCastButton.centerX(), cancelCastButton.bottom + 0.1f * u, Pal.TEXT_DIM, 0.32f * u, 0.8f)
                    }
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
            // A wall is magic crystal: it shatters into shards with a flash of its light, and its own
            // sound: a heavy glass crash with an icy shatter on top (not the globes' lighter pop).
            is BattleEvent.WallsBroken -> {
                sounds.play(Sounds.Fx.WALL_BREAK, 1f)
                sounds.play(Sounds.Fx.WALL_SHARDS, 0.55f)
                e.cells.forEach {
                    val x = cellX(it.col.toFloat())
                    val y = cellY(it.row.toFloat())
                    brokenWalls += BrokenWall(x, y, battle.board.wallLook(it.row, it.col), battle.board.wallOwner(it.row, it.col) == Side.PLAYER)
                    rings += Ring(x, y, mix(WALL_COLOR, WHITE, 0.4f), 0.2f * fu, 0.75f * fu, 0.3f)
                }
            }
            is BattleEvent.Launched -> {
                // The bot's drawn (vector) mage shoots a bolt line from its staff to the lane; the avatars'
                // cast animations (yours and a bot's Midjourney one) already throw the magic, so no line.
                if (e.side == Side.ENEMY && enemyAvatar == null) {
                    val from = staffTip(e.side)
                    bolts += Bolt(from.x, from.y, startX(e.side), laneY(e.lane), art.color(e.element))
                }
                afterglow[e.side.ordinal] = 1f
                lastCastColor[e.side.ordinal] = art.color(e.element)
                sounds.play(Sounds.Fx.LAUNCH, 0.55f)
            }
            // Elements with a freeze clip show the shot freezing (drawFreeze); the others get a frost ring.
            is BattleEvent.Landed -> {
                sounds.play(Sounds.Fx.FREEZE, 0.5f)
                if (art.freeze(e.element, e.side == Side.PLAYER) == null) rings += Ring(
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
            // A defeat sounds as you go down; giving up afterwards is quiet.
            is BattleEvent.GameOver -> if (e.winner == Side.PLAYER) sounds.play(Sounds.Fx.WIN)
            is BattleEvent.PlayerDown -> {
                downTimer = 0f
                sounds.play(Sounds.Fx.LOSE)
                // The revive popup plays the revive (or, without one, the collapse backwards).
                art.prepareSheet(avatar.defeatRes)
                avatar.reviveRes?.let { art.prepareSheet(it) }
            }
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
        // Popups (also "Choose your avatar" over the duel) take every tap while they're open.
        if (screen == Screen.MENU || dialog != null) {
            onMenuTap(x, y)
            return true
        }
        if (RectF(battleMenuButton).apply { inset(-0.2f * u, -0.2f * u) }.contains(x, y)) {
            screen = Screen.MENU
            return true
        }
        if (battle.playerDown) return true // the revive popup is coming
        if (battle.winner != null) {
            if (endTimer > endCardDelay() + TAP_DELAY) nextBattle()
            return true
        }
        if (hintCloseButton.contains(x, y)) {
            closeHint()
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            return true
        }
        if (battle.player.isCasting && RectF(cancelCastButton).apply { inset(-0.15f * u, -0.15f * u) }.contains(x, y)) {
            if (battle.cancelCast()) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            return true
        }
        // A tap on your mage opens "Choose your avatar" (the duel pauses meanwhile).
        if (playerAvatarRect.contains(x, y)) {
            avatarFromOptions = false
            openDialog(Dialog.AVATAR)
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            sounds.play(Sounds.Fx.UI_CLICK, 0.8f)
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
                hit == "ok" || hit == "close" -> closeDialog()
                hit == "lb_retry" -> leaderboard.open(displayName())
                hit == "avatar_open" -> {
                    avatarFromOptions = true
                    openDialog(Dialog.AVATAR)
                }
                hit != null && hit.startsWith("avatar_") -> {
                    val a = Avatars.byId(hit.removePrefix("avatar_"))
                    if (owns(a)) {
                        chooseAvatar(a)
                        closeDialog()
                    } else {
                        offerUnlock(a, Dialog.AVATAR)
                    }
                }
                // An avatar in the shop: unlock it, or put on one you own.
                hit != null && hit.startsWith("skin_") -> {
                    val a = Avatars.byId(hit.removePrefix("skin_"))
                    if (owns(a)) chooseAvatar(a) else offerUnlock(a, Dialog.SHOP)
                }
                // An element pack in the shop: unlock it, or use one you own.
                hit != null && hit.startsWith("epack_") -> {
                    val p = ElementPacks.byId(hit.removePrefix("epack_"))
                    if (ownsPack(p)) choosePack(p) else offerUnlock(p, Dialog.SHOP)
                }
                hit == "unlock" -> unlockOffered()
                hit == "unlock_cancel" -> closeDialog()
                hit == "unlock_shop" -> openDialog(Dialog.SHOP)
                hit == "sound" -> {
                    soundOn = !soundOn
                    sounds.enabled = soundOn
                    save()
                }
                hit == "name" -> editName()
                hit == "restart_open" -> openDialog(Dialog.RESTART)
                hit != null && hit.startsWith("lang_") -> setLanguage(hit.removePrefix("lang_"))
                hit == "collect" -> collectGift()
                hit == "revive" -> startRevive()
                hit != null && hit.startsWith("pack_") -> buyPack(hit.removePrefix("pack_").toInt())
                // The daily gift and the revive popup wait for their buttons.
                open == Dialog.DAILY || open == Dialog.REVIVE -> Unit
                // A tap outside the card closes it ("coming soon" cards close on any tap).
                open == Dialog.SHOP || open == Dialog.UNLOCK -> if (!dialogCard.contains(x, y)) closeDialog()
                open == Dialog.RESTART -> if (!dialogCard.contains(x, y)) openDialog(Dialog.OPTIONS)
                open == Dialog.OPTIONS -> if (!dialogCard.contains(x, y)) dialog = null
                open == Dialog.AVATAR -> if (!dialogCard.contains(x, y)) closeDialog()
                open == Dialog.LEADERBOARD -> if (!dialogCard.contains(x, y)) closeDialog()
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
            "leaderboard" -> {
                dialog = Dialog.LEADERBOARD
                leaderboard.open(displayName())
            }
            "pvp" -> dialog = Dialog.PVP
            "options" -> dialog = Dialog.OPTIONS
            "shop", "essence" -> dialog = Dialog.SHOP
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

    private fun staffTip(side: Side): PointF = when {
        side == Side.PLAYER -> avatarFrame(RectF(), playerX).let { PointF(it.left + avatar.tipX * it.width(), it.top + avatar.tipY * it.height()) }
        else -> enemyAvatar?.let { a -> enemyFrame(RectF(), enemyX, a).let { PointF(it.left + a.tipX * it.width(), it.top + a.tipY * it.height()) } }
            ?: art.mageOrb(enemyX, enemyFoot, enemyH, false, 1f)
    }

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
        dialog?.let { drawDialog(c, it) } // "Choose your avatar" over the paused duel
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
        val e = battle.enemy
        val castColor = castColor(e) ?: if (afterglow[1] > 0f) lastCastColor[1] else null
        val cast = if (e.isCasting) e.castProgress else afterglow[1]
        val sprite = enemyAvatar
        if (sprite == null) {
            art.drawMage(c, x, enemyFoot, enemyH, false, battle.opponent.robeColor, true, castColor, cast, clock, flash[1], DEFEAT_TILT * k, 1f - 0.6f * k)
            return
        }
        drawEnemySprite(c, sprite, x, castColor, cast)
    }

    /** Where a bot's sprite is drawn, standing on [enemyFoot] centred on [x]. */
    private fun enemyFrame(out: RectF, x: Float, a: EnemyAvatar): RectF {
        val img = art.image(a.idleRes)
        val h = enemyH * ENEMY_AVATAR_SCALE * a.scale
        val w = h * img.width / img.height
        out.set(x - w / 2f, enemyFoot - h, x + w / 2f, enemyFoot)
        return out
    }

    /**
     * A bot's Midjourney avatar, facing left. Like yours, its cast animation lasts as long as its
     * quickest cast (but at most BOT_CAST_ANIM_MAX, so it never crawls) and ends as the shot leaves;
     * afterwards it settles back into the idle pose. Hits flash it white and shake it. When you win it sinks into its defeated pose (the
     * last frame of its collapse, so the same figure in the same place).
     */
    private fun drawEnemySprite(c: Canvas, a: EnemyAvatar, x: Float, castColor: Int?, cast: Float) {
        val e = battle.enemy
        val frame = enemyFrame(RectF(), x, a)
        val h = frame.height()
        val beaten = if (battle.winner == Side.PLAYER) smooth01(endTimer / ENEMY_DEFEAT_FADE) else 0f
        val light = castColor ?: a.glow
        fill.shader = RadialGradient(
            frame.centerX(), frame.top + h * 0.45f, h * 0.55f,
            withAlpha(light, ((50 + 60 * cast) * (1f - 0.5f * beaten)).toInt()), withAlpha(light, 0), Shader.TileMode.CLAMP,
        )
        c.drawCircle(frame.centerX(), frame.top + h * 0.45f, h * 0.55f, fill)
        fill.shader = null
        avatarPaint.alpha = 255
        avatarPaint.colorFilter = if (flash[1] > 0.01f) PorterDuffColorFilter(withAlpha(WHITE, (170 * flash[1]).toInt()), PorterDuff.Mode.SRC_ATOP) else null
        val anim = if (e.isCasting && battle.winner == null) art.sheetIfReady(a.castRes) else null
        var clipT = 0f
        if (anim != null) {
            // Like yours, the clip lasts as long as the bot's quickest cast (but at most BOT_CAST_ANIM_MAX:
            // bots cast slowly) and ends as the shot leaves; until it starts the bot stands still.
            clipT = castClipT(e, min(e.shortestCastTime, BOT_CAST_ANIM_MAX))
            enemyLastCastFrame = drawClip(c, anim, clipT, frame, avatarPaint)
            enemyLastCastAnim = anim
            enemyCastEndedAt = -1f
        } else {
            if (beaten < 1f) {
                avatarPaint.alpha = (255 * (1f - beaten)).toInt()
                c.drawBitmap(art.image(a.idleRes), null, frame, avatarPaint)
            }
            if (beaten > 0f) {
                avatarPaint.alpha = (255 * beaten).toInt()
                c.drawBitmap(art.image(a.defeatedRes), null, frame, avatarPaint)
            }
            avatarPaint.alpha = 255
            val last = enemyLastCastAnim
            if (last != null) {
                if (enemyCastEndedAt < 0f) enemyCastEndedAt = clock
                val r = (clock - enemyCastEndedAt) / AVATAR_SETTLE_TIME
                if (r >= 1f || battle.winner != null) {
                    enemyLastCastAnim = null
                } else {
                    avatarPaint.alpha = (255 * (1f - smooth01(r))).toInt()
                    last.draw(c, enemyLastCastFrame, frame, avatarPaint)
                    avatarPaint.alpha = 255
                }
            }
        }
        avatarPaint.colorFilter = null
        val tipGlow = 1f - smooth01(clipT / 0.12f)
        if (castColor != null && cast > 0f && tipGlow > 0f && battle.winner == null) {
            val tx = frame.left + a.tipX * frame.width()
            val ty = frame.top + a.tipY * h
            val r = (0.2f + 0.3f * cast) * u
            fill.shader = RadialGradient(tx, ty, r, withAlpha(castColor, (200 * cast * tipGlow).toInt()), withAlpha(castColor, 0), Shader.TileMode.CLAMP)
            c.drawCircle(tx, ty, r, fill)
            fill.shader = null
        }
    }

    /**
     * How far (0 to 1) [caster]'s cast clip is, for a clip [length] seconds long that ends as the cast
     * completes: 0 until the last [length] seconds of a longer cast (the avatar stands still).
     */
    private fun castClipT(caster: Caster, length: Float): Float {
        val remaining = (1f - caster.castProgress) * caster.castTime
        return (1f - remaining / length).coerceIn(0f, 1f)
    }

    /**
     * Draws the point [t] (0 to 1) of [anim] into [dst]: between two frames the next one fades in over
     * the current one, so 48 close frames read as motion, not a slideshow. Returns the nearest frame.
     */
    private fun drawClip(c: Canvas, anim: Art.Anim, t: Float, dst: RectF, paint: Paint): Int {
        val pos = t.coerceIn(0f, 1f) * (anim.count - 1)
        val f = pos.toInt()
        anim.draw(c, f, dst, paint)
        val blend = pos - f
        if (blend > 0.05f && f + 1 < anim.count) {
            val base = paint.alpha
            paint.alpha = (base * blend).toInt()
            anim.draw(c, f + 1, dst, paint)
            paint.alpha = base
        }
        return min(anim.count - 1, f + if (blend >= 0.5f) 1 else 0)
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
        // Telegraph: the column a caster is raising walls in glows, with the seconds until the walls
        // rise at its top (the top cell is always empty while walls are cast into the column).
        for (side in Side.entries) {
            val caster = battle.caster(side)
            val column = caster.castWallColumn
            if (column < 0) continue
            val pulse = 0.5f + 0.5f * sin(clock * 10f)
            val x0 = fieldLeft + column * fu
            rect.set(x0 + 0.04f * fu, fieldTop + 0.02f * fu, x0 + 0.96f * fu, fb - 0.02f * fu)
            fill.color = withAlpha(WALL_COLOR, (14 + 40 * caster.castProgress * pulse).toInt())
            c.drawRoundRect(rect, 0.2f * u, 0.2f * u, fill)
            drawCountdown(c, rect.centerX(), rect.top + 0.2f * u, WALL_COLOR, caster.castTime * (1f - caster.castProgress))
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
            c.drawBitmap(art.wallBlock(size, board.wallLook(r, col), board.wallOwner(r, col) == Side.PLAYER), null, rect, bitmapPaint)
            if (k < 1f) {
                // A flash of conjuring light as it appears.
                fill.color = withAlpha(WALL_COLOR, (110 * (1f - k)).toInt())
                c.drawRoundRect(rect, 0.06f * size, 0.06f * size, fill)
            }
        }
        // A broken wall shatters (a Midjourney clip, art/walls/PROMPTS.md): its crystal bricks crumble into
        // shards that fly out past the cell, as the clip grows from the wall's size. The clip starts from
        // one of the wall looks, so the wall's own look fades out over its first frames.
        val clip = art.wallBreak()
        for (b in brokenWalls) {
            val k = b.t / WALL_BREAK_TIME
            if (clip != null) {
                val half = size * (1f + WALL_BREAK_GROW * easeOut(k)) / 2f
                rect.set(b.x - half, b.y - half, b.x + half, b.y + half)
                bitmapPaint.alpha = (255 * (1f - smooth01((k - 0.8f) / 0.2f))).toInt()
                drawClip(c, clip, k, rect, bitmapPaint)
            }
            // The wall itself (all of it while the clip is still decoding) fades out.
            val wall = if (clip != null) 1f - smooth01(k / 0.2f) else 1f - k
            if (wall > 0f) {
                rect.set(b.x - size / 2f, b.y - size / 2f, b.x + size / 2f, b.y + size / 2f)
                bitmapPaint.alpha = (255 * wall).toInt()
                c.drawBitmap(art.wallBlock(size, b.look, b.mine), null, rect, bitmapPaint)
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
     * border or animation, so it informs without pulling the eye. Wall casts show one too, at the top
     * of their glowing column: the seconds until the walls rise.
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
                drawShot(c, g.element, cellX(g.drawCol), cellY(g.drawRow), if (g.headingRight) 1f else -1f, 0f, g.look, mine = g.owner == Side.PLAYER)
                return@forEachGlobe
            }
            var scale = 1f
            var alpha = 1f
            val mine = g.owner == Side.PLAYER // your globes wear your element pack
            val hasFreeze = g.freeze > 0f && art.freeze(g.element, mine) != null
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
            if (alpha > 0f) art.drawGlobe(c, g.element, rect, animFrame(g.look), g.look, bitmapPaint, mine)
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
        val anim = art.freeze(g.element, g.owner == Side.PLAYER) ?: return
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
        if (live > 0f) drawShot(c, g.element, x, y, if (g.headingRight) 1f else -1f, 0f, g.look, live, g.owner == Side.PLAYER)
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
    private fun drawShot(c: Canvas, e: Element, x: Float, y: Float, dx: Float, dy: Float, look: Int, alpha: Float = 1f, mine: Boolean = true) {
        val anim = art.projectile(e, look, mine)
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
            val mine = p.maker == Side.PLAYER // your shots (and your globes pushed out) wear your element pack
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
            val mine = p.maker == Side.PLAYER // your shots (and your globes pushed out) wear your element pack
            // Every path is a straight line travelled at constant speed; the projectile's own loop is
            // its only animation (it fades in during the first moment, without moving or growing).
            when (p.state) {
                Projectile.State.FLYING -> drawShot(
                    c, p.element, sx + (ex - sx) * p.progress, laneY, dir, 0f, p.look, min(1f, p.flown / SHOT_FADE_IN), mine,
                )
                // The field is still moving: the shot holds at its edge until it can slide in.
                Projectile.State.WAITING -> drawShot(c, p.element, ex, laneY, dir, 0f, p.look, mine = mine)
                Projectile.State.BOUNCING -> {
                    // Backfire: the shot turns around and flies straight at its own caster.
                    val target = chest(p.owner)
                    val t = p.bounceProgress
                    drawShot(
                        c, p.element, ex + (target.x - ex) * t, laneY + (target.y - laneY) * t,
                        target.x - ex, target.y - laneY, p.look, mine = mine,
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
                    art.drawGlobe(c, p.element, rect, animFrame(p.look), p.look, bitmapPaint, mine)
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

    /**
     * The tutorial's current hint, in a card between the field and your panel, with a × in its corner
     * that closes it (the lesson carries on, and its next hint shows as usual).
     */
    private fun drawTutorial(c: Canvas) {
        hintCloseButton.setEmpty()
        val t = tutorial ?: return
        val key = t.hint?.takeIf { it !in closedHints } ?: return
        val hint = str.getString(str.getIdentifier(key, "string", context.packageName))
        if (!battle.started || battle.winner != null || battle.playerDown) return
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
        val lines = wrap(hint, size, x1 - x0 - 2 * pad - HINT_CLOSE * u)
        val bottom = playerTop - 0.1f * u
        val top = bottom - (lines.size * lineH + 2 * pad - 0.06f * u)
        rect.set(x0, top, x1, bottom)
        fill.color = withAlpha(Pal.CARD, 235)
        c.drawRoundRect(rect, 0.22f * u, 0.22f * u, fill)
        stroke.color = withAlpha(GOLD, (110 + 80 * pulse).toInt())
        stroke.strokeWidth = 0.035f * u
        c.drawRoundRect(rect, 0.22f * u, 0.22f * u, stroke)
        lines.forEachIndexed { i, s -> label(c, s, x0 + pad, top + pad + (i + 0.78f) * lineH, size, Pal.TEXT, Paint.Align.LEFT) }
        // The × in the top right corner; its tap area is a little bigger than the cross.
        val cx = x1 - pad - 0.12f * u
        val cy = top + pad + 0.14f * u
        val arm = 0.11f * u
        stroke.color = Pal.TEXT_DIM
        stroke.strokeWidth = 0.045f * u
        c.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, stroke)
        c.drawLine(cx - arm, cy + arm, cx + arm, cy - arm, stroke)
        hintCloseButton.set(cx - 0.4f * u, cy - 0.4f * u, cx + 0.4f * u, cy + 0.4f * u)
    }

    /** Closes the hint on screen: it stays closed for the rest of this duel. */
    private fun closeHint() {
        tutorial?.hint?.let { closedHints += it }
        hintCloseButton.setEmpty()
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

    /**
     * Start menu: your essence in the top right corner, Continue (or Start) and PvP duel (coming later)
     * as two big rows, then a row of three: leaderboard, shop and options.
     */
    private fun drawMenu(c: Canvas) {
        val cx = width / 2f
        val contentH = 11f * u
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
        // PvP duel: a row as big as Continue (it says "soon" until online duels exist).
        val pvp = RectF(bx, cont.bottom + 0.35f * u, bx + bw, cont.bottom + 0.35f * u + 1.25f * u)
        drawButton(c, pvp, s(R.string.pvp_duel), s(R.string.soon), primary = false, small = false)
        menuButtons["pvp"] = pvp
        by = pvp.bottom + 0.55f * u
        val sgap = 0.25f * u
        val sw = (bw - 2 * sgap) / 3f
        listOf(
            "leaderboard" to R.string.leaderboard,
            "shop" to R.string.shop,
            "options" to R.string.options,
        ).forEachIndexed { i, (id, title) ->
            val r = RectF(bx + i * (sw + sgap), by, bx + i * (sw + sgap) + sw, by + 0.9f * u)
            drawButton(c, r, s(title), null, primary = false, small = true)
            menuButtons[id] = r
        }
        drawEssenceCounter(c)
        dialog?.let { drawDialog(c, it) }
    }

    /** Your essence in the menu's top right corner: the crystal and the count. A tap opens the shop. */
    private fun drawEssenceCounter(c: Canvas) {
        val size = 0.4f * u
        val amount = "${(shownEssence + 0.5f).toInt()}"
        text.textSize = size
        val w = text.measureText(amount) + size * 0.95f + 0.55f * u
        val r = RectF(width - 0.35f * u - w, safeTop + 0.35f * u, width - 0.35f * u, safeTop + 0.35f * u + 0.75f * u)
        fill.color = withAlpha(Pal.CARD, 220)
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill)
        stroke.color = withAlpha(mix(ESSENCE, Pal.PANEL_EDGE, 0.5f - 0.5f * essencePulse), 230)
        stroke.strokeWidth = 0.035f * u
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, stroke)
        drawAmount(c, amount, r.centerX(), r.centerY() + size * 0.36f, size, Pal.TEXT, Paint.Align.CENTER, glow = essencePulse)
        menuButtons["essence"] = RectF(r).apply { inset(-0.2f * u, -0.2f * u) }
    }

    /**
     * The essence crystal: a glowing two-tone gem [size] tall centred on ([cx], [cy]); [glow] (0..1)
     * makes its halo flare.
     */
    private fun drawGem(c: Canvas, cx: Float, cy: Float, size: Float, alpha: Float = 1f, glow: Float = 0f) {
        val a = (255 * alpha.coerceIn(0f, 1f)).toInt()
        val hw = size * 0.36f
        val top = cy - size / 2f
        val bottom = cy + size / 2f
        val belt = cy - size * 0.12f
        val halo = size * (0.8f + 0.6f * glow)
        fill.shader = RadialGradient(cx, cy, halo, withAlpha(ESSENCE, ((60 + 130 * glow) * alpha).toInt()), withAlpha(ESSENCE, 0), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, halo, fill)
        fill.shader = null
        // The lit left side, the shaded right side and the bright crown on top.
        path.reset(); path.moveTo(cx, top); path.lineTo(cx - hw, belt); path.lineTo(cx, bottom); path.close()
        fill.color = withAlpha(ESSENCE_LIGHT, a)
        c.drawPath(path, fill)
        path.reset(); path.moveTo(cx, top); path.lineTo(cx + hw, belt); path.lineTo(cx, bottom); path.close()
        fill.color = withAlpha(ESSENCE_DARK, a)
        c.drawPath(path, fill)
        path.reset(); path.moveTo(cx, top); path.lineTo(cx - hw, belt); path.lineTo(cx + hw, belt); path.close()
        fill.color = withAlpha(WHITE, (120 * alpha).toInt())
        c.drawPath(path, fill)
        stroke.color = withAlpha(mix(ESSENCE, WHITE, 0.6f), a)
        stroke.strokeWidth = size * 0.05f
        path.reset(); path.moveTo(cx, top); path.lineTo(cx + hw, belt); path.lineTo(cx, bottom); path.lineTo(cx - hw, belt); path.close()
        c.drawPath(path, stroke)
        c.drawLine(cx - hw, belt, cx + hw, belt, stroke)
    }

    /** [s] (an amount) followed by the essence crystal, aligned on [x] as a whole. */
    private fun drawAmount(
        c: Canvas, s: String, x: Float, baseline: Float, size: Float, color: Int, align: Paint.Align,
        alpha: Float = 1f, glow: Float = 0f,
    ) {
        text.textSize = size
        val tw = text.measureText(s)
        val gap = size * 0.22f
        val gw = size * 0.7f
        val total = tw + gap + gw
        val x0 = when (align) {
            Paint.Align.LEFT -> x
            Paint.Align.RIGHT -> x - total
            else -> x - total / 2f
        }
        label(c, s, x0, baseline, size, color, Paint.Align.LEFT, alpha)
        drawGem(c, x0 + tw + gap + gw / 2f, baseline - size * 0.36f, size * 0.95f, alpha, glow)
    }

    /** A popup card [w] × [h] in the middle of the screen with its gold [title]; returns the card. */
    private fun drawCard(c: Canvas, w: Float, h: Float, title: String): RectF {
        val cx = width / 2f
        val cy = height / 2f
        dialogCard.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        fill.color = Pal.CARD
        c.drawRoundRect(dialogCard, 0.35f * u, 0.35f * u, fill)
        stroke.color = GOLD
        stroke.strokeWidth = 0.05f * u
        c.drawRoundRect(dialogCard, 0.35f * u, 0.35f * u, stroke)
        label(c, title, cx, dialogCard.top + 0.85f * u, 0.46f * u, GOLD, Paint.Align.CENTER)
        return dialogCard
    }

    /**
     * The daily gift, on the first launch of the day: the seven days of the streak (the days behind
     * you ticked off, today's lit up), today's essence and streak bonus, their total and Collect.
     */
    private fun drawDaily(c: Canvas) {
        val gift = dailyGift ?: return
        val w = min(width - 1.0f * u, 7.8f * u)
        val pad = 0.35f * u
        val chipGap = 0.12f * u
        val chipW = (w - 2 * pad - 6 * chipGap) / Economy.STREAK_DAYS
        val chipH = chipW * 1.6f
        val lineH = 0.52f * u
        val hint = wrap(s(R.string.daily_hint), 0.26f * u, w - 2 * pad)
        val h = 1.3f * u + 0.5f * u + chipH + 0.35f * u + 3 * lineH + 0.2f * u + hint.size * 0.36f * u + 0.3f * u + 0.9f * u + pad
        val card = drawCard(c, w, h, s(R.string.daily_title))
        val cx = card.centerX()
        var y = card.top + 1.3f * u
        label(c, s(R.string.daily_streak_fmt, gift.streakDay), cx, y + 0.15f * u, 0.3f * u, Pal.TEXT_DIM, Paint.Align.CENTER)
        y += 0.5f * u
        val pulse = 0.5f + 0.5f * sin(clock * 4f)
        for (d in 1..Economy.STREAK_DAYS) {
            val x = card.left + pad + (d - 1) * (chipW + chipGap)
            val r = RectF(x, y, x + chipW, y + chipH)
            val past = d < gift.streakDay
            val today = d == gift.streakDay
            fill.color = if (today) mix(Pal.CARD, GOLD, 0.16f) else withAlpha(BLACK, if (past) 110 else 70)
            c.drawRoundRect(r, 0.18f * u, 0.18f * u, fill)
            stroke.color = if (today) withAlpha(GOLD, (170 + 85 * pulse).toInt()) else withAlpha(WHITE, 40)
            stroke.strokeWidth = (if (today) 0.05f else 0.03f) * u
            c.drawRoundRect(r, 0.18f * u, 0.18f * u, stroke)
            val dim = if (past) 0.45f else 1f
            label(c, "$d", r.centerX(), r.top + 0.33f * u, 0.24f * u, if (today) GOLD else Pal.TEXT_DIM, Paint.Align.CENTER)
            drawGem(c, r.centerX(), r.centerY() + 0.02f * u, chipW * 0.42f, dim, if (today) pulse else 0f)
            label(c, "+${Economy.DAILY_ESSENCE + Economy.STREAK_BONUS[d - 1]}", r.centerX(), r.bottom - 0.15f * u, 0.26f * u, if (today) GOLD else Pal.TEXT, Paint.Align.CENTER, dim)
            if (past) {
                // A tick over the days already collected.
                stroke.color = Pal.PLAYER_HP
                stroke.strokeWidth = 0.06f * u
                val tx = r.centerX()
                val ty = r.centerY()
                val k = chipW * 0.2f
                path.reset(); path.moveTo(tx - k, ty); path.lineTo(tx - k * 0.25f, ty + k * 0.75f); path.lineTo(tx + k * 1.1f, ty - k * 0.8f)
                c.drawPath(path, stroke)
            }
        }
        y += chipH + 0.35f * u
        val x0 = card.left + pad + 0.2f * u
        val x1 = card.right - pad - 0.2f * u
        label(c, s(R.string.daily_base), x0, y + 0.35f * u, 0.3f * u, Pal.TEXT, Paint.Align.LEFT)
        drawAmount(c, "+${gift.base}", x1, y + 0.35f * u, 0.3f * u, Pal.TEXT, Paint.Align.RIGHT)
        y += lineH
        label(c, s(R.string.daily_bonus), x0, y + 0.35f * u, 0.3f * u, Pal.TEXT, Paint.Align.LEFT)
        drawAmount(c, "+${gift.bonus}", x1, y + 0.35f * u, 0.3f * u, if (gift.bonus > 0) Pal.TEXT else Pal.TEXT_DIM, Paint.Align.RIGHT)
        y += lineH
        stroke.color = withAlpha(GOLD, 90)
        stroke.strokeWidth = 0.025f * u
        c.drawLine(x0, y + 0.02f * u, x1, y + 0.02f * u, stroke)
        label(c, s(R.string.daily_total), x0, y + 0.42f * u, 0.36f * u, GOLD, Paint.Align.LEFT)
        drawAmount(c, "+${gift.total}", x1, y + 0.42f * u, 0.38f * u, GOLD, Paint.Align.RIGHT, glow = pulse)
        y += lineH + 0.2f * u
        hint.forEachIndexed { i, line -> label(c, line, cx, y + 0.26f * u + i * 0.36f * u, 0.26f * u, Pal.TEXT_DIM, Paint.Align.CENTER) }
        val by = card.bottom - pad - 0.9f * u
        val collect = RectF(cx - 1.8f * u, by, cx + 1.8f * u, by + 0.9f * u)
        drawButton(c, collect, s(R.string.collect), null, primary = true, small = false)
        dialogButtons["collect"] = collect
    }

    /**
     * The revive popup, after your collapse: your fallen avatar and Revive (free; the only way on). It
     * plays here: the avatar rises in a glow of its crystal's colour, then the duel starts over.
     */
    private fun drawRevive(c: Canvas) {
        val w = min(width - 1.2f * u, 7.4f * u)
        val pad = 0.35f * u
        val picH = 4.6f * u
        val playing = reviveT >= 0f
        val msg = wrap(s(R.string.revive_msg), 0.3f * u, w - 2 * pad)
        val h = 1.3f * u + picH + 0.3f * u + msg.size * 0.42f * u + 0.35f * u + 0.9f * u + pad
        val card = drawCard(c, w, h, s(if (playing) R.string.reviving else R.string.revive_title))
        val cx = card.centerX()
        val pic = RectF(card.left + pad, card.top + 1.3f * u, card.right - pad, card.top + 1.3f * u + picH)
        fill.color = withAlpha(BLACK, 90)
        c.drawRoundRect(pic, 0.25f * u, 0.25f * u, fill)
        val t = if (playing) (reviveT / REVIVE_ANIM_TIME).coerceIn(0f, 1f) else 0f
        // The glow swells as the avatar rises and settles once it stands.
        val light = if (playing) 0.35f + 0.65f * sin(PI.toFloat() * min(1f, t * 1.15f)).coerceAtLeast(0f) + 0.25f * smooth01((reviveT - REVIVE_ANIM_TIME) / REVIVE_HOLD) else 0.25f
        fill.shader = RadialGradient(pic.centerX(), pic.bottom - picH * 0.4f, picH * 0.6f, withAlpha(avatar.glow, (40 + 110 * light).toInt()), withAlpha(avatar.glow, 0), Shader.TileMode.CLAMP)
        c.drawRoundRect(pic, 0.25f * u, 0.25f * u, fill)
        fill.shader = null
        c.save()
        c.clipRect(pic)
        val idle = art.avatarIdle(avatar)
        val fh = picH * 0.98f
        val fw = fh * idle.width / idle.height
        val frame = RectF(pic.centerX() - fw / 2f, pic.bottom - fh, pic.centerX() + fw / 2f, pic.bottom)
        avatarPaint.alpha = 255
        avatarPaint.colorFilter = null
        val defeat = art.sheetIfReady(avatar.defeatRes)
        val rise = avatar.reviveRes?.let { art.sheetIfReady(it) }
        when {
            playing && t >= 1f -> c.drawBitmap(idle, null, frame, avatarPaint)
            playing && rise != null -> drawClip(c, rise, t, frame, avatarPaint)
            // Without its own revive clip the avatar plays its collapse backwards.
            playing && defeat != null -> drawClip(c, defeat, 1f - t, frame, avatarPaint)
            playing -> {
                avatarPaint.alpha = (255 * t).toInt()
                c.drawBitmap(idle, null, frame, avatarPaint)
            }
            defeat != null -> defeat.draw(c, defeat.count - 1, frame, avatarPaint)
            else -> c.drawBitmap(idle, null, frame, avatarPaint)
        }
        avatarPaint.alpha = 255
        if (playing) {
            // Motes of light drifting up around the figure.
            for (i in 0 until 18) {
                val k = (i * 0.6180339f) % 1f
                val p = (reviveT * 0.55f + i * 0.137f) % 1f
                val mx = pic.centerX() + (k - 0.5f) * fw * 0.9f + sin(reviveT * 2f + i) * 0.1f * u
                val my = pic.bottom - p * picH
                fill.color = withAlpha(mix(avatar.glow, WHITE, 0.4f), (200 * sin(PI.toFloat() * p) * min(1f, light)).toInt())
                c.drawCircle(mx, my, (0.03f + 0.04f * k) * u, fill)
            }
        }
        c.restore()
        stroke.color = withAlpha(GOLD, 120)
        stroke.strokeWidth = 0.035f * u
        c.drawRoundRect(pic, 0.25f * u, 0.25f * u, stroke)
        val y = pic.bottom + 0.3f * u
        msg.forEachIndexed { i, line -> label(c, line, cx, y + 0.3f * u + i * 0.42f * u, 0.3f * u, Pal.TEXT, Paint.Align.CENTER) }
        if (playing) return
        val by = card.bottom - pad - 0.9f * u
        val go = RectF(cx - 1.8f * u, by, cx + 1.8f * u, by + 0.9f * u)
        drawButton(c, go, s(R.string.revive), null, primary = true, small = false)
        dialogButtons["revive"] = go
    }

    /**
     * The shop: essence packs (free to test in debug builds; bought through Google Play later) and the
     * paid avatars, unlocked with essence (a card says more are coming). Your essence shows in the corner.
     */
    private fun drawShop(c: Canvas) {
        val w = min(width - 1.2f * u, 7.6f * u)
        val pad = 0.35f * u
        val gap = 0.25f * u
        val packW = (w - 2 * pad - (SHOP_PACKS.size - 1) * gap) / SHOP_PACKS.size
        val packH = packW * 1.3f
        val skinsH = packW * 1.75f
        val elH = packW * 1.45f
        val h = 1.3f * u + 0.5f * u + packH + 0.55f * u + 0.5f * u + skinsH + 0.3f * u + 0.5f * u + elH + 0.35f * u + 0.8f * u + pad
        val card = drawCard(c, w, h, s(R.string.shop))
        val cx = card.centerX()
        drawAmount(c, "${(shownEssence + 0.5f).toInt()}", card.right - pad, card.top + 0.8f * u, 0.34f * u, Pal.TEXT, Paint.Align.RIGHT, glow = essencePulse)
        var y = card.top + 1.3f * u
        label(c, s(R.string.shop_essence), card.left + pad, y + 0.3f * u, 0.28f * u, Pal.TEXT_DIM, Paint.Align.LEFT)
        y += 0.5f * u
        SHOP_PACKS.forEachIndexed { i, amount ->
            val x = card.left + pad + i * (packW + gap)
            val r = RectF(x, y, x + packW, y + packH)
            fill.color = withAlpha(BLACK, 90)
            c.drawRoundRect(r, 0.25f * u, 0.25f * u, fill)
            fill.shader = RadialGradient(r.centerX(), r.top + packH * 0.38f, packW * 0.6f, withAlpha(ESSENCE, 50 + 25 * i), withAlpha(ESSENCE, 0), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 0.25f * u, 0.25f * u, fill)
            fill.shader = null
            // One, two or three crystals: the bigger the pack, the bigger the pile.
            val gy = r.top + packH * 0.36f
            val gs = packW * 0.34f
            when (i) {
                0 -> drawGem(c, r.centerX(), gy, gs * 1.15f)
                1 -> {
                    drawGem(c, r.centerX() - gs * 0.3f, gy + gs * 0.08f, gs)
                    drawGem(c, r.centerX() + gs * 0.3f, gy - gs * 0.05f, gs * 1.1f)
                }
                else -> {
                    drawGem(c, r.centerX() - gs * 0.45f, gy + gs * 0.12f, gs * 0.9f)
                    drawGem(c, r.centerX() + gs * 0.45f, gy + gs * 0.12f, gs * 0.9f)
                    drawGem(c, r.centerX(), gy - gs * 0.08f, gs * 1.15f)
                }
            }
            label(c, "$amount", r.centerX(), r.top + packH * 0.72f, 0.46f * u, Pal.TEXT, Paint.Align.CENTER)
            label(c, s(if (BuildConfig.DEBUG) R.string.shop_free_test else R.string.soon), r.centerX(), r.bottom - 0.2f * u, 0.24f * u, if (BuildConfig.DEBUG) Pal.PLAYER_HP else Pal.TEXT_DIM, Paint.Align.CENTER)
            stroke.color = withAlpha(ESSENCE, 110)
            stroke.strokeWidth = 0.035f * u
            c.drawRoundRect(r, 0.25f * u, 0.25f * u, stroke)
            dialogButtons["pack_$amount"] = r
        }
        y += packH
        val note = 1f - smooth01((clock - shopNoteAt - 1.6f) / 0.4f)
        if (note > 0f) label(c, s(R.string.shop_soon_msg), cx, y + 0.38f * u, 0.26f * u, GOLD, Paint.Align.CENTER, note)
        y += 0.55f * u
        label(c, s(R.string.shop_avatars), card.left + pad, y + 0.3f * u, 0.28f * u, Pal.TEXT_DIM, Paint.Align.LEFT)
        y += 0.5f * u
        // The paid avatars: the figure on its crystal's light, its name, and its price (or Chosen /
        // Owned once unlocked; a tap on one you own puts it on). The last card says more are coming.
        val forSale = Avatars.paid
        for (i in 0..forSale.size) {
            val x = card.left + pad + i * (packW + gap)
            val r = RectF(x, y, x + packW, y + skinsH)
            fill.color = withAlpha(BLACK, 90)
            c.drawRoundRect(r, 0.25f * u, 0.25f * u, fill)
            val a = forSale.getOrNull(i)
            if (a == null) {
                stroke.color = withAlpha(WHITE, 40)
                stroke.strokeWidth = 0.03f * u
                c.drawRoundRect(r, 0.25f * u, 0.25f * u, stroke)
                val lines = wrap(s(R.string.avatar_more), 0.26f * u, packW - 0.3f * u)
                val ly = r.centerY() - (lines.size - 1) * 0.18f * u + 0.09f * u
                lines.forEachIndexed { k, line -> label(c, line, r.centerX(), ly + k * 0.36f * u, 0.26f * u, Pal.TEXT_DIM, Paint.Align.CENTER) }
                continue
            }
            val owned = owns(a)
            fill.shader = RadialGradient(r.centerX(), r.top + skinsH * 0.4f, packW * 0.62f, withAlpha(a.glow, 70), withAlpha(a.glow, 0), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 0.25f * u, 0.25f * u, fill)
            fill.shader = null
            val src = art.opaqueBounds(a.idleRes)
            val ih = skinsH - 1.05f * u
            val iw = ih * src.width() / src.height()
            bitmapPaint.alpha = if (owned) 255 else 150
            c.drawBitmap(art.avatarIdle(a), src, RectF(r.centerX() - iw / 2f, r.top + 0.12f * u, r.centerX() + iw / 2f, r.top + 0.12f * u + ih), bitmapPaint)
            bitmapPaint.alpha = 255
            label(c, s(a.nameRes), r.centerX(), r.bottom - 0.58f * u, 0.26f * u, Pal.TEXT, Paint.Align.CENTER)
            val chosen = a.id == avatar.id
            when {
                chosen -> label(c, s(R.string.avatar_chosen), r.centerX(), r.bottom - 0.18f * u, 0.24f * u, GOLD, Paint.Align.CENTER)
                owned -> label(c, s(R.string.avatar_owned), r.centerX(), r.bottom - 0.18f * u, 0.24f * u, Pal.TEXT_DIM, Paint.Align.CENTER)
                else -> drawAmount(c, "${a.price}", r.centerX(), r.bottom - 0.17f * u, 0.3f * u, GOLD, Paint.Align.CENTER)
            }
            stroke.color = if (chosen) GOLD else withAlpha(a.glow, 120)
            stroke.strokeWidth = (if (chosen) 0.06f else 0.035f) * u
            c.drawRoundRect(r, 0.25f * u, 0.25f * u, stroke)
            dialogButtons["skin_${a.id}"] = r
        }
        y += skinsH + 0.3f * u
        // Element packs: the classic look and the paid ones, each with its four globes; the last card
        // says more are coming. A tap uses a pack you own, or opens the unlock popup.
        label(c, s(R.string.shop_packs), card.left + pad, y + 0.3f * u, 0.28f * u, Pal.TEXT_DIM, Paint.Align.LEFT)
        y += 0.5f * u
        val packs = ElementPacks.all
        for (i in 0..packs.size) {
            if (i >= 3) break
            val x = card.left + pad + i * (packW + gap)
            val r = RectF(x, y, x + packW, y + elH)
            fill.color = withAlpha(BLACK, 90)
            c.drawRoundRect(r, 0.25f * u, 0.25f * u, fill)
            val p = packs.getOrNull(i)
            if (p == null) {
                stroke.color = withAlpha(WHITE, 40)
                stroke.strokeWidth = 0.03f * u
                c.drawRoundRect(r, 0.25f * u, 0.25f * u, stroke)
                val lines = wrap(s(R.string.pack_more), 0.26f * u, packW - 0.3f * u)
                val ly = r.centerY() - (lines.size - 1) * 0.18f * u + 0.09f * u
                lines.forEachIndexed { k, line -> label(c, line, r.centerX(), ly + k * 0.36f * u, 0.26f * u, Pal.TEXT_DIM, Paint.Align.CENTER) }
                continue
            }
            val owned = ownsPack(p)
            fill.shader = RadialGradient(r.centerX(), r.top + elH * 0.38f, packW * 0.62f, withAlpha(p.glow, 60), withAlpha(p.glow, 0), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 0.25f * u, 0.25f * u, fill)
            fill.shader = null
            // Its four globes, two by two.
            val gs = min((packW - 0.45f * u) / 2f, (elH - 1.05f * u) / 2f)
            val gx0 = r.centerX() - gs - 0.04f * u
            val gy0 = r.top + 0.12f * u
            bitmapPaint.alpha = if (owned) 255 else 170
            Element.active(Rules.START_ELEMENTS).take(4).forEachIndexed { k, e ->
                val gx = gx0 + (k % 2) * (gs + 0.08f * u)
                val gy = gy0 + (k / 2) * (gs + 0.08f * u)
                art.packGlobe(p, e)?.draw(c, animFrame(e.ordinal * 5), RectF(gx, gy, gx + gs, gy + gs), bitmapPaint)
            }
            bitmapPaint.alpha = 255
            label(c, s(p.nameRes), r.centerX(), r.bottom - 0.58f * u, 0.24f * u, Pal.TEXT, Paint.Align.CENTER)
            val chosen = p.id == pack.id
            when {
                chosen -> label(c, s(R.string.avatar_chosen), r.centerX(), r.bottom - 0.18f * u, 0.24f * u, GOLD, Paint.Align.CENTER)
                owned -> label(c, s(R.string.avatar_owned), r.centerX(), r.bottom - 0.18f * u, 0.24f * u, Pal.TEXT_DIM, Paint.Align.CENTER)
                else -> drawAmount(c, "${p.price}", r.centerX(), r.bottom - 0.17f * u, 0.3f * u, GOLD, Paint.Align.CENTER)
            }
            stroke.color = if (chosen) GOLD else withAlpha(p.glow, 120)
            stroke.strokeWidth = (if (chosen) 0.06f else 0.035f) * u
            c.drawRoundRect(r, 0.25f * u, 0.25f * u, stroke)
            dialogButtons["epack_${p.id}"] = r
        }
        val by = card.bottom - pad - 0.8f * u
        val close = RectF(cx - 1.4f * u, by, cx + 1.4f * u, by + 0.8f * u)
        drawButton(c, close, s(R.string.close), null, primary = false, small = true)
        dialogButtons["close"] = close
    }

    /**
     * Element pack [p]'s look in [area]: its four globes in a row (animated), and under them one of its
     * walls next to a flying shot.
     */
    private fun drawPackPreview(c: Canvas, p: ElementPack, area: RectF) {
        val elements = Element.active(Rules.START_ELEMENTS)
        val gap = 0.12f * area.width() / elements.size
        val gs = min((area.width() - 0.4f * u - gap * (elements.size - 1)) / elements.size, area.height() * 0.42f)
        val gy = area.top + 0.2f * u
        var gx = area.centerX() - (gs * elements.size + gap * (elements.size - 1)) / 2f
        for (e in elements) {
            val anim = art.packGlobe(p, e)
            if (anim != null) anim.draw(c, animFrame(e.ordinal * 5), RectF(gx, gy, gx + gs, gy + gs), bitmapPaint)
            gx += gs + gap
        }
        val rowTop = gy + gs + 0.25f * u
        val ws = min(area.bottom - 0.2f * u - rowTop, area.width() * 0.3f)
        val shot = art.packShot(p, Element.FIRE)
        val shotH = ws * 0.9f
        val shotW = min(shotH * (shot?.aspect ?: 2f), area.width() - ws - 0.9f * u)
        val left = area.centerX() - (ws + 0.3f * u + shotW) / 2f
        c.drawBitmap(art.packWall(p, ws.toInt().coerceAtLeast(8)), null, RectF(left, rowTop, left + ws, rowTop + ws), bitmapPaint)
        if (shot != null) {
            val sx = left + ws + 0.3f * u
            val sy = rowTop + (ws - shotH) / 2f
            shot.draw(c, animFrame(3), RectF(sx, sy, sx + shotW, sy + shotH), bitmapPaint)
        }
    }

    /**
     * Unlocking a paid avatar or element pack: the avatar's figure on its crystal's light (or the pack's
     * globes, a wall and a flying shot), its price and the essence you have, with Cancel and Unlock.
     * Short of essence, a note says where to get more and Shop replaces Unlock.
     */
    private fun drawUnlock(c: Canvas) {
        val a = unlocking
        val p = unlockingPack
        if (a == null && p == null) return
        val price = a?.price ?: p!!.price
        val glow = a?.glow ?: p!!.glow
        val w = min(width - 1.6f * u, 6.8f * u)
        val pad = 0.35f * u
        val picH = 3.8f * u
        val enough = economy.essence >= price
        val note = if (enough) emptyList() else wrap(s(R.string.not_enough_essence), 0.28f * u, w - 2 * pad)
        val h = 1.3f * u + picH + 0.25f * u + 0.6f * u + 0.45f * u + note.size * 0.38f * u + 0.35f * u + 0.85f * u + pad
        val card = drawCard(c, w, h, s(R.string.unlock_title_fmt, s(a?.nameRes ?: p!!.nameRes)))
        val cx = card.centerX()
        val pic = RectF(card.left + pad, card.top + 1.3f * u, card.right - pad, card.top + 1.3f * u + picH)
        fill.color = withAlpha(BLACK, 90)
        c.drawRoundRect(pic, 0.25f * u, 0.25f * u, fill)
        fill.shader = RadialGradient(pic.centerX(), pic.top + picH * 0.45f, picH * 0.6f, withAlpha(glow, 90), withAlpha(glow, 0), Shader.TileMode.CLAMP)
        c.drawRoundRect(pic, 0.25f * u, 0.25f * u, fill)
        fill.shader = null
        if (a != null) {
            val src = art.opaqueBounds(a.idleRes)
            val ih = picH - 0.3f * u
            val iw = ih * src.width() / src.height()
            c.drawBitmap(art.avatarIdle(a), src, RectF(cx - iw / 2f, pic.top + 0.15f * u, cx + iw / 2f, pic.top + 0.15f * u + ih), bitmapPaint)
        } else if (p != null) {
            drawPackPreview(c, p, pic)
        }
        stroke.color = withAlpha(glow, 140)
        stroke.strokeWidth = 0.035f * u
        c.drawRoundRect(pic, 0.25f * u, 0.25f * u, stroke)
        var y = pic.bottom + 0.25f * u
        drawAmount(c, "$price", cx, y + 0.48f * u, 0.5f * u, GOLD, Paint.Align.CENTER)
        y += 0.6f * u
        drawAmount(c, s(R.string.you_have_fmt, (shownEssence + 0.5f).toInt()), cx, y + 0.34f * u, 0.28f * u, Pal.TEXT_DIM, Paint.Align.CENTER, glow = essencePulse)
        y += 0.45f * u
        note.forEachIndexed { i, line -> label(c, line, cx, y + 0.3f * u + i * 0.38f * u, 0.28f * u, Pal.TEXT, Paint.Align.CENTER) }
        val by = card.bottom - pad - 0.85f * u
        val half = (w - 2 * pad - 0.3f * u) / 2f
        val cancel = RectF(card.left + pad, by, card.left + pad + half, by + 0.85f * u)
        val go = RectF(card.right - pad - half, by, card.right - pad, by + 0.85f * u)
        drawButton(c, cancel, s(R.string.cancel), null, primary = false, small = true)
        dialogButtons["unlock_cancel"] = cancel
        drawButton(c, go, s(if (enough) R.string.unlock else R.string.shop), null, primary = true, small = true)
        dialogButtons[if (enough) "unlock" else "unlock_shop"] = go
    }

    private fun openDialog(d: Dialog) {
        dialog = d
        dialogOpenedAt = clock
    }

    /**
     * Closes the open popup: the restart question and an avatar choice opened from Options go back to
     * Options; closing the daily gift collects it.
     * The revive popup stays until you revive (Back steps out to the menu).
     */
    private fun closeDialog() {
        if (dialog == Dialog.DAILY) {
            collectGift()
            return
        }
        dialog = when {
            dialog == Dialog.RESTART -> Dialog.OPTIONS
            dialog == Dialog.AVATAR && avatarFromOptions -> Dialog.OPTIONS
            dialog == Dialog.UNLOCK -> unlockFrom
            dialog == Dialog.REVIVE -> Dialog.REVIVE
            else -> null
        }
        if (dialog != null) dialogOpenedAt = clock
    }

    private fun setLanguage(code: String) {
        language = code
        str = localized(code)
        save()
    }

    /** Your name, typed on the phone's keyboard in a small system dialog ([first]: the welcome question on first launch). */
    private fun editName(first: Boolean = false) {
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
            .setTitle(s(if (first) R.string.first_name_title else R.string.your_name))
            .setView(box)
            .setPositiveButton(s(R.string.save)) { _, _ ->
                playerName = input.text.toString().trim().take(NAME_MAX)
                save()
                leaderboard.sync(displayName()) // the leaderboard shows the new name
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
        val avatarH = 3.6f * u
        val h = 1.25f * u + 0.45f * u + rowH + 0.35f * u + 2 * (rowH + 0.3f * u) + avatarH + 0.3f * u + 0.25f * u + rowH + 0.35f * u + rowH + pad
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
        y += rowH + 0.3f * u
        // Your avatar: the full figure on a card (name below, a soft glow in its colour); a tap opens
        // "Choose your avatar".
        label(c, s(R.string.avatar), x0, y + rowH / 2f + 0.1f * u, 0.3f * u, Pal.TEXT, Paint.Align.LEFT)
        val av = RectF(x1 - bw, y, x1, y + avatarH)
        fill.color = withAlpha(BLACK, 90)
        c.drawRoundRect(av, 0.25f * u, 0.25f * u, fill)
        fill.shader = RadialGradient(av.centerX(), av.top + avatarH * 0.42f, bw * 0.65f, withAlpha(avatar.glow, 70), withAlpha(avatar.glow, 0), Shader.TileMode.CLAMP)
        c.drawRoundRect(av, 0.25f * u, 0.25f * u, fill)
        fill.shader = null
        val img = art.avatarIdle(avatar)
        val ih = avatarH - 0.7f * u
        val src = art.opaqueBounds(avatar.idleRes) // the figure, without the frame's empty room
        val iw = ih * src.width() / src.height()
        rect.set(av.centerX() - iw / 2f, av.top + 0.08f * u, av.centerX() + iw / 2f, av.top + 0.08f * u + ih)
        c.drawBitmap(img, src, rect, bitmapPaint)
        label(c, s(avatar.nameRes), av.centerX(), av.bottom - 0.25f * u, 0.3f * u, Pal.TEXT, Paint.Align.CENTER)
        stroke.color = withAlpha(GOLD, 150)
        stroke.strokeWidth = 0.04f * u
        c.drawRoundRect(av, 0.25f * u, 0.25f * u, stroke)
        dialogButtons["avatar_open"] = av
        y += avatarH + 0.3f * u + 0.25f * u

        val restart = RectF(x0, y, x1, y + rowH)
        drawButton(c, restart, s(R.string.restart_game), null, primary = false, small = true, danger = true)
        dialogButtons["restart_open"] = restart
        y += rowH + 0.35f * u
        val close = RectF(cx - 1.4f * u, y, cx + 1.4f * u, y + rowH)
        drawButton(c, close, s(R.string.close), null, primary = false, small = true)
        dialogButtons["close"] = close
    }

    /**
     * "Choose your avatar": a card per avatar in a grid of two, each with its idle pose and name: the
     * free one marked Free, the paid ones you haven't unlocked dimmed with their price. Yours is outlined
     * in gold. A last card says more avatars are coming. A tap on one you own picks it and closes the
     * popup; on a locked one it opens the unlock popup.
     */
    private fun drawAvatarPicker(c: Canvas) {
        val w = min(width - 1.2f * u, 7.6f * u)
        val pad = 0.35f * u
        val gap = 0.3f * u
        val cols = 2
        val cardW = (w - 2 * pad - (cols - 1) * gap) / cols
        val cardH = cardW * 1.45f
        val slots = Avatars.all.size + 1 // + "more avatars soon"
        val rows = (slots + cols - 1) / cols
        val h = 1.3f * u + rows * cardH + (rows - 1) * gap + 0.35f * u + 0.8f * u + pad
        val cx = width / 2f
        val cy = height / 2f
        dialogCard.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        val card = dialogCard
        fill.color = Pal.CARD
        c.drawRoundRect(card, 0.35f * u, 0.35f * u, fill)
        stroke.color = GOLD
        stroke.strokeWidth = 0.05f * u
        c.drawRoundRect(card, 0.35f * u, 0.35f * u, stroke)
        label(c, s(R.string.choose_avatar), cx, card.top + 0.85f * u, 0.46f * u, GOLD, Paint.Align.CENTER)
        val top = card.top + 1.3f * u
        for (i in 0 until slots) {
            val col = i % cols
            val row = i / cols
            val r = RectF(card.left + pad + col * (cardW + gap), top + row * (cardH + gap), 0f, 0f)
            r.right = r.left + cardW
            r.bottom = r.top + cardH
            fill.color = withAlpha(BLACK, 90)
            c.drawRoundRect(r, 0.25f * u, 0.25f * u, fill)
            val a = Avatars.all.getOrNull(i)
            if (a == null) {
                // More (paid) avatars will appear here.
                stroke.color = withAlpha(WHITE, 40)
                stroke.strokeWidth = 0.03f * u
                c.drawRoundRect(r, 0.25f * u, 0.25f * u, stroke)
                wrap(s(R.string.avatar_more), 0.27f * u, cardW - 0.4f * u).forEachIndexed { k, line ->
                    label(c, line, r.centerX(), r.centerY() + k * 0.38f * u, 0.27f * u, Pal.TEXT_DIM, Paint.Align.CENTER)
                }
                continue
            }
            // Soft light behind the figure, in the colour of its crystal.
            fill.shader = RadialGradient(r.centerX(), r.top + cardH * 0.45f, cardW * 0.6f, withAlpha(a.glow, 70), withAlpha(a.glow, 0), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 0.25f * u, 0.25f * u, fill)
            fill.shader = null
            val img = art.avatarIdle(a)
            val ih = cardH - 0.75f * u
            val src = art.opaqueBounds(a.idleRes)
            val iw = ih * src.width() / src.height()
            rect.set(r.centerX() - iw / 2f, r.top + 0.1f * u, r.centerX() + iw / 2f, r.top + 0.1f * u + ih)
            // One you haven't unlocked is dimmed, with its price in the corner (a tap opens the unlock popup).
            val owned = owns(a)
            bitmapPaint.alpha = if (owned) 255 else 150
            c.drawBitmap(img, src, rect, bitmapPaint)
            bitmapPaint.alpha = 255
            label(c, s(a.nameRes), r.centerX(), r.bottom - 0.3f * u, 0.3f * u, Pal.TEXT, Paint.Align.CENTER)
            when {
                !a.paid -> label(c, s(R.string.avatar_free), r.right - 0.15f * u, r.top + 0.35f * u, 0.22f * u, Pal.TEXT_DIM, Paint.Align.RIGHT)
                !owned -> drawAmount(c, "${a.price}", r.right - 0.15f * u, r.top + 0.42f * u, 0.3f * u, GOLD, Paint.Align.RIGHT)
            }
            val chosen = a.id == avatar.id
            stroke.color = if (chosen) GOLD else withAlpha(WHITE, 40)
            stroke.strokeWidth = (if (chosen) 0.07f else 0.03f) * u
            c.drawRoundRect(r, 0.25f * u, 0.25f * u, stroke)
            dialogButtons["avatar_${a.id}"] = r
        }
        val by = top + rows * cardH + (rows - 1) * gap + 0.35f * u
        val close = RectF(cx - 1.4f * u, by, cx + 1.4f * u, by + 0.8f * u)
        drawButton(c, close, s(R.string.close), null, primary = false, small = true)
        dialogButtons["close"] = close
    }

    /**
     * The leaderboard: rank, name and highest level of the top 10. Your row is highlighted in gold,
     * and if you're further down it follows after a gap, between your neighbours. While loading, or
     * when the server can't be reached, a short message shows instead (with Retry after an error).
     */
    private fun drawLeaderboard(c: Canvas) {
        val lb = leaderboard
        val rowH = 0.62f * u
        val ready = lb.state == Leaderboard.State.READY
        val rows = if (ready) lb.top.size + (if (lb.mine.isNotEmpty()) lb.mine.size + 1 else 0) else 0
        val bodyH = if (ready && rows > 0) 0.55f * u + rows * rowH else 1.6f * u
        val w = min(width - 1.2f * u, 7.6f * u)
        val h = 1.3f * u + bodyH + 0.35f * u + 0.8f * u + 0.35f * u
        val cx = width / 2f
        val cy = height / 2f
        dialogCard.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        val card = dialogCard
        fill.color = Pal.CARD
        c.drawRoundRect(card, 0.35f * u, 0.35f * u, fill)
        stroke.color = GOLD
        stroke.strokeWidth = 0.05f * u
        c.drawRoundRect(card, 0.35f * u, 0.35f * u, stroke)
        label(c, s(R.string.leaderboard), cx, card.top + 0.85f * u, 0.46f * u, GOLD, Paint.Align.CENTER)
        val x0 = card.left + 0.35f * u
        val x1 = card.right - 0.35f * u
        var y = card.top + 1.3f * u
        val message = when {
            lb.state == Leaderboard.State.UNAVAILABLE -> s(R.string.lb_unavailable)
            lb.state == Leaderboard.State.ERROR -> s(R.string.lb_error)
            !ready -> s(R.string.lb_loading)
            rows == 0 -> s(R.string.lb_empty)
            else -> null
        }
        if (message != null) {
            wrap(message, 0.3f * u, w - 0.8f * u).forEachIndexed { i, line ->
                label(c, line, cx, y + 0.6f * u + i * 0.42f * u, 0.3f * u, Pal.TEXT_DIM, Paint.Align.CENTER)
            }
        } else {
            // Column headings: rank, player, level.
            val rankX = x0 + 0.75f * u
            val nameX = x0 + 1.0f * u
            label(c, "#", rankX, y + 0.3f * u, 0.24f * u, Pal.TEXT_DIM, Paint.Align.RIGHT)
            label(c, s(R.string.lb_player), nameX, y + 0.3f * u, 0.24f * u, Pal.TEXT_DIM, Paint.Align.LEFT)
            label(c, s(R.string.lb_level), x1 - 0.1f * u, y + 0.3f * u, 0.24f * u, Pal.TEXT_DIM, Paint.Align.RIGHT)
            y += 0.55f * u
            fun row(e: com.an0obis.magicduel.net.ScoreEntry) {
                val me = e.playerId == lb.playerId
                if (me) {
                    rect.set(x0, y, x1, y + rowH - 0.06f * u)
                    fill.color = withAlpha(GOLD, 40)
                    c.drawRoundRect(rect, 0.15f * u, 0.15f * u, fill)
                }
                val col = if (me) GOLD else Pal.TEXT
                val base = y + rowH * 0.62f
                label(c, "${e.rank}", rankX, base, 0.3f * u, col, Paint.Align.RIGHT)
                text.textSize = 0.3f * u
                var name = e.name
                val room = x1 - 1.3f * u - nameX
                while (name.length > 1 && text.measureText(name) > room) name = name.dropLast(1)
                if (name != e.name) name = name.dropLast(1) + "…"
                label(c, name, nameX, base, 0.3f * u, col, Paint.Align.LEFT)
                label(c, "${e.score}", x1 - 0.1f * u, base, 0.3f * u, col, Paint.Align.RIGHT)
                y += rowH
            }
            lb.top.forEach(::row)
            if (lb.mine.isNotEmpty()) {
                label(c, "⋯", cx, y + rowH * 0.6f, 0.3f * u, Pal.TEXT_DIM, Paint.Align.CENTER)
                y += rowH
                lb.mine.forEach(::row)
            }
        }
        val by = card.bottom - 0.35f * u - 0.8f * u
        if (lb.state == Leaderboard.State.ERROR) {
            val half = (w - 0.9f * u) / 2f
            val retry = RectF(card.left + 0.3f * u, by, card.left + 0.3f * u + half, by + 0.8f * u)
            val close = RectF(card.right - 0.3f * u - half, by, card.right - 0.3f * u, by + 0.8f * u)
            drawButton(c, retry, s(R.string.lb_retry), null, primary = true, small = true)
            drawButton(c, close, s(R.string.close), null, primary = false, small = true)
            dialogButtons["lb_retry"] = retry
            dialogButtons["close"] = close
        } else {
            val close = RectF(cx - 1.4f * u, by, cx + 1.4f * u, by + 0.8f * u)
            drawButton(c, close, s(R.string.close), null, primary = false, small = true)
            dialogButtons["close"] = close
        }
    }

    private fun drawDialog(c: Canvas, d: Dialog) {
        dialogButtons.clear()
        fill.color = withAlpha(BLACK, 170)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        if (d == Dialog.OPTIONS) {
            drawOptions(c)
            return
        }
        if (d == Dialog.AVATAR) {
            drawAvatarPicker(c)
            return
        }
        if (d == Dialog.LEADERBOARD) {
            drawLeaderboard(c)
            return
        }
        when (d) {
            Dialog.DAILY -> return drawDaily(c)
            Dialog.REVIVE -> return drawRevive(c)
            Dialog.SHOP -> return drawShop(c)
            Dialog.UNLOCK -> return drawUnlock(c)
            else -> Unit
        }
        val (title, message) = when (d) {
            Dialog.RESTART -> s(R.string.restart_q) to s(R.string.restart_msg)
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

    /** Where your avatar's frame is drawn, standing on [playerFoot] centred on [x]. */
    private fun avatarFrame(out: RectF, x: Float): RectF {
        val img = art.avatarIdle(avatar)
        val h = playerH * AVATAR_SCALE * avatar.scale
        val w = h * img.width / img.height
        out.set(x - w / 2f, playerFoot - h, x + w / 2f, playerFoot)
        return out
    }

    /**
     * Your avatar (Midjourney sprite). While you cast, one of its three cast animations (48 frames)
     * plays, always as long as your quickest cast (into lane 5) and ending with the throw as the shot
     * leaves; in a longer cast the figure stands still until the clip starts. Neighbouring frames are
     * blended so the motion stays smooth. Behind it,
     * a soft light in its crystal's colour (the spell's colour while casting) keeps the dark figure
     * readable; the crystal glows with the spell. Hits flash it white and shake it; on defeat it
     * plays its collapse animation and stays in the defeated pose.
     */
    private fun drawPlayerAvatar(c: Canvas) {
        val lost = battle.winner == Side.ENEMY || battle.playerDown
        // Going down, the avatar plays its collapse (over DEFEAT_ANIM_TIME, then holds the last, defeated
        // pose); if that sheet isn't decoded yet it topples backwards and fades instead.
        val defeat = if (lost) art.sheetIfReady(avatar.defeatRes) else null
        val fade = if (lost) smooth01(downTimer / 0.8f) else 0f
        val k = if (defeat == null) fade else 0f
        val p = battle.player
        val castColor = castColor(p) ?: if (afterglow[0] > 0f) lastCastColor[0] else null
        val cast = if (p.isCasting) p.castProgress else afterglow[0]
        val x = playerX + sin(clock * 55f) * shake[0] * 0.08f * u
        avatarFrame(playerAvatarRect, playerX).inset(playerAvatarRect.width() * 0.2f, 0f)
        val frame = avatarFrame(RectF(), x)
        val h = frame.height()
        c.save()
        if (k > 0f) c.rotate(-DEFEAT_TILT * k, frame.centerX(), frame.bottom)
        val light = castColor ?: avatar.glow
        fill.shader = RadialGradient(
            frame.centerX(), frame.top + h * 0.45f, h * 0.55f,
            withAlpha(light, ((50 + 60 * cast) * (1f - 0.6f * fade)).toInt()), withAlpha(light, 0), Shader.TileMode.CLAMP,
        )
        c.drawCircle(frame.centerX(), frame.top + h * 0.45f, h * 0.55f, fill)
        fill.shader = null
        avatarPaint.alpha = (255 * (1f - 0.6f * k)).toInt()
        avatarPaint.colorFilter = if (flash[0] > 0.01f) PorterDuffColorFilter(withAlpha(WHITE, (170 * flash[0]).toInt()), PorterDuff.Mode.SRC_ATOP) else null
        // The cast clip always lasts as long as your quickest cast and ends with the throw as the shot
        // leaves; in a longer cast the avatar stands still until the clip starts.
        val anim = if (p.isCasting && !lost) art.avatarCast(avatar, castAnim) else null
        var clipT = 0f
        if (defeat != null) {
            drawClip(c, defeat, downTimer / DEFEAT_ANIM_TIME, frame, avatarPaint)
            lastCastAnim = null
        } else if (anim != null) {
            clipT = castClipT(p, p.shortestCastTime)
            lastCastFrame = drawClip(c, anim, clipT, frame, avatarPaint)
            lastCastAnim = anim
            castEndedAt = -1f
        } else {
            c.drawBitmap(art.avatarIdle(avatar), null, frame, avatarPaint)
            // The clip ends on the throw, not in the idle pose: after the cast (or a cancel) the last
            // pose fades into the idle one over AVATAR_SETTLE_TIME.
            val last = lastCastAnim
            if (last != null) {
                if (castEndedAt < 0f) castEndedAt = clock
                val r = (clock - castEndedAt) / AVATAR_SETTLE_TIME
                if (r >= 1f || battle.winner != null) {
                    lastCastAnim = null
                } else {
                    val base = avatarPaint.alpha
                    avatarPaint.alpha = (base * (1f - smooth01(r))).toInt()
                    last.draw(c, lastCastFrame, frame, avatarPaint)
                    avatarPaint.alpha = base
                }
            }
        }
        avatarPaint.colorFilter = null
        // The crystal glow flashes up as the cast begins and fades as the cast motion gets going
        // (the staff moves away from where the glow is); it lights up again as the shot leaves.
        val tipGlow = 1f - smooth01(clipT / 0.12f)
        if (castColor != null && cast > 0f && tipGlow > 0f) {
            val tx = frame.left + avatar.tipX * frame.width()
            val ty = frame.top + avatar.tipY * h
            val r = (0.25f + 0.35f * cast) * u
            fill.shader = RadialGradient(tx, ty, r, withAlpha(castColor, (200 * cast * tipGlow * (1f - fade)).toInt()), withAlpha(castColor, 0), Shader.TileMode.CLAMP)
            c.drawCircle(tx, ty, r, fill)
            fill.shader = null
        }
        c.restore()
    }

    private fun drawSpellGrid(c: Canvas) {
        val spells = battle.spells
        val caster = battle.player
        val locked = caster.isCasting || !battle.started || battle.winner != null || battle.playerDown
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

    /**
     * The level's title card as every duel starts (a new level, a retry, a revive): the opponent's
     * figure on a soft light, "Level X" and its name. It pops in, stays through the intro (nobody casts
     * yet) and fades as the duel begins.
     */
    private fun drawBanner(c: Canvas) {
        val t = battle.time
        val intro = battle.introTime
        if (t > intro + BANNER_FADE) return
        val a = if (t < intro) smooth01(t / BANNER_POP) else 1f - (t - intro) / BANNER_FADE
        val pop = 0.9f + 0.1f * easeOut(min(1f, t / BANNER_POP))
        val cx = fieldLeft + 2.5f * fu
        val cy = fieldTop + 2.5f * fu
        val imgH = 2.5f * u
        val h = 0.3f * u + imgH + 1.6f * u
        val top = cy - h / 2f
        c.save()
        c.scale(pop, pop, cx, cy)
        rect.set(left + 0.4f * u, top, right - 0.4f * u, top + h)
        fill.color = withAlpha(0xFF0B0820.toInt(), (225 * a).toInt())
        c.drawRoundRect(rect, 0.3f * u, 0.3f * u, fill)
        stroke.color = withAlpha(GOLD, (170 * a).toInt())
        stroke.strokeWidth = 0.04f * u
        c.drawRoundRect(rect, 0.3f * u, 0.3f * u, stroke)
        val foot = top + 0.3f * u + imgH
        val o = battle.opponent
        val sprite = enemyAvatar
        val glow = when {
            o.kind == AvatarKind.DUMMY -> BANNER_DUMMY_GLOW
            sprite != null -> sprite.glow
            else -> mix(o.robeColor, WHITE, 0.3f)
        }
        val gy = foot - imgH * 0.45f
        fill.shader = RadialGradient(cx, gy, imgH * 0.65f, withAlpha(glow, (80 * a).toInt()), withAlpha(glow, 0), Shader.TileMode.CLAMP)
        c.drawCircle(cx, gy, imgH * 0.65f, fill)
        fill.shader = null
        when {
            o.kind == AvatarKind.DUMMY -> art.drawDummySprite(c, cx, foot, imgH * 0.95f, -1, 0f, 0f, a)
            sprite != null -> {
                // Trimmed to the figure (bot frames keep room for their magic).
                val src = art.opaqueBounds(sprite.idleRes)
                val w = imgH * src.width() / src.height()
                bitmapPaint.alpha = (255 * a).toInt()
                c.drawBitmap(art.image(sprite.idleRes), src, RectF(cx - w / 2f, foot - imgH, cx + w / 2f, foot), bitmapPaint)
                bitmapPaint.alpha = 255
            }
            else -> art.drawMage(c, cx, foot, imgH * 0.9f, false, o.robeColor, true, null, 0f, clock, 0f, 0f, a)
        }
        label(c, s(R.string.level_fmt, level + 1), cx, foot + 0.8f * u, 0.7f * u, GOLD, Paint.Align.CENTER, a)
        label(c, opponentName(o), cx, foot + 1.32f * u, 0.38f * u, Pal.TEXT, Paint.Align.CENTER, a)
        c.restore()
    }

    /**
     * When the end card starts fading in. Only a win ends a duel now (going down always leads to the
     * revive), so it's the same for every end card.
     */
    private fun endCardDelay(): Float = END_CARD_DELAY

    /**
     * The defeated pose shown on the end card: the beaten opponent's when you win (the dummies share
     * one), your avatar's when you lose; null for a mage without an avatar yet.
     */
    private fun endCardImage(won: Boolean): Int? = when {
        !won -> avatar.defeatedRes
        battle.opponent.kind == AvatarKind.DUMMY -> R.drawable.dummy_defeated
        else -> enemyAvatar?.defeatedRes
    }

    /**
     * The end card: Victory or Defeated, who's next (or who won), and above them the loser's defeated
     * pose, trimmed to the figure and fitted into a band END_IMAGE_H tall.
     */
    private fun drawEndOverlay(c: Canvas) {
        val winner = battle.winner ?: return
        val k = smooth01((endTimer - endCardDelay()) / 0.5f)
        if (k <= 0f) return
        fill.color = withAlpha(BLACK, (150 * k).toInt())
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val won = winner == Side.PLAYER
        val image = endCardImage(won)
        val cx = width / 2f
        val cy = fieldTop + 2.5f * fu
        val imageBottom = cy - 1.2f * u
        val top = if (image != null) imageBottom - (END_IMAGE_H + 0.25f) * u else cy - 1.6f * u
        rect.set(left + 0.6f * u, top, right - 0.6f * u, cy + 1.6f * u)
        fill.color = withAlpha(Pal.CARD, (240 * k).toInt())
        c.drawRoundRect(rect, 0.35f * u, 0.35f * u, fill)
        stroke.color = withAlpha(GOLD, (200 * k).toInt())
        stroke.strokeWidth = 0.05f * u
        c.drawRoundRect(rect, 0.35f * u, 0.35f * u, stroke)
        if (image != null) {
            val src = art.opaqueBounds(image)
            val maxW = rect.width() - 1f * u
            var h = END_IMAGE_H * u
            var w = h * src.width() / src.height()
            if (w > maxW) { h *= maxW / w; w = maxW }
            val dst = RectF(cx - w / 2f, imageBottom - h, cx + w / 2f, imageBottom)
            avatarPaint.alpha = (255 * k).toInt()
            avatarPaint.colorFilter = null
            c.drawBitmap(art.image(image), src, dst, avatarPaint)
            avatarPaint.alpha = 255
        }
        label(c, if (won) s(R.string.victory) else s(R.string.defeated), cx, cy - 0.45f * u, 0.8f * u, if (won) GOLD else Pal.ENEMY_HP, Paint.Align.CENTER, k)
        val subtitle = when {
            won && tutorial is Tutorial -> s(R.string.tutorial_complete_next, opponentName(Opponents.forLevel(level + 1)))
            won -> s(R.string.next_fmt, opponentName(Opponents.forLevel(level + 1)))
            else -> s(R.string.wins_fmt, opponentName(battle.opponent))
        }
        label(c, subtitle, cx, cy + 0.35f * u, 0.36f * u, Pal.TEXT, Paint.Align.CENTER, k)
        if (endTimer > endCardDelay() + TAP_DELAY) {
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
        const val KEY_AVATAR = "avatar"
        const val KEY_NAME_ASKED = "nameAsked"
        const val KEY_ESSENCE = "essence"
        const val KEY_LAST_CLAIM_DAY = "lastClaimDay"
        const val KEY_STREAK_DAY = "streakDay"
        const val KEY_OWNED_AVATARS = "ownedAvatars"
        const val KEY_OWNED_PACKS = "ownedPacks"
        const val KEY_PACK = "elementPack"

        /** Essence: the crystal's glow, its lit and its shaded side. */
        val ESSENCE = 0xFF6FE8FF.toInt()
        val ESSENCE_LIGHT = 0xFFA6F5FF.toInt()
        val ESSENCE_DARK = 0xFF5B5BE6.toInt()

        /** The essence packs in the shop. */
        val SHOP_PACKS = intArrayOf(10, 30, 100)

        /** The counter's crystal flares for this long when essence is added. */
        const val ESSENCE_PULSE_TIME = 1.2f

        /** After your collapse has played, the revive popup opens this much later. */
        const val REVIVE_OFFER_DELAY = 0.3f

        /** The revive plays in its popup over this long, then holds the standing pose a moment. */
        const val REVIVE_ANIM_TIME = 3f
        const val REVIVE_HOLD = 0.45f

        /** The level's title card pops in over this long and fades out over this long after the intro. */
        const val BANNER_POP = 0.25f
        const val BANNER_FADE = 0.5f

        /** The light behind the training dummies on the title card. */
        val BANNER_DUMMY_GLOW = 0xFFE0B46A.toInt()

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

        /** Room kept for the × on a tutorial hint, in layout units. */
        const val HINT_CLOSE = 0.45f

        /** A wall on the field, as a share of the field cell (globes are 0.9). */
        const val WALL_TILE = 0.94f

        /** A new wall grows in over this long; each wall above the lowest one starts a little later. */
        const val WALL_RISE_TIME = 0.32f
        const val WALL_RISE_STAGGER = 0.07f

        /**
         * A broken wall's shatter clip plays over this long, growing to (1 + this) times the wall's size
         * as its shards fly out past the cell.
         */
        const val WALL_BREAK_TIME = 0.7f
        const val WALL_BREAK_GROW = 0.6f

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

        /** After the end card starts fading in, how long until a tap continues. */
        const val TAP_DELAY = 0.8f

        /** When the end card starts fading in after a win (after a loss it waits for your avatar's collapse). */
        const val END_CARD_DELAY = 0.7f

        /** How long your avatar's defeat animation plays before it holds the defeated pose. */
        const val DEFEAT_ANIM_TIME = 2f

        /** How long a beaten bot takes to sink into its defeated pose. */
        const val ENEMY_DEFEAT_FADE = 0.6f

        /** A bot sprite's height, as a multiple of [enemyH]. */
        const val ENEMY_AVATAR_SCALE = 1.05f

        /** The longest a bot's cast animation plays, in seconds (its slow casts start with it standing still). */
        const val BOT_CAST_ANIM_MAX = 4f

        /** Height of the defeated pose on the end card, in layout units. */
        const val END_IMAGE_H = 2.1f

        const val DEFEAT_TILT = 32f

        /** Your avatar's frame height, as a multiple of [playerH] (the frame has room for the cast's magic). */
        const val AVATAR_SCALE = 1.2f

        /** After a cast, your avatar settles from its last pose (the throw) back into its idle pose over this long. */
        const val AVATAR_SETTLE_TIME = 0.4f

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
