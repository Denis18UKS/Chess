package com.chess;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class ChessNetwork {
    public static final Identifier REQUEST_BOARD = ChessMod.id("board_request");
    public static final Identifier BOARD_STATE = ChessMod.id("board_state");
    public static final Identifier MOVE = ChessMod.id("move");
    public static final Identifier ANIMATE = ChessMod.id("animate_piece");
    public static final Identifier GRAFFITI_CREATE = ChessMod.id("graffiti_create");

    private ChessNetwork() {}

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_BOARD, (server, player, handler, buf, responseSender) ->
            server.execute(() -> sendBoard(player)));
        ServerPlayNetworking.registerGlobalReceiver(MOVE, (server, player, handler, buf, responseSender) -> {
            int fr = buf.readInt(), fc = buf.readInt(), tr = buf.readInt(), tc = buf.readInt();
            server.execute(() -> ChessGameManager.tryMove(player, fr, fc, tr, tc));
        });
        ServerPlayNetworking.registerGlobalReceiver(GRAFFITI_CREATE, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String text = buf.readString(256);
            float scale = buf.readFloat();
            int rgb = buf.readInt();
            float yaw = buf.readFloat();
            server.execute(() -> {
                boolean hasTool = player.getMainHandStack().isOf(ModItems.CHESS_GRAFFITI_TOOL)
                    || player.getOffHandStack().isOf(ModItems.CHESS_GRAFFITI_TOOL);
                if (!hasTool || player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
                ChessGraffitiToolItem.placeText(player, pos, text, scale, rgb, yaw);
            });
        });
    }

    public static void sendBoard(ServerPlayerEntity player) {
        World world = player.getWorld();
        ChessGameManager.BoardState state = ChessGameManager.board(world);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(ChessGameManager.serializeBoard(world));
        buf.writeBoolean(state.configured);
        buf.writeBoolean(state.running);
        buf.writeBoolean(state.paused);
        buf.writeBoolean(state.whiteTurn);
        buf.writeString(state.ruleMode.name());
        buf.writeString(state.matchMode.name());
        buf.writeBlockPos(state.origin);
        ServerPlayNetworking.send(player, BOARD_STATE, buf);
    }

    public static void broadcastAnimation(ServerWorld world, BlockPos pos, String animation) {
        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeString(animation, 64);
            ServerPlayNetworking.send(player, ANIMATE, buf);
        }
    }
}
