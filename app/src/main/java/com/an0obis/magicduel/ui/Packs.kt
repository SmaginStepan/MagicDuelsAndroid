package com.an0obis.magicduel.ui

import com.an0obis.magicduel.R
import com.an0obis.magicduel.game.Economy

/**
 * An element pack: another look for the globes, the flying shots and the walls (art/packs/PROMPTS.md).
 * Its art has the classic resources' names with [prefix] in front (`spiked_orb_fire_anim`,
 * `spiked_proj_fire_anim`, `spiked_wall_block_1` …); an element it has no art for keeps the classic
 * look. [price]: the essence it costs to unlock (0 for the classic look, which everyone has).
 * [glow]: its colour in the shop.
 */
class ElementPack(val id: String, val nameRes: Int, val prefix: String, val price: Int, val glow: Int) {
    val paid: Boolean get() = price > 0
}

object ElementPacks {
    val CLASSIC = ElementPack("classic", R.string.pack_classic, "", 0, 0xFF7F8CFF.toInt())

    /** Dark spikes: spiked obsidian globes, spiked iron balls for shots, spiked obsidian walls. */
    val SPIKED = ElementPack("spiked", R.string.pack_spiked, "spiked_", Economy.PRICE_NORMAL, 0xFFB45CFF.toInt())

    /**
     * Living grove: glass orbs wrapped in roots and leaves, thorn spears of living wood for shots, walls
     * of woven roots and moss.
     */
    val GROVE = ElementPack("grove", R.string.pack_grove, "grove_", Economy.PRICE_PREMIUM, 0xFF6FD36A.toInt())

    /**
     * Plush toys: round plush creatures with button eyes, plush toy rockets for shots, walls of quilted
     * patchwork cushions.
     */
    val PLUSH = ElementPack("plush", R.string.pack_plush, "plush_", Economy.PRICE_PREMIUM, 0xFFFF8FC8.toInt())

    /**
     * Gaming and streaming: glossy arcade buttons with element icons, burning like-hearts for shots,
     * walls of backlit keyboard keys.
     */
    val STREAM = ElementPack("stream", R.string.pack_stream, "stream_", Economy.PRICE_PREMIUM, 0xFFFF4FD8.toInt())

    /**
     * Gears and mech, light: white armored reactor cores with a glowing element lens, flaming silver cogs
     * for shots, walls of silver and gold gears.
     */
    val MECH = ElementPack("mech", R.string.pack_mech, "mech_", Economy.PRICE_NORMAL, 0xFFE8C46A.toInt())

    /** Every pack, the classic look first (the default). */
    val all = listOf(CLASSIC, SPIKED, GROVE, PLUSH, STREAM, MECH)

    /** The packs sold in the shop (the classic look is free for everyone). */
    val paid = all.filter { it.paid }

    fun byId(id: String?): ElementPack = all.firstOrNull { it.id == id } ?: CLASSIC
}
