package com.kcvisuals.preview;

/** Minimal ARGB raster ({@code 0xAARRGGBB}, row-major). No Minecraft classes, so previews can be rendered and tested anywhere. */
public final class ArgbImage {
    private final int width;
    private final int height;
    private final int[] pixels;

    public ArgbImage(int width, int height) {
        this(width, height, new int[Math.multiplyExact(width, height)]);
    }

    public ArgbImage(int width, int height, int[] pixels) {
        if (width <= 0 || height <= 0 || pixels.length != width * height) {
            throw new IllegalArgumentException("Bad image size " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** The backing array (row-major); writes go straight into the image. */
    public int[] pixels() {
        return pixels;
    }

    public int get(int x, int y) {
        return pixels[y * width + x];
    }

    public void set(int x, int y, int argb) {
        pixels[y * width + x] = argb;
    }

    /** Nearest-neighbour sample at normalised (u, v); coordinates outside [0, 1] are clamped to the edge. */
    public int sample(double u, double v) {
        int x = (int) Math.floor(u * width);
        int y = (int) Math.floor(v * height);
        x = x < 0 ? 0 : Math.min(width - 1, x);
        y = y < 0 ? 0 : Math.min(height - 1, y);
        return pixels[y * width + x];
    }

    /**
     * Copy of the region (x, y, w, h), shrunk with nearest-neighbour sampling so its longer side is at most
     * {@code max} (never enlarged).
     */
    public ArgbImage region(int x, int y, int w, int h, int max) {
        int longer = Math.max(w, h);
        int ow = w, oh = h;
        if (longer > max) {
            ow = Math.max(1, (int) Math.round(w * (double) max / longer));
            oh = Math.max(1, (int) Math.round(h * (double) max / longer));
        }
        ArgbImage out = new ArgbImage(ow, oh);
        for (int oy = 0; oy < oh; oy++) {
            int sy = y + (int) ((long) oy * h / oh);
            for (int ox = 0; ox < ow; ox++) {
                int sx = x + (int) ((long) ox * w / ow);
                out.pixels[oy * ow + ox] = pixels[sy * width + sx];
            }
        }
        return out;
    }
}
