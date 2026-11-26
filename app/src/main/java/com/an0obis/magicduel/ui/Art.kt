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
 * Globes use the Midjourney images in res/drawable-nodpi (see art/elements/PROMPTS.md);
 * everything else is vector, authored in a 100-unit box and scaled, so it stays crisp at any size.
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

    private val firePath = Path().apply {
        moveTo(0f, -48f)
        cubicTo(14f, -28f, 38f, -14f, 36f, 12f)
        cubicTo(34f, 36f, 18f, 48f, 0f, 48f)
        cubicTo(-18f, 48f, -36f, 36f, -36f, 14f)
        cubicTo(-36f, -2f, -27f, -12f, -21f, -26f)
        cubicTo(-14f, -14f, -9f, -10f, -4f, -9f)
        cubicTo(-2f, -24f, -8f, -36f, 0f, -48f)
        close()
    }
    private val fireCore = Path().apply {
        moveTo(0f, 2f)
        cubicTo(10f, 14f, 18f, 22f, 16f, 31f)
        cubicTo(14f, 42f, -14f, 42f, -16f, 31f)
        cubicTo(-18f, 22f, -8f, 14f, 0f, 2f)
        close()
    }
    private val waterPath = Path().apply {
        moveTo(0f, -48f)
        cubicTo(12f, -30f, 36f, -6f, 36f, 14f)
        cubicTo(36f, 36f, 20f, 48f, 0f, 48f)
        cubicTo(-20f, 48f, -36f, 36f, -36f, 14f)
        cubicTo(-36f, -6f, -12f, -30f, 0f, -48f)
        close()
    }
    private val earthPath = Path().apply {
        moveTo(-48f, 36f)
        lineTo(-14f, -32f)
        lineTo(2f, -2f)
        lineTo(17f, -22f)
        lineTo(48f, 36f)
        close()
    }
    private val airPaths = listOf(
        Path().apply {
            moveTo(-42f, -14f); lineTo(14f, -14f)
            cubicTo(32f, -14f, 34f, -40f, 18f, -40f)
            cubicTo(8f, -40f, 4f, -30f, 10f, -25f)
        },
        Path().apply {
            moveTo(-42f, 6f); lineTo(26f, 6f)
            cubicTo(44f, 6f, 46f, 32f, 29f, 32f)
            cubicTo(19f, 32f, 15f, 22f, 21f, 18f)
        },
        Path().apply { moveTo(-32f, 26f); lineTo(2f, 26f) },
    )
    private val boltPath = Path().apply {
        moveTo(10f, -48f); lineTo(-26f, 6f); lineTo(-2f, 6f); lineTo(-12f, 48f)
        lineTo(28f, -10f); lineTo(4f, -10f); lineTo(18f, -48f); close()
    }
    private val moonPath = Path().apply {
        addCircle(0f, 0f, 40f, Path.Direction.CW)
        op(Path().apply { addCircle(18f, -12f, 33f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
    }

    /** Draws the symbol of [e] centred on (cx, cy), about [size] pixels across. */
    fun drawSymbol(c: Canvas, e: Element, cx: Float, cy: Float, size: Float, color: Int) {
        c.save()
        c.translate(cx, cy)
        val s = size / 100f
        c.scale(s, s)
        fill.color = color
        when (e) {
            Element.FIRE -> {
                c.drawPath(firePath, fill)
                fill.color = withAlpha(mix(color, BLACK, 0.35f), Color.alpha(color))
                c.drawPath(fireCore, fill)
            }
            Element.WATER -> c.drawPath(waterPath, fill)
            Element.EARTH -> c.drawPath(earthPath, fill)
            Element.AIR -> {
                stroke.color = color
                stroke.strokeWidth = 10f
                airPaths.forEach { c.drawPath(it, stroke) }
            }
            Element.LIGHTNING -> c.drawPath(boltPath, fill)
            Element.SHADOW -> c.drawPath(moonPath, fill)
        }
        c.restore()
    }

    // ---------------------------------------------------------------- Midjourney sprites
    // res/drawable-nodpi: orb_* (globes), proj_* (flying projectiles, head on the right),
    // btn_* (spell buttons). Sources and prompts are in the project's art/ folder.

    private enum class Kind { GLOBE, PROJECTILE, BUTTON, FREEZE, HIT }

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
        }
        BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })
    }

    /**
     * A crystal globe with the element sealed inside, [size] pixels square. If the element has
     * animation sheets, [look] picks one of its variants and [frame] the animation frame (any
     * number; it wraps around).
     */
    fun globe(e: Element, size: Int, frame: Int = 0, look: Int = 0): Bitmap {
        animFrames(Kind.GLOBE, e, variant(Kind.GLOBE, e, look), size)?.let { return it[Math.floorMod(frame, it.size)] }
        return scaled.getOrPut(Triple(Kind.GLOBE, e, size)) { renderGlobe(e, size) }
    }

    /**
     * The flying projectile for [e], head on the right, [height] pixels tall (width follows the image).
     * If the element has animation sheets, [look] picks a variant and [frame] the animation frame.
     */
    fun projectile(e: Element, height: Int, frame: Int = 0, look: Int = 0): Bitmap {
        animFrames(Kind.PROJECTILE, e, variant(Kind.PROJECTILE, e, look), height)?.let { return it[Math.floorMod(frame, it.size)] }
        return scaled.getOrPut(Triple(Kind.PROJECTILE, e, height)) {
            val src = source(Kind.PROJECTILE, e)
            Bitmap.createScaledBitmap(src, max(1, height * src.width / src.height), height, true)
        }
    }

    /**
     * The landed shot freezing into a globe: [FREEZE_COLS] × [FREEZE_ROWS] frames, [height] pixels
     * tall and [FREEZE_ASPECT] times as wide. The comet's head (and the globe it becomes) is centred
     * half a frame height in from the right edge, the globe [FREEZE_GLOBE] of the height across.
     * Null if the element has no freeze sheet.
     */
    fun freezeFrames(e: Element, height: Int): Array<Bitmap>? = animFrames(Kind.FREEZE, e, 0, height)

    /**
     * The burst where magic of [e] hits a duellist: 16 square frames (4×4 sheet `hit_<element>`),
     * [size] pixels, the burst at its peak in the first frame and gone in the last. Null if the
     * element has no hit sheet.
     */
    fun hitFrames(e: Element, size: Int): Array<Bitmap>? = animFrames(Kind.HIT, e, 0, size)

    // ---------------------------------------------------------------- Midjourney animations
    // Optional sheets, see art/ANIMATIONS.md:
    // - orb_<element>_anim, _anim2 … and proj_<element>_anim, _anim2 …: variants of a Midjourney
    //   "Loop" video, 24 frames in a 6×4 grid. Each globe and shot shows one variant, picked by its
    //   look number. Elements without a sheet simply use the still image.
    // - freeze_<element>: a shot freezing into a crystal globe, 16 frames in a 4×4 grid.
    // A sheet is only decoded to cut out frames at the size they are drawn; then it is let go.

    private val variantCounts = ConcurrentHashMap<Pair<Kind, Element>, Int>()
    private val animCache = ConcurrentHashMap<String, Array<Bitmap>>()
    private val noAnim = ConcurrentHashMap<String, Boolean>()

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

    /** All frames of one animation, already sized for drawing, or null if there is no sheet. */
    private fun animFrames(kind: Kind, e: Element, variant: Int, size: Int): Array<Bitmap>? {
        val key = "$kind/$e/$variant/$size"
        animCache[key]?.let { return it }
        if (noAnim.containsKey(key)) return null
        val id = sheetId(kind, e, variant)
        if (id == 0) {
            noAnim[key] = true
            return null
        }
        val s = BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })
        val cols = if (kind == Kind.FREEZE || kind == Kind.HIT) FREEZE_COLS else ANIM_COLS
        val rows = if (kind == Kind.FREEZE || kind == Kind.HIT) FREEZE_ROWS else ANIM_ROWS
        val fw = s.width / cols
        val fh = s.height / rows
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val frames = Array(cols * rows) { k ->
            val src = Rect((k % cols) * fw, (k / cols) * fh, (k % cols + 1) * fw, (k / cols + 1) * fh)
            if (kind == Kind.GLOBE) {
                renderGlobe(e, size) { c, dst -> c.drawBitmap(s, src, dst, p) }
            } else {
                val w = max(1, size * fw / fh)
                Bitmap.createBitmap(w, size, Bitmap.Config.ARGB_8888).also { b ->
                    Canvas(b).drawBitmap(s, src, RectF(0f, 0f, w.toFloat(), size.toFloat()), p)
                }
            }
        }
        s.recycle()
        animCache[key] = frames
        return frames
    }

    /**
     * Cuts out the frames of every globe, shot and freeze animation of [elements] on a background
     * thread, at the sizes the duel draws them, so the first appearance of a variant doesn't stall
     * a frame. Anything not ready yet is simply built when it is first drawn.
     */
    fun preload(elements: List<Element>, globeSize: Int, shotHeight: Int, freezeHeight: Int, hitSizes: List<Int>) {
        Thread {
            for (e in elements) {
                for (v in 0 until MAX_VARIANTS) {
                    if (sheetId(Kind.GLOBE, e, v) != 0) animFrames(Kind.GLOBE, e, v, globeSize)
                    if (sheetId(Kind.PROJECTILE, e, v) != 0) animFrames(Kind.PROJECTILE, e, v, shotHeight)
                }
                animFrames(Kind.FREEZE, e, 0, freezeHeight)
                for (s in hitSizes) animFrames(Kind.HIT, e, 0, s)
            }
        }.apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    /** The square stone spell button for [e], [size] pixels, with rounded transparent corners. */
    fun button(e: Element, size: Int): Bitmap = scaled.getOrPut(Triple(Kind.BUTTON, e, size)) {
        Bitmap.createScaledBitmap(source(Kind.BUTTON, e), size, size, true)
    }

    /**
     * A soft aura in the element's colour, with the globe image on top at 88% of the size.
     * [body] draws the globe image into the given rectangle (the still image by default).
     */
    private fun renderGlobe(
        e: Element,
        size: Int,
        body: (Canvas, RectF) -> Unit = { c, dst -> c.drawBitmap(source(Kind.GLOBE, e), null, dst, Paint(Paint.FILTER_BITMAP_FLAG)) },
    ): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size / 2f
        val br = r * 0.88f
        val col = color(e)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(r, r, r, intArrayOf(withAlpha(col, 120), withAlpha(col, 0)), floatArrayOf(0.8f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(r, r, r, p)
        body(c, RectF(r - br, r - br, r + br, r + br))
        return bmp
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
