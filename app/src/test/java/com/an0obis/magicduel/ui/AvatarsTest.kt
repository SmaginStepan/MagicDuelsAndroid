package com.an0obis.magicduel.ui

import com.an0obis.magicduel.game.Economy
import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarsTest {
    @Test
    fun onlyTheArchmageIsFreeAndTheOthersCostEssence() {
        assertEquals(listOf(Avatars.MAGE), Avatars.all.filter { !it.paid })
        assertEquals(Avatars.MAGE, Avatars.all.first()) // the free one is the default
        assertEquals(Economy.PRICE_NORMAL, Avatars.SORCERESS.price)
        assertEquals(Economy.PRICE_NORMAL, Avatars.CONJURER.price)
        assertEquals(listOf(Avatars.SORCERESS, Avatars.CONJURER), Avatars.paid)
    }

    @Test
    fun everythingForSaleCostsANormalOrAPremiumPrice() {
        val tiers = setOf(Economy.PRICE_NORMAL, Economy.PRICE_PREMIUM)
        (Avatars.paid.map { it.price } + ElementPacks.paid.map { it.price }).forEach { assert(it in tiers) { "price $it" } }
        assertEquals(0, ElementPacks.CLASSIC.price)
        assertEquals(Economy.PRICE_NORMAL, ElementPacks.SPIKED.price)
        assertEquals(listOf(Economy.PRICE_PREMIUM), listOf(ElementPacks.GROVE, ElementPacks.PLUSH, ElementPacks.STREAM).map { it.price }.distinct())
    }
}
