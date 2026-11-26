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
    // res/drawable-nodpi: orb_* (globes), proj_* (flying projectiles, head on the right),
    // btn_* (spell buttons), sym_* (element symbols). Sources and prompts are in the project's art/ folder.

    private enum class Kind { GLOBE, PROJECTILE, BUTTON, SYMBOL, FREEZE, HIT }

    private val sources = HashMap<Pair<Kind, Element>, Bitmap>()
    private val scaled = HashMap<Triple<Kind, Element, Int>, Bitmap>()

    private fun source(kind: Kind, e: Element): Bitmap = sources.getOrPut(kind to e) {
        val id = when (kind) {
            Kind.GLOBE -> when (e) {
                Element.FIRE -> R.drawable.orb_fire
                Element.WATER -> R.drawable.orb_water
                Element.EARTH -> R.drawable.orb_earth
                Element.AIR -> R.drawable.orb_air
                Element.LIGHTNING -> R.drawable.orb_lightning
                Element.SHADOW -> R.drawable.orb_shadow
            }
            Kind.PROJECTILE -> when (e) {
                Element.FIRE -> R.drawable.proj_fire
                Element.WATER -> R.drawable.proj_water
                Element.EARTH -> R.drawable.proj_earth
                Element.AIR -> R.drawable.proj_air
                Element.LIGHTNING -> R.drawable.proj_lightning
                Element.SHADOW -> R.drawable.proj_shadow
            }
            Kind.FREEZE, Kind.HIT -> error("$kind animations have no still image")
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
     * size. If the element has animation sheets, [look] picks one of its variants and [frame] the
     * animation frame (any number; it wraps around); otherwise the still image is drawn.
     */
    fun drawGlobe(c: Canvas, e: Element, dst: RectF, frame: Int, look: Int, paint: Paint) {
        c.drawBitmap(aura(e), null, dst, paint)
        val inset = dst.width() * 0.06f
        inner.set(dst.left + inset, dst.top + inset, dst.right - inset, dst.bottom - inset)
        val a = anim(Kind.GLOBE, e, variant(Kind.GLOBE, e, look))
        if (a != null) a.draw(c, frame, inner, paint) else c.drawBitmap(source(Kind.GLOBE, e), null, inner, paint)
    }

    /**
     * The flying projectile for [e], head on the right; [look] picks an animation variant. Elements
     * without animation sheets get their still image as a one-frame animation.
     */
    fun projectile(e: Element, look: Int): Anim =
        anim(Kind.PROJECTILE, e, variant(Kind.PROJECTILE, e, look)) ?: stills.getOrPut(e) { Anim(source(Kind.PROJECTILE, e), 1, 1) }

    /**
     * The landed shot freezing into a globe: [FREEZE_COLS] × [FREEZE_ROWS] frames, [FREEZE_ASPECT]
     * times as wide as tall. The comet's head (and the globe it becomes) is centred half a frame
     * height in from the right edge, the globe [FREEZE_GLOBE] of the height across. Null if the
     * element has no freeze sheet.
     */
    fun freeze(e: Element): Anim? = anim(Kind.FREEZE, e, 0)

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
    //   look number. Elements without a sheet simply use the still image.
    // - freeze_<element> and hit_<element>: 16 frames in a 4×4 grid.
    // Each sheet is decoded once, only when an element in play needs it, and kept whole; frames are
    // drawn from it. With [hardwareBitmaps] sheets live only in GPU memory.

    /** Decode sheets as hardware bitmaps (GPU memory only); needs a hardware-accelerated canvas. */
    @Volatile
    var hardwareBitmaps = false

    private val variantCounts = ConcurrentHashMap<Pair<Kind, Element>, Int>()
    private val anims = ConcurrentHashMap<String, Any>()
    private val noSheet = Any()
    private val stills = HashMap<Element, Anim>()
    private val auras = HashMap<Element, Bitmap>()
    private val inner = RectF()

    @Suppress("DiscouragedApi")
    private fun sheetId(kind: Kind, e: Element, variant: Int): Int {
        val el = e.name.lowercase()
        val name = when (kind) {
            Kind.GLOBE -> "orb_${el}_anim"
            Kind.PROJECTILE -> "proj_${el}_anim"
            Kind.HIT -> return res.getIdentifier("hit_$el", "drawable", packageName)
            else -> return res.getIdentifier("freeze_$el", "drawable", packageName)
        } + if (variant == 0) "" else "${variant + 1}"
        return res.getIdentifier(name, "drawable", packageName)
    }

    /** Which animation variant to show for [look]: how many there are is found from the resources. */
    private fun variant(kind: Kind, e: Element, look: Int): Int {
        val n = variantCounts.getOrPut(kind to e) {
            var k = 0
            while (k < MAX_VARIANTS && sheetId(kind, e, k) != 0) k++
            k
        }
        return if (n <= 1) 0 else Math.floorMod(look / 7, n)
    }

    /**
     * One animation's sheet, decoded the first time it is needed (once, even if two threads ask at
     * the same moment), or null if there is no such sheet.
     */
    private fun anim(kind: Kind, e: Element, variant: Int): Anim? =
        anims.computeIfAbsent("$kind/$e/$variant") { loadAnim(kind, e, variant) ?: noSheet } as? Anim

    private fun loadAnim(kind: Kind, e: Element, variant: Int): Anim? {
        val id = sheetId(kind, e, variant)
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
                for (v in 0 until MAX_VARIANTS) {
                    anim(Kind.GLOBE, e, v)
                    anim(Kind.PROJECTILE, e, v)
                }
                anim(Kind.FREEZE, e, 0)
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
    private val wallBlockSource: Bitmap by lazy { decode(R.drawable.wall_block) }
    private val wallButtons = HashMap<Int, Bitmap>()
    private val wallBlocks = HashMap<Int, Bitmap>()

    /** The square stone wall button, [size] pixels, with rounded transparent corners. */
    fun wallButton(size: Int): Bitmap = wallButtons.getOrPut(size) { Bitmap.createScaledBitmap(wallButtonSource, size, size, true) }

    /** A conjured stone wall block for one field cell, [size] pixels, with rounded corners. */
    fun wallBlock(size: Int): Bitmap = wallBlocks.getOrPut(size) { Bitmap.createScaledBitmap(wallBlockSource, size, size, true) }

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
