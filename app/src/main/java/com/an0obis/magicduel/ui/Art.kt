package com.an0obis.magicduel.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.an0obis.magicduel.R
import com.an0obis.magicduel.game.Element
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * All drawing of game pieces: element symbols, crystal globes, projectiles and avatars.
 * Element symbols, globes, shots, buttons and their animations are Midjourney images in
 * res/drawable-nodpi (prompts in the project's art/ folder); the mage is vector, authored in a
 * 100-unit box and scaled, so it stays crisp at any size.
 */
class Art(private val res: Resources, private val packageName: String) {
    companion object {
        const val ANIM_COLS = 6
        const val ANIM_ROWS = 4

        /** Most animation variants per element and kind (sheets _anim, _anim2 … _anim4). */
        const val MAX_VARIANTS = 4

        /** A resource prefix that names no art: a pack's globe has no freeze clip. */
        private const val NO_ART = "noart_"

        // Freeze sheets (shot → globe), see [freezeFrames].
        const val FREEZE_COLS = 4
        const val FREEZE_ROWS = 4
        const val FREEZE_ASPECT = 1.8f
        const val FREEZE_GLOBE = 0.42f
        const val DUMMY_HIT_COLS = 4
        const val DUMMY_HIT_FRAMES = 16

        /** Glyph ring styles around a spell being cast, see [drawCastRing]. */
        const val CAST_RING_STYLES = 3
        private val RING_IDS = intArrayOf(R.drawable.ring_glyphs, R.drawable.ring_chain, R.drawable.ring_seal)
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun color(e: Element): Int = when (e) {
        Element.FIRE -> 0xFFFF6A3D
        Element.WATER -> 0xFF3E9BFF
        Element.EARTH -> 0xFF62C048
        Element.AIR -> 0xFFCFE6F5
        Element.LIGHTNING -> 0xFFFFD23F
        Element.SHADOW -> 0xFFA35CFF
    }.toInt()

    // ---------------------------------------------------------------- element symbols

    /**
     * Draws the Midjourney symbol of [e] (sym_*, a glowing emblem with no tile, see
     * art/symbols/PROMPTS.md) centred on (cx, cy), [size] pixels across, with [paint] (its alpha
     * fades the symbol).
     */
    fun drawSymbol(c: Canvas, e: Element, cx: Float, cy: Float, size: Float, paint: Paint) {
        val h = size / 2f
        symbolDst.set(cx - h, cy - h, cx + h, cy + h)
        c.drawBitmap(source(Kind.SYMBOL, e), null, symbolDst, paint)
    }

    private val symbolDst = RectF()

    // ---------------------------------------------------------------- cast rings

    /**
     * Draws the glyph ring around a spell being cast (ring_*, white Midjourney art tinted with
     * [color], see art/rings/PROMPTS.md), centred on (cx, cy) and [size] pixels across. The whole
     * ring shows dimly; a bright part sweeps clockwise from the top as [progress] goes 0..1, with a
     * spark at its leading edge. [style] (0 until [CAST_RING_STYLES]) picks the ring and how it moves:
     * 0 glyphs: stands still and breathes; 1 chain: the runes stream round through the lit part;
     * 2 seal: turns and closes in as it fills. [time] is any running clock in seconds.
     */
    fun drawCastRing(c: Canvas, style: Int, cx: Float, cy: Float, size: Float, color: Int, progress: Float, time: Float) {
        val s = Math.floorMod(style, CAST_RING_STYLES)
        val bmp = rings[s] ?: decode(RING_IDS[s]).also { rings[s] = it }
        val tint = mix(color, WHITE, 0.3f)
        if (tint != ringTint) {
            ringTint = tint
            ringPaint.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.MULTIPLY)
        }
        val p = progress.coerceIn(0f, 1f)
        val rot: Float
        val scale: Float
        val dim: Int
        val lit: Int
        when (s) {
            0 -> { rot = 0f; scale = 1f; dim = 100; lit = (215 + 40 * sin(time * 7f)).toInt() }
            1 -> { rot = -time * 45f; scale = 1f; dim = 95; lit = 255 }
            else -> { rot = time * 25f; scale = 1.2f - 0.2f * easeOut(p); dim = (75 + 90 * p).toInt(); lit = 255 }
        }
        val half = size * scale / 2f
        ringDst.set(cx - half, cy - half, cx + half, cy + half)
        c.save()
        c.rotate(rot, cx, cy)
        ringPaint.alpha = dim
        c.drawBitmap(bmp, null, ringDst, ringPaint)
        c.restore()
        if (p <= 0f) return
        // The lit part: the ring again, clipped to a pie slice from the top.
        val reach = half * 1.2f
        ringOval.set(cx - reach, cy - reach, cx + reach, cy + reach)
        ringClip.reset()
        ringClip.moveTo(cx, cy)
        ringClip.arcTo(ringOval, -90f, 360f * p, false)
        ringClip.close()
        c.save()
        c.clipPath(ringClip)
        c.rotate(rot, cx, cy)
        ringPaint.alpha = lit
        c.drawBitmap(bmp, null, ringDst, ringPaint)
        c.restore()
        // A spark where the light is spreading.
        if (p < 1f) {
            val a = (-90f + 360f * p) * (PI / 180f).toFloat()
            val r = half * 0.9f
            val sx = cx + r * cos(a)
            val sy = cy + r * sin(a)
            fill.color = withAlpha(color, 110)
            c.drawCircle(sx, sy, size * 0.07f, fill)
            fill.color = withAlpha(tint, 230)
            c.drawCircle(sx, sy, size * 0.035f, fill)
            fill.color = WHITE
            c.drawCircle(sx, sy, size * 0.016f, fill)
        }
    }

    private val rings = arrayOfNulls<Bitmap>(CAST_RING_STYLES)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var ringTint = 0
    private val ringDst = RectF()
    private val ringOval = RectF()
    private val ringClip = Path()

    // ---------------------------------------------------------------- Midjourney sprites
    // res/drawable-nodpi: btn_* (spell buttons), sym_* (element symbols); globes and shots are animation
    // sheets only (below). Sources and prompts are in the project's art/ folder.

    private enum class Kind { GLOBE, PROJECTILE, BUTTON, SYMBOL, FREEZE, HIT }

    private val sources = HashMap<Pair<Kind, Element>, Bitmap>()
    private val scaled = HashMap<Triple<Kind, Element, Int>, Bitmap>()

    private fun source(kind: Kind, e: Element): Bitmap = sources.getOrPut(kind to e) {
        val id = when (kind) {
            Kind.GLOBE, Kind.PROJECTILE, Kind.FREEZE, Kind.HIT -> error("$kind is an animation, not a still image")
            Kind.BUTTON -> when (e) {
                Element.FIRE -> R.drawable.btn_fire
                Element.WATER -> R.drawable.btn_water
                Element.EARTH -> R.drawable.btn_earth
                Element.AIR -> R.drawable.btn_air
                Element.LIGHTNING -> R.drawable.btn_lightning
                Element.SHADOW -> R.drawable.btn_shadow
            }
            Kind.SYMBOL -> when (e) {
                Element.FIRE -> R.drawable.sym_fire
                Element.WATER -> R.drawable.sym_water
                Element.EARTH -> R.drawable.sym_earth
                Element.AIR -> R.drawable.sym_air
                Element.LIGHTNING -> R.drawable.sym_lightning
                Element.SHADOW -> R.drawable.sym_shadow
            }
        }
        BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })
    }

    /**
     * Draws a crystal globe with the element sealed inside into [dst] (square), with [paint] (its
     * alpha fades the whole globe): a soft aura in the element's colour and the globe at 88% of the
     * size. [look] picks one of the element's animation variants and [frame] the animation frame
     * (any number; it wraps around). [mine]: your globe (from your shot), which wears your element
     * pack; everything else (the bot's, the duel's starting globes) keeps the classic look. A pack's
     * globe (dark, smaller than a glass orb, with spikes) gets a fainter aura that hugs its body: at full
     * strength a pale one (air) showed as a whitish disc.
     */
    fun drawGlobe(c: Canvas, e: Element, dst: RectF, frame: Int, look: Int, paint: Paint, mine: Boolean = true) {
        val prefix = prefixFor(Kind.GLOBE, e, mine)
        if (prefix.isEmpty()) {
            c.drawBitmap(aura(e), null, dst, paint)
        } else {
            val base = paint.alpha
            val a = dst.width() * 0.12f
            inner.set(dst.left + a, dst.top + a, dst.right - a, dst.bottom - a)
            paint.alpha = (base * (if (e == Element.AIR) 0.3f else 0.55f)).toInt()
            c.drawBitmap(aura(e), null, inner, paint)
            paint.alpha = base
        }
        val inset = dst.width() * 0.06f
        inner.set(dst.left + inset, dst.top + inset, dst.right - inset, dst.bottom - inset)
        sheet(Kind.GLOBE, e, look, prefix).draw(c, frame, inner, paint)
    }

    /**
     * The flying projectile for [e], head on the right; [look] picks an animation variant. [mine]: your
     * shot, in your element pack's look (the bot's shots keep the classic one).
     */
    fun projectile(e: Element, look: Int, mine: Boolean = true): Anim =
        sheet(Kind.PROJECTILE, e, look, prefixFor(Kind.PROJECTILE, e, mine))

    /** Every element has globe and shot sheets (orb_<element>_anim…, proj_<element>_anim…). */
    private fun sheet(kind: Kind, e: Element, look: Int, prefix: String): Anim =
        checkNotNull(anim(kind, e, variant(kind, e, look, prefix), prefix)) { "no $kind animation sheet for $e" }

    /**
     * The landed shot freezing into a globe: [FREEZE_COLS] × [FREEZE_ROWS] frames, [FREEZE_ASPECT]
     * times as wide as tall. The comet's head (and the globe it becomes) is centred half a frame
     * height in from the right edge, the globe [FREEZE_GLOBE] of the height across. Null if the
     * element has no freeze sheet, or for your shot when your element pack has its own globe ([mine]).
     */
    fun freeze(e: Element, mine: Boolean = true): Anim? = anim(Kind.FREEZE, e, 0, prefixFor(Kind.FREEZE, e, mine))

    /**
     * The burst where magic of [e] hits a duellist: 16 square frames (4×4 sheet `hit_<element>`),
     * the burst at its peak in the first frame and gone in the last. Null if the element has no
     * hit sheet.
     */
    fun hit(e: Element): Anim? = anim(Kind.HIT, e, 0)

    /**
     * The frames of one animation: regions of one decoded sheet, drawn scaled straight from it
     * (nothing is copied per frame or per size).
     */
    class Anim(val sheet: Bitmap, cols: Int, rows: Int) {
        val count = cols * rows
        private val fw = sheet.width / cols
        private val fh = sheet.height / rows

        /** Width of a frame divided by its height. */
        val aspect = fw.toFloat() / fh
        private val rects = Array(count) { k -> Rect((k % cols) * fw, (k / cols) * fh, (k % cols + 1) * fw, (k / cols + 1) * fh) }

        /** Draws frame [k] (any number; it wraps around) into [dst]. */
        fun draw(c: Canvas, k: Int, dst: RectF, paint: Paint) = c.drawBitmap(sheet, rects[Math.floorMod(k, count)], dst, paint)
    }

    // ---------------------------------------------------------------- Midjourney animations
    // Optional sheets, see art/ANIMATIONS.md:
    // - orb_<element>_anim, _anim2 … and proj_<element>_anim, _anim2 …: variants of a Midjourney
    //   "Loop" video, 24 frames in a 6×4 grid. Each globe and shot shows one variant, picked by its
    //   look number.
    // - freeze_<element> and hit_<element>: 16 frames in a 4×4 grid.
    // Each sheet is decoded once, only when an element in play needs it, and kept whole; frames are
    // drawn from it. With [hardwareBitmaps] sheets live only in GPU memory.

    /** Decode sheets as hardware bitmaps (GPU memory only); needs a hardware-accelerated canvas. */
    @Volatile
    var hardwareBitmaps = false

    private val variantCounts = ConcurrentHashMap<String, Int>()
    private val anims = ConcurrentHashMap<String, Any>()
    private val noSheet = Any()
    private val auras = HashMap<Element, Bitmap>()
    private val inner = RectF()

    /**
     * The element pack whose globes, shots and walls are drawn (art/packs/PROMPTS.md). An element the
     * pack has no art for keeps the classic look. A pack's shots don't play the classic freeze clip
     * (it ends on the classic globe): the frost ring shows instead. Changing the pack frees the old
     * pack's decoded sheets.
     */
    @Volatile
    var pack: ElementPack = ElementPacks.CLASSIC
        set(value) {
            if (field.id == value.id) return
            field = value
            prefixes.clear()
            anims.clear()
            variantCounts.clear()
            wallBlocks.clear()
            wallBlockSources.clear()
            wallIds = null
        }

    private val prefixes = ConcurrentHashMap<String, String>()

    @Suppress("DiscouragedApi")
    private fun sheetId(kind: Kind, e: Element, variant: Int, prefix: String): Int {
        val el = e.name.lowercase()
        val name = when (kind) {
            Kind.GLOBE -> "orb_${el}_anim"
            Kind.PROJECTILE -> "proj_${el}_anim"
            Kind.HIT -> "hit_$el"
            else -> "freeze_$el"
        } + if (variant == 0 || kind == Kind.HIT || kind == Kind.FREEZE) "" else "${variant + 1}"
        return res.getIdentifier(prefix + name, "drawable", packageName)
    }

    /**
     * The resource prefix [kind] art of [e] comes from: the pack's for its globes and shots (if it has
     * them for [e]), the classic "" otherwise; [NO_ART] for the freeze clip of a pack's globe.
     */
    private fun prefixFor(kind: Kind, e: Element, mine: Boolean = true): String = if (!mine) "" else prefixes.getOrPut("$kind/$e") {
        val p = pack.prefix
        val packGlobe = p.isNotEmpty() && sheetId(Kind.GLOBE, e, 0, p) != 0
        when (kind) {
            Kind.GLOBE -> if (packGlobe) p else ""
            Kind.PROJECTILE -> if (p.isNotEmpty() && sheetId(kind, e, 0, p) != 0) p else ""
            Kind.FREEZE -> if (packGlobe) NO_ART else ""
            else -> ""
        }
    }

    /** Which animation variant to show for [look]: how many there are is found from the resources. */
    private fun variant(kind: Kind, e: Element, look: Int, prefix: String = prefixFor(kind, e)): Int {
        val n = variantCounts.getOrPut("$prefix$kind/$e") {
            var k = 0
            while (k < MAX_VARIANTS && sheetId(kind, e, k, prefix) != 0) k++
            k
        }
        return if (n <= 1) 0 else Math.floorMod(look / 7, n)
    }

    /**
     * One animation's sheet, decoded the first time it is needed (once, even if two threads ask at
     * the same moment), or null if there is no such sheet. [prefix]: the pack it comes from.
     */
    private fun anim(kind: Kind, e: Element, variant: Int, prefix: String = prefixFor(kind, e)): Anim? =
        anims.computeIfAbsent("$prefix$kind/$e/$variant") { loadAnim(kind, e, variant, prefix) ?: noSheet } as? Anim

    private fun loadAnim(kind: Kind, e: Element, variant: Int, prefix: String): Anim? {
        val id = sheetId(kind, e, variant, prefix)
        if (id == 0) return null
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            if (hardwareBitmaps) inPreferredConfig = Bitmap.Config.HARDWARE
        }
        val sheet = BitmapFactory.decodeResource(res, id, opts)
            ?: BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })
            ?: return null
        val small = kind == Kind.FREEZE || kind == Kind.HIT
        return Anim(sheet, if (small) FREEZE_COLS else ANIM_COLS, if (small) FREEZE_ROWS else ANIM_ROWS)
    }

    /**
     * Decodes the globe, shot, freeze and hit sheets of [elements] (the ones in play) on a
     * background thread, so the first appearance of a variant doesn't stall a frame. Anything not
     * ready yet is decoded when it is first drawn; elements not in play are never loaded.
     */
    fun preload(elements: List<Element>) {
        Thread {
            for (e in elements) {
                // The classic look (the bot's and the starting globes) and, with a pack, yours.
                for (prefix in setOf("", prefixFor(Kind.GLOBE, e))) for (v in 0 until MAX_VARIANTS) anim(Kind.GLOBE, e, v, prefix)
                for (prefix in setOf("", prefixFor(Kind.PROJECTILE, e))) for (v in 0 until MAX_VARIANTS) anim(Kind.PROJECTILE, e, v, prefix)
                anim(Kind.FREEZE, e, 0, "")
                anim(Kind.HIT, e, 0)
            }
        }.apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    /** The square stone spell button for [e], [size] pixels, with rounded transparent corners. */
    fun button(e: Element, size: Int): Bitmap = scaled.getOrPut(Triple(Kind.BUTTON, e, size)) {
        Bitmap.createScaledBitmap(source(Kind.BUTTON, e), size, size, true)
    }

    // Walls (art/walls/PROMPTS.md): the button above each field column, and the block on the field.
    private val wallButtonSource: Bitmap by lazy { decode(R.drawable.wall_button) }
    private val wallButtons = HashMap<Int, Bitmap>()
    private val wallBlocks = HashMap<Long, Bitmap>()

    // Wall variants wall_block_1, wall_block_2 … (variations of one crystal brick wall), found by name;
    // a pack's are <prefix>wall_block_1 … (the classic ones if the pack has none).
    @Suppress("DiscouragedApi")
    private fun wallIdsOf(prefix: String): IntArray = generateSequence(1) { it + 1 }
        .map { res.getIdentifier("${prefix}wall_block_$it", "drawable", packageName) }
        .takeWhile { it != 0 }
        .toList().toIntArray()

    private var wallIds: IntArray? = null
    private val wallBlockIds: IntArray
        get() = wallIds ?: (wallIdsOf(pack.prefix).takeIf { it.isNotEmpty() } ?: wallIdsOf("")).also { wallIds = it }
    private val wallBlockSources = HashMap<Int, Bitmap>()

    // The shop's and the unlock popup's previews of any pack, whichever is in use.
    private val previewWalls = HashMap<String, Bitmap>()

    /** Pack [p]'s globe sheet for [e] (the classic one if it has none), for previews. */
    fun packGlobe(p: ElementPack, e: Element): Anim? {
        val prefix = if (p.prefix.isNotEmpty() && sheetId(Kind.GLOBE, e, 0, p.prefix) != 0) p.prefix else ""
        return anim(Kind.GLOBE, e, 0, prefix)
    }

    /** Pack [p]'s shot sheet for [e] (the classic one if it has none), for previews. */
    fun packShot(p: ElementPack, e: Element): Anim? {
        val prefix = if (p.prefix.isNotEmpty() && sheetId(Kind.PROJECTILE, e, 0, p.prefix) != 0) p.prefix else ""
        return anim(Kind.PROJECTILE, e, 0, prefix)
    }

    /** Pack [p]'s first wall block, [size] pixels, for previews. */
    fun packWall(p: ElementPack, size: Int): Bitmap = previewWalls.getOrPut("${p.id}/$size") {
        val id = (wallIdsOf(p.prefix).takeIf { it.isNotEmpty() } ?: wallIdsOf("")).first()
        Bitmap.createScaledBitmap(decode(id), size, size, true)
    }

    /** The square stone wall button, [size] pixels, with rounded transparent corners. */
    fun wallButton(size: Int): Bitmap = wallButtons.getOrPut(size) { Bitmap.createScaledBitmap(wallButtonSource, size, size, true) }

    @Volatile
    private var wallBreakAnim: Anim? = null
    private val wallBreakLoading = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Starts decoding the wall-break sheet in the background (for duels with walls). */
    fun prepareWallBreak() {
        if (wallBreakAnim != null || !wallBreakLoading.compareAndSet(false, true)) return
        Thread {
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                if (hardwareBitmaps) inPreferredConfig = Bitmap.Config.HARDWARE
            }
            val sheet = BitmapFactory.decodeResource(res, R.drawable.wall_break, opts) ?: decode(R.drawable.wall_break)
            wallBreakAnim = Anim(sheet, FREEZE_COLS, FREEZE_ROWS)
        }.apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    /**
     * A crystal wall shattering (art/walls/PROMPTS.md): 16 square frames (4×4 sheet `wall_break`), the
     * whole wall in the first frame and the last flying shards in the last. Null until decoded (it
     * starts decoding if needed).
     */
    fun wallBreak(): Anim? = wallBreakAnim ?: run { prepareWallBreak(); null }

    /**
     * A conjured wall of arcane crystal bricks for one field cell, [size] pixels, with slightly rounded
     * corners. [look] (the wall's, see Board.wallLook) picks one of the variants. [mine]: your wall, in
     * your element pack's look (the bot's walls keep the classic one).
     */
    fun wallBlock(size: Int, look: Int, mine: Boolean = true): Bitmap {
        val ids = if (mine) wallBlockIds else classicWallIds
        val id = ids[Math.floorMod(look, ids.size)]
        return wallBlocks.getOrPut(id.toLong() shl 32 or size.toLong()) {
            val source = wallBlockSources.getOrPut(id) { decode(id) }
            Bitmap.createScaledBitmap(source, size, size, true)
        }
    }

    /** The classic walls, for the bot's (and starting) walls whatever pack you use. */
    private val classicWallIds: IntArray by lazy { wallIdsOf("") }

    // Player avatars: an idle pose each, and one cast sheet at a time (a sheet is 24 frames, so only
    // the cast being played is kept; the next one is decoded in the background when a cast starts).
    private val avatarIdles = HashMap<String, Bitmap>()

    @Volatile
    private var avatarCastKey: String? = null

    @Volatile
    private var avatarCastAnim: Anim? = null

    /** The avatar's idle pose (also the first frame of every cast). */
    fun avatarIdle(a: Avatar): Bitmap = avatarIdles.getOrPut(a.id) { decode(a.idleRes) }

    /**
     * Starts decoding cast animation [k] (0 until 3) of [a] in the background, replacing the one kept
     * before; [avatarCast] returns it once it's ready.
     */
    fun prepareAvatarCast(a: Avatar, k: Int) {
        val key = "${a.id}/${Math.floorMod(k, a.castRes.size)}"
        if (key == avatarCastKey) return
        avatarCastKey = key
        avatarCastAnim = null
        Thread {
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                if (hardwareBitmaps) inPreferredConfig = Bitmap.Config.HARDWARE
            }
            val id = a.castRes[Math.floorMod(k, a.castRes.size)]
            val sheet = BitmapFactory.decodeResource(res, id, opts) ?: decode(id)
            val anim = Anim(sheet, Avatars.CAST_COLS, Avatars.CAST_ROWS)
            if (avatarCastKey == key) avatarCastAnim = anim
        }.apply { isDaemon = true }.start()
    }

    /**
     * Cast animation [k] of [a] if it's decoded (see [prepareAvatarCast]), else null: frames run from
     * the idle pose, through the spell, back to it.
     */
    fun avatarCast(a: Avatar, k: Int): Anim? =
        if (avatarCastKey == "${a.id}/${Math.floorMod(k, a.castRes.size)}") avatarCastAnim else null

    // Single animation sheets (an enemy's cast, your avatar's defeat), 48 frames in Avatars' grid,
    // decoded in the background the first time they're asked for; and still images (idle and
    // defeated poses).
    private val sheets = ConcurrentHashMap<Int, Anim>()
    private val sheetsLoading = ConcurrentHashMap.newKeySet<Int>()
    private val images = HashMap<Int, Bitmap>()
    private val opaque = HashMap<Int, Rect>()

    /** Frees every decoded sheet but [keep]: a new duel needs only its own (each is about 13 MB). */
    fun retainSheets(keep: Set<Int>) {
        sheets.keys.retainAll(keep)
    }

    /** Starts decoding the 48-frame sheet [id] in the background (see [sheetIfReady]). */
    fun prepareSheet(id: Int) {
        if (sheets.containsKey(id) || !sheetsLoading.add(id)) return
        Thread {
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                if (hardwareBitmaps) inPreferredConfig = Bitmap.Config.HARDWARE
            }
            val sheet = BitmapFactory.decodeResource(res, id, opts) ?: decode(id)
            sheets[id] = Anim(sheet, Avatars.CAST_COLS, Avatars.CAST_ROWS)
            sheetsLoading.remove(id)
        }.apply { isDaemon = true }.start()
    }

    /** The sheet [id] once decoded (it starts decoding if needed), else null. */
    fun sheetIfReady(id: Int): Anim? = sheets[id] ?: run { prepareSheet(id); null }

    /** A still image (an idle or defeated pose), decoded once. */
    fun image(id: Int): Bitmap = images.getOrPut(id) { decode(id) }

    /** The part of image [id] that isn't transparent (to draw a defeated pose without its empty margins). */
    fun opaqueBounds(id: Int): Rect = opaque.getOrPut(id) {
        val b = image(id)
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        var x0 = b.width; var y0 = b.height; var x1 = -1; var y1 = -1
        for (y in 0 until b.height) for (x in 0 until b.width) {
            if ((px[y * b.width + x] ushr 24) > 40) {
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
            }
        }
        if (x1 < 0) Rect(0, 0, b.width, b.height) else Rect(x0, y0, x1 + 1, y1 + 1)
    }

    /** The soft aura behind a globe in the element's colour: one small bitmap per element, drawn scaled. */
    private fun aura(e: Element): Bitmap = auras.getOrPut(e) {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val r = size / 2f
        val col = color(e)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(r, r, r, intArrayOf(withAlpha(col, 120), withAlpha(col, 0)), floatArrayOf(0.8f, 1f), Shader.TileMode.CLAMP)
        Canvas(bmp).drawCircle(r, r, r, p)
        bmp
    }

    // ---------------------------------------------------------------- mage

    private val robePath = Path().apply {
        moveTo(-25f, 0f); cubicTo(-22f, -18f, -17f, -40f, -11f, -60f)
        lineTo(11f, -60f); cubicTo(17f, -40f, 22f, -18f, 27f, 0f); close()
    }
    private val robeShade = Path().apply {
        moveTo(-25f, 0f); cubicTo(-22f, -18f, -17f, -40f, -11f, -60f)
        lineTo(-4f, -60f); cubicTo(-7f, -40f, -9f, -18f, -9f, 0f); close()
    }
    private val hemPath = Path().apply { moveTo(-24.5f, -3f); quadTo(1f, 2f, 26.5f, -3f) }
    private val hatPath = Path().apply {
        moveTo(-12f, -72f); quadTo(-2f, -88f, -15f, -104f); quadTo(5f, -94f, 17f, -72f); close()
    }
    private val beardPath = Path().apply {
        moveTo(-3f, -64f); lineTo(12f, -64f); quadTo(10f, -51f, 3f, -44f); quadTo(-2f, -53f, -3f, -64f); close()
    }

    private class Pose(val handX: Float, val handY: Float, val dirX: Float, val dirY: Float)

    /** Staff pose: at rest the staff leans a little; while casting it is raised toward the field. */
    private fun pose(cast: Float): Pose {
        val k = smooth01(cast)
        val angle = Math.toRadians((10.0 + 32.0 * k))
        return Pose(17f + 5f * k, -36f - 17f * k, sin(angle).toFloat(), -cos(angle).toFloat())
    }

    /** Screen position of the orb on top of the mage's staff. */
    fun mageOrb(x: Float, footY: Float, h: Float, facingRight: Boolean, cast: Float): PointF {
        val p = pose(cast)
        val s = h / 100f
        val tx = p.handX + p.dirX * 44f
        val ty = p.handY + p.dirY * 44f
        return PointF(x + (if (facingRight) tx else -tx) * s, footY + ty * s)
    }

    /**
     * Draws a mage standing at ([x], [footY]) and [h] pixels tall.
     * [castColor]/[cast] animate casting: magic circle, raised staff, glowing orb, inflowing sparks.
     */
    fun drawMage(
        c: Canvas, x: Float, footY: Float, h: Float, facingRight: Boolean, robe: Int, beard: Boolean,
        castColor: Int?, cast: Float, time: Float, flash: Float, tilt: Float, alpha: Float,
    ) {
        val s = h / 100f
        val a = (alpha * 255).toInt()
        c.save()
        c.translate(x, footY)
        c.rotate(if (facingRight) -tilt else tilt)
        c.scale(if (facingRight) s else -s, s)
        val robeCol = mix(robe, RED, flash * 0.6f)

        if (castColor != null) {
            val w = 30f + 30f * cast
            stroke.strokeWidth = 2.4f
            stroke.color = withAlpha(castColor, ((110 + 120 * cast) * alpha).toInt())
            c.drawOval(-w, -6f, w, 6f, stroke)
            fill.color = withAlpha(castColor, a)
            for (i in 0 until 8) {
                val an = time * 2.2f + i * PI.toFloat() / 4f
                c.drawCircle(cos(an) * w, sin(an) * 6f, 2f, fill)
            }
        }
        fill.color = withAlpha(BLACK, (85 * alpha).toInt())
        c.drawOval(-26f, -4f, 26f, 4f, fill)

        fill.color = withAlpha(robeCol, a)
        c.drawPath(robePath, fill)
        fill.color = withAlpha(mix(robeCol, BLACK, 0.3f), a)
        c.drawPath(robeShade, fill)
        stroke.color = withAlpha(GOLD, a)
        stroke.strokeWidth = 3f
        c.drawPath(hemPath, stroke)
        fill.color = withAlpha(GOLD, a)
        c.drawRect(-12f, -41f, 13f, -37f, fill)

        fill.color = withAlpha(SKIN, a)
        c.drawCircle(3f, -66f, 9f, fill)
        if (beard) {
            fill.color = withAlpha(0xFFDAD8E6.toInt(), a)
            c.drawPath(beardPath, fill)
        }
        fill.color = withAlpha(castColor?.let { mix(it, WHITE, 0.4f) } ?: 0xFF2A2140.toInt(), a)
        c.drawCircle(8f, -67f, 1.8f, fill)

        val hatCol = mix(robeCol, BLACK, 0.25f)
        fill.color = withAlpha(hatCol, a)
        c.drawPath(hatPath, fill)
        c.drawOval(-16f, -76f, 22f, -68f, fill)
        stroke.color = withAlpha(GOLD, a)
        stroke.strokeWidth = 2.4f
        c.drawLine(-11f, -75.5f, 16.5f, -75.5f, stroke)

        val p = pose(cast)
        val bx = p.handX - p.dirX * 34f
        val by = p.handY - p.dirY * 34f
        val tx = p.handX + p.dirX * 44f
        val ty = p.handY + p.dirY * 44f
        stroke.color = withAlpha(0xFF8A5A35.toInt(), a)
        stroke.strokeWidth = 3.6f
        c.drawLine(bx, by, tx, ty, stroke)
        stroke.color = withAlpha(robeCol, a)
        stroke.strokeWidth = 7f
        c.drawLine(7f, -55f, p.handX, p.handY, stroke)
        fill.color = withAlpha(SKIN, a)
        c.drawCircle(p.handX, p.handY, 3.4f, fill)

        val orb = castColor ?: 0xFFBFD9FF.toInt()
        val glow = if (castColor != null) cast else 0.15f
        fill.color = withAlpha(orb, (45 * alpha).toInt())
        c.drawCircle(tx, ty, 8f + 12f * glow, fill)
        fill.color = withAlpha(orb, (90 * alpha).toInt())
        c.drawCircle(tx, ty, 5.5f + 6f * glow, fill)
        fill.color = withAlpha(mix(orb, WHITE, 0.35f), a)
        c.drawCircle(tx, ty, 4.2f + 1.5f * glow, fill)
        if (castColor != null) {
            for (i in 0 until 6) {
                val f = (time * 1.6f + i / 6f) % 1f
                val an = i * 1.047f + time * 4f
                val rad = 26f * (1f - f)
                fill.color = withAlpha(castColor, (255 * f * alpha).toInt())
                c.drawCircle(tx + cos(an) * rad, ty + sin(an) * rad, 1.8f, fill)
            }
        }
        c.restore()
    }

    // ---------------------------------------------------------------- training dummy (Midjourney)
    // res/drawable-nodpi/dummy_idle.png: the standing pose. dummy_hit.png: DUMMY_HIT_FRAMES frames of
    // the hit reaction in a DUMMY_HIT_COLS-wide grid, same size and framing as the idle pose
    // (made with tools/art/cut-sprite.ps1, see art/dummy/README.md).

    private val dummyIdle: Bitmap by lazy { decode(R.drawable.dummy_idle) }
    private val dummyHit: Bitmap by lazy { decode(R.drawable.dummy_hit) }
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val spriteSrc = Rect()
    private val spriteDst = RectF()

    private fun decode(id: Int): Bitmap = BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })

    /**
     * Draws the training dummy standing at ([x], [footY]), [h] pixels tall (bottom centre of the image
     * on the foot point). [hitFrame] < 0 draws the idle pose, otherwise that frame of the hit reaction.
     * [flash] tints it red when it takes damage; [tilt] tips it over (degrees) when it is defeated.
     */
    fun drawDummySprite(c: Canvas, x: Float, footY: Float, h: Float, hitFrame: Int, flash: Float, tilt: Float, alpha: Float) {
        val bmp: Bitmap
        if (hitFrame in 0 until DUMMY_HIT_FRAMES) {
            bmp = dummyHit
            val fw = bmp.width / DUMMY_HIT_COLS
            val fh = bmp.height / ((DUMMY_HIT_FRAMES + DUMMY_HIT_COLS - 1) / DUMMY_HIT_COLS)
            spriteSrc.set((hitFrame % DUMMY_HIT_COLS) * fw, (hitFrame / DUMMY_HIT_COLS) * fh, (hitFrame % DUMMY_HIT_COLS + 1) * fw, (hitFrame / DUMMY_HIT_COLS + 1) * fh)
        } else {
            bmp = dummyIdle
            spriteSrc.set(0, 0, bmp.width, bmp.height)
        }
        val w = h * spriteSrc.width() / spriteSrc.height()
        spritePaint.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        spritePaint.colorFilter =
            if (flash > 0f) PorterDuffColorFilter(withAlpha(RED, (110 * flash).toInt()), PorterDuff.Mode.SRC_ATOP) else null
        c.save()
        c.translate(x, footY)
        if (tilt != 0f) c.rotate(tilt)
        spriteDst.set(-w / 2f, -h, w / 2f, 0f)
        c.drawBitmap(bmp, spriteSrc, spriteDst, spritePaint)
        c.restore()
    }
}
