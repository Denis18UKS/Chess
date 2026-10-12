package com.chess.client;

import net.minecraft.client.MinecraftClient;

/**
 * Turn information is shown in the server-synchronized action bar now.
 * This class remains as a network-state compatibility shim; it no longer draws
 * a second centre-screen "Ход белых/чёрных" box over the world.
 */
public final class ChessSpectatorHud {
    private static boolean watching;
    private static boolean whiteTurn = true;

    private ChessSpectatorHud() {}

    public static void register() {
        // Deliberately no HudRenderCallback: turn state belongs in the action bar.
    }

    public static void update(boolean isWatching, boolean whiteToMove) {
        watching = isWatching;
        whiteTurn = whiteToMove;
    }

    public static boolean isWatching() { return watching; }
    public static boolean isWhiteTurn() { return whiteTurn; }
}
