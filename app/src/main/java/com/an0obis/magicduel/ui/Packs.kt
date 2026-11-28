package com.an0obis.magicduel.ui

import com.an0obis.magicduel.R

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
    val SPIKED = ElementPack("spiked", R.string.pack_spiked, "spiked_", 25, 0xFFB45CFF.toInt())

    /** Every pack, the classic look first (the default). */
    val all = listOf(CLASSIC, SPIKED)

    fun byId(id: String?): ElementPack = all.firstOrNull { it.id == id } ?: CLASSIC
}
