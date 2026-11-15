package com.kcvisuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

/** Toast notifications. Call on the client thread. */
public final class Toasts {
    /** Progress → result of an apply; same type so the result replaces the progress toast in place. */
    public static final SystemToast.Type APPLY = new SystemToast.Type(5000L);
    public static final SystemToast.Type MISSING = new SystemToast.Type(8000L);
    public static final SystemToast.Type SKIPPED = new SystemToast.Type(6000L);
    public static final SystemToast.Type DROPPED = new SystemToast.Type(8000L);
    public static final SystemToast.Type INFO = new SystemToast.Type(4000L);

    private Toasts() {}

    /** Shows or updates the single toast of {@code type} (one-line description). */
    public static void show(SystemToast.Type type, String title, String description) {
        SystemToast.show(MinecraftClient.getInstance().getToastManager(), type, Text.literal(title),
                description == null ? null : Text.literal(description));
    }

    /** A new toast whose description wraps over several lines (for lists of names). */
    public static void multiline(SystemToast.Type type, String title, String description) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SystemToast.hide(mc.getToastManager(), type);
        mc.getToastManager().add(SystemToast.create(mc, type, Text.literal(title), Text.literal(description)));
    }
}
