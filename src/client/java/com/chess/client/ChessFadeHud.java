package com.chess.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Client-side fade transition. The global mixin calls renderOverlay after all GUI and HUD
 * elements have been drawn, so the fade is still visible when F1 hides the HUD and when
 * the chat or another screen is open.
 */
public final class ChessFadeHud {
    private static long startedNanos;
    private static long fadeOutNanos;
    private static long holdNanos;
    private static long fadeInNanos;
    private static boolean active;

    private ChessFadeHud() {}

    // Preserve the existing initializer API; the GlobalFadeOverlayMixin owns rendering.
    public static void register() {}

    public static void start(int fadeOutTicks, int holdTicks, int fadeInTicks) {
        fadeOutNanos = Math.max(1L, fadeOutTicks) * 50_000_000L;
        holdNanos = Math.max(0L, holdTicks) * 50_000_000L;
        fadeInNanos = Math.max(1L, fadeInTicks) * 50_000_000L;
        startedNanos = System.nanoTime();
        active = true;
    }

    /** Compatibility bridge for an older, now-disabled HUD mixin. */
    @Deprecated
    public static void renderHudOverlay(DrawContext context) {
        renderOverlay(context);
    }

    public static void renderOverlay(DrawContext context) {
        if (!active) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            active = false;
            return;
        }
        long elapsed = Math.max(0L, System.nanoTime() - startedNanos);
        long fadeEnd = fadeOutNanos;
        long holdEnd = fadeEnd + holdNanos;
        long total = holdEnd + fadeInNanos;
        if (elapsed >= total) {
            active = false;
            return;
        }
        double alpha;
        if (elapsed < fadeEnd) alpha = elapsed / (double) fadeOutNanos;
        else if (elapsed < holdEnd) alpha = 1.0;
        else alpha = 1.0 - (elapsed - holdEnd) / (double) fadeInNanos;
        int opacity = (int)Math.max(0, Math.min(255, Math.round(alpha * 255.0)));
        if (opacity <= 0) return;
        context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(),
            (opacity << 24) | 0x000000);
    }
}
