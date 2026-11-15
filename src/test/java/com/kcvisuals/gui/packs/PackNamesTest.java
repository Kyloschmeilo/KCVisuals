package com.kcvisuals.gui.packs;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PackNamesTest {
    @Test
    void stripsLeadingBangsSpacesAndColourCodes() {
        assertEquals("Black Smoothvanilla", PackNames.clean("!  Black Smoothvanilla"));
        assertEquals("Small Held Items v2.0.1", PackNames.clean("! §3§6Small Held Items v2.0.1"));
        assertEquals("MacePvP Essentials [CM]", PackNames.clean("!  §8MacePvP Essentials §3[CM]§8.zip"));
        assertEquals("Living Legend Rewamp", PackNames.clean("!      §5§lLiving Legend Rewamp"));
        assertEquals("O3kar Purple", PackNames.clean("§5§L! O3kar Purple.zip"));
    }

    @Test
    void keepsInnerPunctuationAndOrdinaryNames() {
        assertEquals("DiamondSMP Pack FIXED!", PackNames.clean("DiamondSMP Pack FIXED!"));
        assertEquals("Lavander 1.21.11", PackNames.clean("Lavander 1.21.11"));
        assertEquals("Catto's Overlay", PackNames.clean("Catto's Overlay.zip"));
    }

    @Test
    void neverReturnsEmptyForPunctuationOnlyNames() {
        assertEquals("!!!", PackNames.clean("!!!"));
        assertEquals("", PackNames.clean(null));
    }

    @Test
    void searchIsCaseInsensitiveAndTokenised() {
        assertTrue(PackNames.matches("Crystal PvP LT3 Essentials v25", ""));
        assertTrue(PackNames.matches("Crystal PvP LT3 Essentials v25", "  "));
        assertTrue(PackNames.matches("Crystal PvP LT3 Essentials v25", "crystal"));
        assertTrue(PackNames.matches("Crystal PvP LT3 Essentials v25", "lt3 CRYSTAL"));
        assertFalse(PackNames.matches("Crystal PvP LT3 Essentials v25", "crystal mace"));
        assertTrue(PackNames.matches("! §8MacePvP Essentials", "macepvp"));
        assertFalse(PackNames.matches("! §8MacePvP Essentials", "§8"));
    }
}
