package com.kcvisuals.gui.widget;

import static org.junit.jupiter.api.Assertions.*;

import com.kcvisuals.gui.Theme;
import org.junit.jupiter.api.Test;

class WidgetMathTest {
    @Test
    void roundedInsetsAreSymmetricAndZeroInTheMiddle() {
        int h = 40, r = 8;
        assertTrue(Shapes.insetAt(0, h, r) > 0);
        for (int row = 0; row < h; row++) {
            assertEquals(Shapes.insetAt(row, h, r), Shapes.insetAt(h - 1 - row, h, r), "row " + row);
        }
        for (int row = r; row < h - r; row++) {
            assertEquals(0, Shapes.insetAt(row, h, r));
        }
        for (int row = 1; row < r; row++) {
            assertTrue(Shapes.insetAt(row, h, r) <= Shapes.insetAt(row - 1, h, r));
        }
        assertEquals(0, Shapes.insetAt(5, 10, 0));
    }

    @Test
    void radiusIsClampedToHalfTheShortSide() {
        assertEquals(5, Shapes.clampRadius(20, 10, 40));
        assertEquals(3, Shapes.clampRadius(3, 10, 40));
        assertEquals(0, Shapes.clampRadius(-1, 10, 40));
    }

    @Test
    void scrollClampAndThumb() {
        assertEquals(0, ScrollPanel.maxScroll(50, 100));
        assertEquals(100, ScrollPanel.maxScroll(200, 100));
        assertEquals(100, ScrollPanel.clamp(500, 200, 100));
        assertEquals(0, ScrollPanel.clamp(-5, 200, 100));
        assertArrayEquals(new int[] {0, 100}, ScrollPanel.thumb(0, 50, 100));
        int[] top = ScrollPanel.thumb(0, 200, 100);
        int[] bottom = ScrollPanel.thumb(100, 200, 100);
        assertEquals(0, top[0]);
        assertEquals(50, top[1]);
        assertEquals(100, bottom[0] + bottom[1]);
        assertTrue(ScrollPanel.thumb(0, 100000, 100)[1] >= 16);
    }

    @Test
    void sliderSnapping() {
        assertEquals(0.5, Slider.snap(0.52, 0, 1, 0.1), 1e-9);
        assertEquals(1.0, Slider.snap(3, 0, 1, 0.1), 1e-9);
        assertEquals(0.0, Slider.snap(-3, 0, 1, 0), 1e-9);
        assertEquals(0.37, Slider.snap(0.37, 0, 1, 0), 1e-9);
    }

    @Test
    void hsvRoundTrip() {
        for (int rgb : new int[] {0xFF9B5CFF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF808080, 0xFF000000, 0xFFFFFFFF}) {
            float[] hsv = ColorPicker.rgbToHsv(rgb);
            int back = ColorPicker.hsvToRgb(hsv[0], hsv[1], hsv[2]);
            for (int shift = 0; shift <= 16; shift += 8) {
                assertTrue(Math.abs(((rgb >> shift) & 0xFF) - ((back >> shift) & 0xFF)) <= 1,
                        Integer.toHexString(rgb) + " -> " + Integer.toHexString(back));
            }
        }
    }

    @Test
    void themeMix() {
        assertEquals(0xFF000000, Theme.mix(0xFF000000, 0xFFFFFFFF, 0f));
        assertEquals(0xFFFFFFFF, Theme.mix(0xFF000000, 0xFFFFFFFF, 1f));
        assertEquals(0x80808080, Theme.mix(0x00000000, 0xFFFFFFFF, 0.5f));
    }
}
