package com.kcvisuals.preview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Something renderable as a preview: quads in view space (x right, y up, z towards the viewer, orthographic) for each
 * of {@link #FRAMES} turn steps. Frame 0 is the static pose; the other frames turn the model around its vertical axis
 * for the hover animation. One framing (scale and centre) is computed over all frames so the strip does not jitter.
 */
public final class Scene {
    /** Frames of the hover turn (one full revolution). */
    public static final int FRAMES = 24;
    /** View of the placed crystal: looking down a little, between two sides. */
    static final double CRYSTAL_PITCH = 22, CRYSTAL_YAW = 45, CRYSTAL_SPIN = 20;

    private final IntFunction<List<Quad>> frames;
    /** True: one block unit spans the image, centred (vanilla slot framing), shrunk only if the model overflows. */
    private final boolean slotFramed;
    private double scale = Double.NaN, centreX, centreY;

    private Scene(IntFunction<List<Quad>> frames, boolean slotFramed) {
        this.frames = frames;
        this.slotFramed = slotFramed;
    }

    /**
     * A 3D item/block model in its {@code display.gui} pose (identity when it has none, as in vanilla). Faces whose
     * texture is unresolved or missing from {@code textures} draw the missing-texture checker.
     */
    public static Scene item(ResolvedModel model, Function<String, ArgbImage> textures) {
        List<Quad> base = itemQuads(model, textures);
        Mat4 pose = (model.gui() == null ? GuiTransform.IDENTITY : model.gui()).matrix();
        Mat4 centre = Mat4.translation(-0.5, -0.5, -0.5).mul(Mat4.scaling(1 / 16.0, 1 / 16.0, 1 / 16.0));
        return new Scene(f -> {
            Mat4 m = pose.mul(Mat4.rotationY(f * 360.0 / FRAMES)).mul(centre);
            List<Quad> out = new ArrayList<>(base.size());
            for (Quad q : base) {
                out.add(q.transformed(m));
            }
            return out;
        }, true);
    }

    /** The placed end crystal (with base) textured with {@code tex}; frames spin the glass like in game. */
    public static Scene endCrystal(ArgbImage tex) {
        Mat4 view = Mat4.rotationX(CRYSTAL_PITCH).mul(Mat4.rotationY(CRYSTAL_YAW));
        return new Scene(f -> {
            List<Quad> out = new ArrayList<>();
            for (Quad q : EndCrystalGeometry.quads(tex, CRYSTAL_SPIN + f * 360.0 / FRAMES)) {
                out.add(q.transformed(view));
            }
            return out;
        }, false);
    }

    /**
     * A flat item icon: the layers drawn over each other at the size of the largest one (nearest-neighbour). Null if
     * there is no layer.
     */
    public static ArgbImage flat(List<ArgbImage> layers) {
        int w = 0, h = 0;
        for (ArgbImage l : layers) {
            if (l != null) {
                w = Math.max(w, l.width());
                h = Math.max(h, l.height());
            }
        }
        if (w == 0) {
            return null;
        }
        ArgbImage out = new ArgbImage(w, h);
        int[] px = out.pixels();
        for (ArgbImage l : layers) {
            if (l == null) {
                continue;
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int s = l.get(x * l.width() / w, y * l.height() / h);
                    int a = s >>> 24;
                    if (a == 255 || (px[y * w + x] >>> 24) == 0) {
                        if (a > 0) {
                            px[y * w + x] = s;
                        }
                    } else if (a > 0) {
                        px[y * w + x] = over(s, px[y * w + x]);
                    }
                }
            }
        }
        return out;
    }

    private static int over(int s, int d) {
        float sa = (s >>> 24) / 255f, da = (d >>> 24) / 255f;
        float oa = sa + da * (1 - sa);
        int r = Math.round((((s >> 16) & 0xFF) * sa + ((d >> 16) & 0xFF) * da * (1 - sa)) / oa);
        int g = Math.round((((s >> 8) & 0xFF) * sa + ((d >> 8) & 0xFF) * da * (1 - sa)) / oa);
        int b = Math.round(((s & 0xFF) * sa + (d & 0xFF) * da * (1 - sa)) / oa);
        return Math.round(oa * 255) << 24 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    /** Frame {@code frame} (0 = static pose) as a {@code size}x{@code size} image. */
    public ArgbImage render(int size, int frame) {
        ArgbImage img = new ArgbImage(size, size);
        draw(img, size, frame);
        return img;
    }

    /** All {@link #FRAMES} frames stacked vertically: {@code size} wide, {@code size * FRAMES} high. */
    public ArgbImage strip(int size) {
        ArgbImage strip = new ArgbImage(size, size * FRAMES);
        ArgbImage frame = new ArgbImage(size, size);
        for (int f = 0; f < FRAMES; f++) {
            java.util.Arrays.fill(frame.pixels(), 0);
            draw(frame, size, f);
            System.arraycopy(frame.pixels(), 0, strip.pixels(), f * size * size, size * size);
        }
        return strip;
    }

    private void draw(ArgbImage img, int size, int frame) {
        fit();
        Rasterizer r = new Rasterizer(img);
        double k = scale * size;
        double half = size / 2.0;
        for (Quad q : frames.apply(frame)) {
            float[] p = q.pos();
            float[] s = new float[12];
            for (int i = 0; i < 4; i++) {
                s[i * 3] = (float) (half + (p[i * 3] - centreX) * k);
                s[i * 3 + 1] = (float) (half - (p[i * 3 + 1] - centreY) * k);
                s[i * 3 + 2] = p[i * 3 + 2];
            }
            r.draw(new Quad(s, q.uv(), q.tex(), q.shade(), q.cull(), q.translucent()));
        }
    }

    /** Framing shared by all frames: image units per view unit and the view point at the image centre. */
    private void fit() {
        if (!Double.isNaN(scale)) {
            return;
        }
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        for (int f = 0; f < FRAMES; f++) {
            for (Quad q : frames.apply(f)) {
                float[] p = q.pos();
                for (int i = 0; i < 4; i++) {
                    minX = Math.min(minX, p[i * 3]);
                    maxX = Math.max(maxX, p[i * 3]);
                    minY = Math.min(minY, p[i * 3 + 1]);
                    maxY = Math.max(maxY, p[i * 3 + 1]);
                }
            }
        }
        if (minX > maxX) {
            scale = 1;
            return;
        }
        if (slotFramed) {
            double extent = Math.max(Math.max(-minX, maxX), Math.max(-minY, maxY));
            scale = extent > 0.5 ? 0.5 / extent : 1;
            centreX = centreY = 0;
        } else {
            double span = Math.max(maxX - minX, maxY - minY);
            scale = span <= 0 ? 1 : 0.94 / span;
            centreX = (minX + maxX) / 2;
            centreY = (minY + maxY) / 2;
        }
    }

    /** Model-pixel quads (0..16 space) of the elements, textures looked up once per id. */
    static List<Quad> itemQuads(ResolvedModel model, Function<String, ArgbImage> textures) {
        Map<String, ArgbImage> cache = new HashMap<>();
        List<Quad> out = new ArrayList<>();
        for (ModelElement e : model.elements()) {
            Mat4 rot = elementRotation(e.rotation());
            for (Map.Entry<Direction, ModelElement.Face> fe : e.faces().entrySet()) {
                Direction d = fe.getKey();
                ModelElement.Face face = fe.getValue();
                String texId = model.texture(face.texture());
                ArgbImage tex = texId == null ? null : cache.computeIfAbsent(texId, id -> textures.apply(id));
                float[] pos = corners(d, e.from(), e.to());
                float[] uvBox = face.uv() != null ? face.uv() : defaultUv(d, e.from(), e.to());
                float[] uv = new float[8];
                for (int i = 0; i < 4; i++) {
                    int corner = (i + face.rotation() / 90) % 4;
                    uv[i * 2] = (corner == 0 || corner == 1 ? uvBox[0] : uvBox[2]) / 16f;
                    uv[i * 2 + 1] = (corner == 0 || corner == 3 ? uvBox[1] : uvBox[3]) / 16f;
                }
                Quad q = new Quad(pos, uv, tex, e.shade() ? d.shade() : 1f, true, true);
                out.add(rot == null ? q : q.transformed(rot));
            }
        }
        return out;
    }

    /** Rotation about the origin; with rescale the two other axes are stretched by 1/cos(angle) like vanilla. */
    private static Mat4 elementRotation(ModelElement.Rotation r) {
        if (r == null || r.angle() == 0) {
            return null;
        }
        float[] o = r.origin();
        Mat4 rot = switch (r.axis()) {
            case 0 -> Mat4.rotationX(r.angle());
            case 1 -> Mat4.rotationY(r.angle());
            default -> Mat4.rotationZ(r.angle());
        };
        Mat4 scale = Mat4.identity();
        if (r.rescale()) {
            double c = Math.cos(Math.toRadians(r.angle()));
            double s = Math.abs(c) < 1e-3 ? 1 : 1 / Math.abs(c);
            scale = Mat4.scaling(r.axis() == 0 ? 1 : s, r.axis() == 1 ? 1 : s, r.axis() == 2 ? 1 : s);
        }
        return Mat4.translation(o[0], o[1], o[2]).mul(scale).mul(rot).mul(Mat4.translation(-o[0], -o[1], -o[2]));
    }

    /** Face corners in vanilla's order (counter-clockwise seen from outside); uv corner i belongs to vertex i. */
    static float[] corners(Direction d, float[] from, float[] to) {
        float x0 = Math.min(from[0], to[0]), y0 = Math.min(from[1], to[1]), z0 = Math.min(from[2], to[2]);
        float x1 = Math.max(from[0], to[0]), y1 = Math.max(from[1], to[1]), z1 = Math.max(from[2], to[2]);
        return switch (d) {
            case DOWN -> new float[] {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1};
            case UP -> new float[] {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0};
            case NORTH -> new float[] {x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0};
            case SOUTH -> new float[] {x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1};
            case WEST -> new float[] {x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1};
            case EAST -> new float[] {x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0};
        };
    }

    /** Vanilla's uv for a face without an explicit {@code uv}: the element bounds projected onto the face. */
    static float[] defaultUv(Direction d, float[] from, float[] to) {
        float x0 = Math.min(from[0], to[0]), y0 = Math.min(from[1], to[1]), z0 = Math.min(from[2], to[2]);
        float x1 = Math.max(from[0], to[0]), y1 = Math.max(from[1], to[1]), z1 = Math.max(from[2], to[2]);
        return switch (d) {
            case DOWN -> new float[] {x0, 16 - z1, x1, 16 - z0};
            case UP -> new float[] {x0, z0, x1, z1};
            case NORTH -> new float[] {16 - x1, 16 - y1, 16 - x0, 16 - y0};
            case SOUTH -> new float[] {x0, 16 - y1, x1, 16 - y0};
            case WEST -> new float[] {z0, 16 - y1, z1, 16 - y0};
            case EAST -> new float[] {16 - z1, 16 - y1, 16 - z0, 16 - y0};
        };
    }
}
