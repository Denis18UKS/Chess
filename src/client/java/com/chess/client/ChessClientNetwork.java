package com.chess.client;

import com.chess.ChessFigureBlockEntity;
import com.chess.ChessNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.math.BlockPos;

/** Client-only networking helpers and server state synchronization. */
public final class ChessClientNetwork {
    private ChessClientNetwork() {}

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(ChessNetwork.BOARD_STATE, (client, handler, buf, responseSender) -> {
            String cells = buf.readString(64);
            boolean configured = buf.readBoolean();
            boolean running = buf.readBoolean();
            boolean paused = buf.readBoolean();
            boolean whiteTurn = buf.readBoolean();
            String ruleMode = buf.readString(32);
            String matchMode = buf.readString(32);
            BlockPos origin = buf.readBlockPos();
            client.execute(() -> ChessBoardScreen.acceptSnapshot(cells, configured, running, paused, whiteTurn, ruleMode, matchMode, origin));
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
    }

    public static void requestBoard() {
        ClientPlayNetworking.send(ChessNetwork.REQUEST_BOARD, PacketByteBufs.create());
    }

    public static void requestMove(int fr, int fc, int tr, int tc) {
        var buf = PacketByteBufs.create();
        buf.writeInt(fr); buf.writeInt(fc); buf.writeInt(tr); buf.writeInt(tc);
        ClientPlayNetworking.send(ChessNetwork.MOVE, buf);
    }
}
