package com.an0obis.magicduel.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarsTest {
    @Test
    fun onlyTheArchmageIsFreeAndTheOthersCostEssence() {
        assertEquals(listOf(Avatars.MAGE), Avatars.all.filter { !it.paid })
        assertEquals(Avatars.MAGE, Avatars.all.first()) // the free one is the default
        assertEquals(3, Avatars.SORCERESS.price)
        assertEquals(5, Avatars.CONJURER.price)
        assertEquals(listOf(Avatars.SORCERESS, Avatars.CONJURER), Avatars.paid)
    }
}
