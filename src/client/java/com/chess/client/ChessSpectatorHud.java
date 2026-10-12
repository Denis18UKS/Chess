package com.chess.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Minimal HUD indicator driven by authoritative server turn state while spectating. */
public final class ChessSpectatorHud {
    private static boolean visible;
    private static boolean whiteTurn = true;

    private ChessSpectatorHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register(ChessSpectatorHud::render);
    }

    public static void update(boolean watching, boolean whiteToMove) {
        visible = watching;
        whiteTurn = whiteToMove;
    }

    private static void render(DrawContext context, float tickDelta) {
        if (!visible) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null || client.textRenderer == null) {
            visible = false;
            return;
        }
        String label = whiteTurn ? "Ход белых" : "Ход чёрных";
        int color = whiteTurn ? 0xFFFFFFFF : 0xFFB0B0B0;
        int x = client.getWindow().getScaledWidth() / 2;
        int y = Math.max(8, client.getWindow().getScaledHeight() / 2 + 28);
        context.fill(x - 54, y - 3, x + 54, y + 13, 0xA0000000);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), x, y, color);
    }
}
