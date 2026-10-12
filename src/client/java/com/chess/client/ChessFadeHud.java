package com.chess.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

/** Server-timed full-screen fade drawn after the HUD and again above the chat screen. */
public final class ChessFadeHud {
    private static long startedNanos;
    private static long fadeOutNanos;
    private static long holdNanos;
    private static long fadeInNanos;
    private static boolean active;

    private ChessFadeHud() {}

    // Kept as a no-op for the existing client initializer; rendering is injected at render TAIL
    // so F1 and the chat screen cannot suppress or cover the transition.
    public static void register() {}

    public static void start(int fadeOutTicks, int holdTicks, int fadeInTicks) {
        fadeOutNanos = Math.max(1L, fadeOutTicks) * 50_000_000L;
        holdNanos = Math.max(0L, holdTicks) * 50_000_000L;
        fadeInNanos = Math.max(1L, fadeInTicks) * 50_000_000L;
        startedNanos = System.nanoTime();
        active = true;
    }

    /** Called at the very end of HUD rendering. The regular HUD may be hidden by F1, but this hook still runs. */
    public static void renderHudOverlay(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen instanceof ChatScreen) return;
        renderOverlay(context);
    }

    /** Draw above chat when chat is open, instead of letting chat panels cut through the fade. */
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
        int opacity = (int) Math.max(0, Math.min(255, Math.round(alpha * 255.0)));
        if (opacity <= 0) return;
        context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(),
            (opacity << 24));
    }
}
