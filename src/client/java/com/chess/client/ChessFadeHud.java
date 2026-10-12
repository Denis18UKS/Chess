package com.chess.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Server-timed full-screen fade used to hide teleport transitions. */
public final class ChessFadeHud {
    private static long startedNanos;
    private static long fadeOutNanos;
    private static long holdNanos;
    private static long fadeInNanos;
    private static boolean active;

    private ChessFadeHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register(ChessFadeHud::render);
    }

    public static void start(int fadeOutTicks, int holdTicks, int fadeInTicks) {
        fadeOutNanos = Math.max(1L, fadeOutTicks) * 50_000_000L;
        holdNanos = Math.max(0L, holdTicks) * 50_000_000L;
        fadeInNanos = Math.max(1L, fadeInTicks) * 50_000_000L;
        startedNanos = System.nanoTime();
        active = true;
    }

    private static void render(DrawContext context, float tickDelta) {
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
        if (elapsed < fadeEnd) {
            alpha = elapsed / (double) fadeOutNanos;
        } else if (elapsed < holdEnd) {
            alpha = 1.0;
        } else {
            alpha = 1.0 - (elapsed - holdEnd) / (double) fadeInNanos;
        }
        int opacity = (int) Math.max(0, Math.min(255, Math.round(alpha * 255.0)));
        if (opacity <= 0) return;
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        context.fill(0, 0, width, height, (opacity << 24));
    }
}
