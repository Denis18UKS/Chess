package com.chess.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Draws the chess clock over Minecraft's normal experience-bar position without changing player XP. */
public final class ChessClockHud {
    private static final Identifier GUI_ICONS = new Identifier("minecraft", "textures/gui/icons.png");
    private static boolean visible;
    private static boolean ticking;
    private static long remainingTicks;
    private static long maximumTicks = 12000L;
    private static long updateNanos;

    private ChessClockHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register(ChessClockHud::render);
    }

    public static void update(boolean shouldShow, boolean shouldTick, long remaining, long maximum) {
        visible = shouldShow;
        ticking = shouldTick;
        remainingTicks = Math.max(0L, remaining);
        maximumTicks = Math.max(1L, maximum);
        updateNanos = System.nanoTime();
    }

    private static void render(DrawContext context, float tickDelta) {
        if (!visible) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.textRenderer == null) return;

        long elapsedTicks = ticking ? Math.max(0L, (System.nanoTime() - updateNanos) / 50_000_000L) : 0L;
        long leftTicks = Math.max(0L, remainingTicks - elapsedTicks);
        long seconds = (leftTicks + 19L) / 20L;
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int x = screenWidth / 2 - 91;
        int y = screenHeight - 29;

        // The vanilla XP bar background and fill sprites are reused, but XP data itself is untouched.
        context.drawTexture(GUI_ICONS, x, y, 0, 64, 182, 5);
        int fillWidth = (int)Math.round(182.0 * Math.min(1.0, leftTicks / (double)maximumTicks));
        if (fillWidth > 0) context.drawTexture(GUI_ICONS, x, y, 0, 69, fillWidth, 5);

        String timer = String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
        context.fill(screenWidth / 2 - 24, y - 13, screenWidth / 2 + 24, y - 2, 0xB0000000);
        int color = seconds <= 30 ? 0xFFFF5555 : 0xFFFFFFFF;
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(timer), screenWidth / 2, y - 12, color);
    }
}
