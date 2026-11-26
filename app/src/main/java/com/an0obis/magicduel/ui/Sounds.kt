package com.an0obis.magicduel.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.an0obis.magicduel.R
import com.an0obis.magicduel.game.Element
import kotlin.random.Random

/**
 * The game's sound effects (CC0 packs, see audio/SOUNDS.md), played with a [SoundPool]. Every effect
 * can have a few variants; one is picked at random and its pitch varies a little, so repeated
 * sounds don't feel mechanical. Nothing plays while [enabled] is false (Options → Sound effects).
 */
class Sounds(context: Context) {
    enum class Fx { CAST, LAUNCH, FREEZE, POP, HIT_FIRE, HIT_WATER, HIT_EARTH, HIT_AIR, HIT_SHADOW, UI_CLICK, UI_MENU, UI_TOGGLE, UI_BACK, WIN, LOSE }

    var enabled = true

    private val pool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids: Map<Fx, IntArray> = mapOf(
        Fx.CAST to intArrayOf(R.raw.sfx_cast),
        Fx.LAUNCH to intArrayOf(R.raw.sfx_launch),
        Fx.FREEZE to intArrayOf(R.raw.sfx_freeze_1, R.raw.sfx_freeze_2),
        Fx.POP to intArrayOf(R.raw.sfx_pop_1, R.raw.sfx_pop_2),
        Fx.HIT_FIRE to intArrayOf(R.raw.sfx_hit_fire_1, R.raw.sfx_hit_fire_2),
        Fx.HIT_WATER to intArrayOf(R.raw.sfx_hit_water_1, R.raw.sfx_hit_water_2),
        Fx.HIT_EARTH to intArrayOf(R.raw.sfx_hit_earth_1, R.raw.sfx_hit_earth_2),
        Fx.HIT_AIR to intArrayOf(R.raw.sfx_hit_air_1, R.raw.sfx_hit_air_2),
        Fx.HIT_SHADOW to intArrayOf(R.raw.sfx_hit_shadow),
        Fx.UI_CLICK to intArrayOf(R.raw.sfx_ui_click),
        Fx.UI_MENU to intArrayOf(R.raw.sfx_ui_menu),
        Fx.UI_TOGGLE to intArrayOf(R.raw.sfx_ui_toggle),
        Fx.UI_BACK to intArrayOf(R.raw.sfx_ui_back),
        Fx.WIN to intArrayOf(R.raw.sfx_win),
        Fx.LOSE to intArrayOf(R.raw.sfx_lose),
    ).mapValues { (_, res) -> IntArray(res.size) { pool.load(context, res[it], 1) } }

    /** Plays [fx] at [volume] (0..1) with a slightly random pitch. */
    fun play(fx: Fx, volume: Float = 1f) {
        if (!enabled) return
        val variants = ids[fx] ?: return
        val id = variants[Random.nextInt(variants.size)]
        val v = volume.coerceIn(0f, 1f)
        pool.play(id, v, v, 1, 0, 0.94f + Random.nextFloat() * 0.12f)
    }

    /** Magic of [e] hits a mage. */
    fun hit(e: Element, volume: Float = 1f) = play(
        when (e) {
            Element.FIRE -> Fx.HIT_FIRE
            Element.WATER -> Fx.HIT_WATER
            Element.EARTH -> Fx.HIT_EARTH
            Element.AIR, Element.LIGHTNING -> Fx.HIT_AIR
            Element.SHADOW -> Fx.HIT_SHADOW
        },
        volume,
    )

    fun pause() = pool.autoPause()
    fun resume() = pool.autoResume()
}
