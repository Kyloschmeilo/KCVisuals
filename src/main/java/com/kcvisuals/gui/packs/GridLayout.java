package com.kcvisuals.gui.packs;

/**
 * Fixed-size cell grid fitted into a given width: as many columns as fit, the block centred horizontally.
 * Coordinates are relative to the grid area's top-left corner. Pure Java, unit tested.
 */
public record GridLayout(int columns, int cellW, int cellH, int gapX, int gapY, int offsetX) {
    /** Categories whose tiles show two previews side by side (end crystal: placed and item). */
    private static final java.util.Set<String> DOUBLE_WIDTH = java.util.Set.of("end_crystal");

    public static boolean isDoubleWidth(String categoryId) {
        return categoryId != null && DOUBLE_WIDTH.contains(categoryId);
    }

    /** {@link #fit} with the category's tile width: {@code tile}, or two tiles plus a gap for double-width categories. */
    public static GridLayout forCategory(int width, String categoryId, int tile, int cellH, int gap) {
        int cellW = isDoubleWidth(categoryId) ? 2 * tile + gap : tile;
        return fit(width, cellW, cellH, gap, gap);
    }

    public static GridLayout fit(int width, int cellW, int cellH, int gapX, int gapY) {
        int cols = Math.max(1, (width + gapX) / (cellW + gapX));
        int used = cols * cellW + (cols - 1) * gapX;
        return new GridLayout(cols, cellW, cellH, gapX, gapY, Math.max(0, (width - used) / 2));
    }

    public int cellX(int index) {
        return offsetX + (index % columns) * (cellW + gapX);
    }

    public int cellY(int index) {
        return (index / columns) * (cellH + gapY);
    }

    public int rows(int count) {
        return count <= 0 ? 0 : (count + columns - 1) / columns;
    }

    /** Height needed for {@code count} cells (no trailing gap). */
    public int contentHeight(int count) {
        int r = rows(count);
        return r == 0 ? 0 : r * cellH + (r - 1) * gapY;
    }

    /** Index of the cell under (x, y), or -1 when the point is in a gap, outside, or past the last cell. */
    public int indexAt(double x, double y, int count) {
        double lx = x - offsetX;
        if (lx < 0 || y < 0) {
            return -1;
        }
        int col = (int) (lx / (cellW + gapX));
        int row = (int) (y / (cellH + gapY));
        if (col >= columns || lx - col * (cellW + gapX) >= cellW || y - row * (cellH + gapY) >= cellH) {
            return -1;
        }
        int idx = row * columns + col;
        return idx < count ? idx : -1;
    }
}
