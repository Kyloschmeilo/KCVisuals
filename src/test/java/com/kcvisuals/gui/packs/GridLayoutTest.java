package com.kcvisuals.gui.packs;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GridLayoutTest {
    @Test
    void fitsAsManyColumnsAsPossibleAndCentres() {
        GridLayout g = GridLayout.fit(300, 48, 60, 6, 6);
        assertEquals(5, g.columns()); // 5*48 + 4*6 = 264 <= 300, 6 columns need 318
        assertEquals((300 - 264) / 2, g.offsetX());
        assertEquals(1, GridLayout.fit(10, 48, 60, 6, 6).columns());
        assertEquals(1, GridLayout.fit(48, 48, 60, 6, 6).columns());
        assertEquals(2, GridLayout.fit(102, 48, 60, 6, 6).columns());
    }

    @Test
    void cellPositionsAndContentHeight() {
        GridLayout g = GridLayout.fit(264, 48, 60, 6, 6);
        assertEquals(0, g.offsetX());
        assertEquals(0, g.cellX(0));
        assertEquals(54, g.cellX(1));
        assertEquals(0, g.cellX(5));
        assertEquals(66, g.cellY(5));
        assertEquals(0, g.contentHeight(0));
        assertEquals(60, g.contentHeight(5));
        assertEquals(126, g.contentHeight(6));
        assertEquals(2, g.rows(6));
    }

    @Test
    void hitTestingIgnoresGapsAndMissingCells() {
        GridLayout g = GridLayout.fit(264, 48, 60, 6, 6);
        assertEquals(0, g.indexAt(1, 1, 7));
        assertEquals(1, g.indexAt(54, 10, 7));
        assertEquals(-1, g.indexAt(50, 10, 7)); // horizontal gap
        assertEquals(-1, g.indexAt(10, 62, 7)); // vertical gap
        assertEquals(6, g.indexAt(60, 70, 7));
        assertEquals(-1, g.indexAt(120, 70, 7)); // past the last cell
        assertEquals(-1, g.indexAt(-1, 5, 7));
        assertEquals(-1, g.indexAt(264 + 6, 5, 7));
    }
}
