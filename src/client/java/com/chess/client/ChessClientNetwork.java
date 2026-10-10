package com.chess.client;

import com.chess.ChessFigureBlockEntity;
import com.chess.ChessNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.math.BlockPos;

/** Client-only networking helpers and server state synchronization. */
public final class ChessClientNetwork {
    public static boolean threeDimensional;
    public static boolean developerMode;

    private ChessClientNetwork() {}

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(ChessNetwork.ADMIN_STATE, (client, handler, buf, responseSender) -> {
            int mode = buf.readInt();
            int duration = buf.readInt();
            int rule = buf.readInt();
            client.execute(() -> ChessSettingsScreen.receiveSettings(mode, duration, rule));
        });

        ClientPlayNetworking.registerGlobalReceiver(ChessNetwork.BOARD_STATE, (client, handler, buf, responseSender) -> {
            String cells = buf.readString(64);
            boolean configured = buf.readBoolean();
            boolean running = buf.readBoolean();
            boolean paused = buf.readBoolean();
            boolean whiteTurn = buf.readBoolean();
            String ruleMode = buf.readString(32);
            String matchMode = buf.readString(32);
            BlockPos origin = buf.readBlockPos();
            boolean threeD = buf.readBoolean();
            boolean devMode = buf.readBoolean();
            boolean promotionPending = buf.readBoolean();
            boolean promotionWhite = buf.readBoolean();
            int promotionRow = buf.readInt();
            int promotionCol = buf.readInt();
            String promotionChoices = buf.readString(8);
            String whiteCaptured = buf.readString(32);
            String blackCaptured = buf.readString(32);
            client.execute(() -> {
                threeDimensional = threeD;
                developerMode = devMode;
                ChessBoardScreen.acceptSnapshot(cells, configured, running, paused, whiteTurn, ruleMode, matchMode, origin,
                    threeD, devMode, promotionPending, promotionChoices, whiteCaptured, blackCaptured);
                if (promotionPending) {
                    if (!(client.currentScreen instanceof ChessPromotionScreen)) {
                        client.setScreen(new ChessPromotionScreen(client.currentScreen, promotionChoices,
                            promotionWhite, promotionRow, promotionCol));
                    }
                } else if (client.currentScreen instanceof ChessPromotionScreen promotionScreen) {
                    promotionScreen.closeIfResolved();
                }
                if (threeD && client.currentScreen instanceof ChessBoardScreen) client.setScreen(null);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ChessNetwork.ANIMATE, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String animation = buf.readString(64);
            client.execute(() -> {
                if (client.world != null && client.world.getBlockEntity(pos) instanceof ChessFigureBlockEntity figure) {
                    figure.playAnimation(animation);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ChessNetwork.CLOCK_STATE, (client, handler, buf, responseSender) -> {
            boolean visible = buf.readBoolean();
            boolean ticking = buf.readBoolean();
            long remaining = buf.readLong();
            long maximum = buf.readLong();
            client.execute(() -> ChessClockHud.update(visible, ticking, remaining, maximum));
        });

        ClientPlayNetworking.registerGlobalReceiver(ChessNetwork.BOARD_MOVE_ANIMATION, (client, handler, buf, responseSender) -> {
            int fromRow = buf.readInt();
            int fromCol = buf.readInt();
            int toRow = buf.readInt();
            int toCol = buf.readInt();
            char piece = buf.readChar();
            boolean knight = buf.readBoolean();
            int durationTicks = buf.readInt();
            client.execute(() -> ChessBoardScreen.beginAnimatedMove(fromRow, fromCol, toRow, toCol, piece, knight, durationTicks));
        });

        ClientPlayNetworking.registerGlobalReceiver(ChessNetwork.HIGHLIGHTS, (client, handler, buf, responseSender) -> {
            BlockPos selected = buf.readBlockPos();
            int row = buf.readInt();
            int col = buf.readInt();
            boolean[] legal = new boolean[64];
            for (int i = 0; i < 64; i++) legal[i] = buf.readBoolean();
            client.execute(() -> ChessWorldHighlights.update(selected, row, col, legal));
        });
    }

    public static void requestBoard() {
        if (ClientPlayNetworking.canSend(ChessNetwork.REQUEST_BOARD))
            ClientPlayNetworking.send(ChessNetwork.REQUEST_BOARD, PacketByteBufs.create());
    }

    public static void requestMove(int fr, int fc, int tr, int tc) {
        var buf = PacketByteBufs.create();
        buf.writeInt(fr); buf.writeInt(fc); buf.writeInt(tr); buf.writeInt(tc);
        ClientPlayNetworking.send(ChessNetwork.MOVE, buf);
    }

    public static void select3D(BlockPos pos) {
        var buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(ChessNetwork.SELECT_3D, buf);
    }

    public static void move3D(BlockPos pos) {
        var buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(ChessNetwork.MOVE_3D, buf);
    }

    public static void switchDevTeam() {
        ClientPlayNetworking.send(ChessNetwork.DEV_SWITCH_TEAM, PacketByteBufs.create());
    }

    public static void choosePromotion(char piece) {
        var buf = PacketByteBufs.create();
        buf.writeString(String.valueOf(piece), 4);
        ClientPlayNetworking.send(ChessNetwork.PROMOTION, buf);
    }
}
