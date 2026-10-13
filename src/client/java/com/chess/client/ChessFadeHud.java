package com.chess.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Full-screen teleport transition. The outer MinecraftClient mixin renders the black layer
 * once per frame, after the world, HUD, chat and every screen, so it cannot be clipped by UI.
 */
public final class ChessFadeHud {
    private static long startedNanos;
    private static long fadeOutNanos;
    private static long holdNanos;
    private static long fadeInNanos;
    private static boolean active;
    private static boolean hadHudHidden;
    private static boolean hudStateSaved;

    private ChessFadeHud() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ChessFadeHud::onClientTick);
    }

    public static boolean isActive() {
        return active;
    }

    // Compatibility stubs for legacy mixin classes kept in source but no longer registered.
    // The sole active renderer is GlobalFadeOverlayMixin, which draws above every other layer.
    @Deprecated
    public static void beginRenderFrame() {}

    @Deprecated
    public static boolean wasHudOverlayDrawnThisFrame() {
        return false;
    }

    @Deprecated
    public static void renderHudOverlay(DrawContext context) {}

    private static void onClientTick(MinecraftClient client) {
        if (active && client != null && client.options != null) {
            // F1 must not reveal the HUD or change the state to which we restore later.
            client.options.hudHidden = true;
        }
    }

    public static void start(int fadeOutTicks, int holdTicks, int fadeInTicks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active && client != null && client.options != null) {
            hadHudHidden = client.options.hudHidden;
            hudStateSaved = true;
        }

        fadeOutNanos = Math.max(1L, fadeOutTicks) * 50_000_000L;
        holdNanos = Math.max(0L, holdTicks) * 50_000_000L;
        fadeInNanos = Math.max(1L, fadeInTicks) * 50_000_000L;
        startedNanos = System.nanoTime();
        active = true;

        if (client != null) {
            if (client.options != null) client.options.hudHidden = true;
            // Close chat/configuration screens immediately; setScreen is also locked
            // for the entire transition so another screen cannot cover the fade.
            if (client.currentScreen != null) client.setScreen(null);
        }
    }

    private static void finish(MinecraftClient client) {
        active = false;
        if (hudStateSaved && client != null && client.options != null)
            client.options.hudHidden = hadHudHidden;
        hudStateSaved = false;
    }

    public static void renderOverlay(DrawContext context) {
        if (!active) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            finish(client);
            return;
        }

        // Render-time guard closes the short gap between key input and the next client tick.
        if (client.options != null) client.options.hudHidden = true;

        long elapsed = Math.max(0L, System.nanoTime() - startedNanos);
        long fadeEnd = fadeOutNanos;
        long holdEnd = fadeEnd + holdNanos;
        long total = holdEnd + fadeInNanos;
        if (elapsed >= total) {
            finish(client);
            return;
        }

        double alpha;
        if (elapsed < fadeEnd) alpha = elapsed / (double) fadeOutNanos;
        else if (elapsed < holdEnd) alpha = 1.0;
        else alpha = 1.0 - (elapsed - holdEnd) / (double) fadeInNanos;
        int opacity = (int)Math.max(0, Math.min(255, Math.round(alpha * 255.0)));
        if (opacity <= 0) return;

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(),
            (opacity << 24) | 0x000000);
    }
}
