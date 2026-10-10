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
    public static final Identifier BOARD_MOVE_ANIMATION = ChessMod.id("board_move_animation");
    public static final Identifier GRAFFITI_CREATE = ChessMod.id("graffiti_create");
    public static final Identifier SELECT_3D = ChessMod.id("select_3d_piece");
    public static final Identifier MOVE_3D = ChessMod.id("move_3d_piece");
    public static final Identifier HIGHLIGHTS = ChessMod.id("move_highlights");
    public static final Identifier DEV_SWITCH_TEAM = ChessMod.id("dev_switch_team");
    public static final Identifier PROMOTION = ChessMod.id("promotion_choice");
    public static final Identifier CLOCK_STATE = ChessMod.id("clock_state");
    public static final Identifier ADMIN_ACTION = ChessMod.id("admin_action");
    public static final Identifier ADMIN_STATE = ChessMod.id("admin_state");

    private ChessNetwork() {}

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_BOARD, (server, player, handler, buf, responseSender) ->
            server.execute(() -> sendBoard(player)));
        ServerPlayNetworking.registerGlobalReceiver(ADMIN_ACTION, (server, player, handler, buf, responseSender) -> {
            String action = buf.readString(64);
            BlockPos pos = buf.readBlockPos();
            String a = buf.readString(1024), b = buf.readString(1024), c = buf.readString(1024);
            int v1 = buf.readInt(), v2 = buf.readInt(), v3 = buf.readInt();
            server.execute(() -> ChessGameManager.handleAdminAction(player, action, pos, a, b, c, v1, v2, v3));
        });
        ServerPlayNetworking.registerGlobalReceiver(MOVE, (server, player, handler, buf, responseSender) -> {
            int fr = buf.readInt(), fc = buf.readInt(), tr = buf.readInt(), tc = buf.readInt();
            server.execute(() -> ChessGameManager.tryMove(player, fr, fc, tr, tc));
        });
        ServerPlayNetworking.registerGlobalReceiver(SELECT_3D, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> ChessGameManager.select3DPiece(player, pos));
        });
        ServerPlayNetworking.registerGlobalReceiver(MOVE_3D, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> ChessGameManager.moveSelected3D(player, pos));
        });
        ServerPlayNetworking.registerGlobalReceiver(DEV_SWITCH_TEAM, (server, player, handler, buf, responseSender) ->
            server.execute(() -> {
                if (!ChessGameManager.switchDevTeam(player)) player.sendMessage(net.minecraft.text.Text.literal("Кейбинд доступен только после /chessdev."), false);
            }));
        ServerPlayNetworking.registerGlobalReceiver(PROMOTION, (server, player, handler, buf, responseSender) -> {
            String choice = buf.readString(4);
            server.execute(() -> ChessGameManager.choosePromotion(player, choice));
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
        buf.writeBoolean(state.threeDimensional);
        buf.writeBoolean(player.getCommandTags().contains("chess_dev"));
        buf.writeBoolean(state.promotionPending && player.getUuid().equals(state.promotionPlayerId));
        buf.writeBoolean(state.promotionWhite);
        buf.writeInt(state.promotionRow);
        buf.writeInt(state.promotionCol);
        buf.writeString(ChessGameManager.promotionOptions(state, state.promotionWhite), 8);
        buf.writeString(capturedString(state.whiteCapturedPieces), 32);
        buf.writeString(capturedString(state.blackCapturedPieces), 32);
        ServerPlayNetworking.send(player, BOARD_STATE, buf);
    }

    private static String capturedString(java.util.List<Character> pieces) {
        StringBuilder value = new StringBuilder(pieces.size());
        for (char piece : pieces) value.append(piece);
        return value.toString();
    }

    public static void broadcastBoard(ServerWorld world) {
        for (ServerPlayerEntity player : PlayerLookup.world(world)) sendBoard(player);
    }

    public static void sendClockState(ServerPlayerEntity player) {
        ChessGameManager.BoardState state = ChessGameManager.board(player.getWorld());
        net.minecraft.scoreboard.AbstractTeam team = player.getScoreboardTeam();
        boolean hasTeam = team != null && (team.getName().equals("white") || team.getName().equals("black"));
        boolean white = hasTeam && team.getName().equals("white");
        boolean enabled = hasTeam && state.running && state.ruleMode != ChessGameManager.RuleMode.NO_REALISM;
        boolean ticking = enabled && !state.paused && !state.promotionPending && state.whiteTurn == white;
        long remaining = white ? state.whiteClockTicks : state.blackClockTicks;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(enabled);
        buf.writeBoolean(ticking);
        buf.writeLong(remaining);
        buf.writeLong(state.clockMaxTicks());
        ServerPlayNetworking.send(player, CLOCK_STATE, buf);
    }

    public static void broadcastClockState(ServerWorld world) {
        for (ServerPlayerEntity player : PlayerLookup.world(world)) sendClockState(player);
    }

    public static void sendHighlights(ServerPlayerEntity player, BlockPos selected, int row, int col, boolean[] legal) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(selected == null ? BlockPos.ORIGIN : selected);
        buf.writeInt(row);
        buf.writeInt(col);
        for (int i = 0; i < 64; i++) buf.writeBoolean(legal != null && i < legal.length && legal[i]);
        ServerPlayNetworking.send(player, HIGHLIGHTS, buf);
    }

    public static void broadcastMoveStart(ServerWorld world, int fromRow, int fromCol, int toRow, int toCol,
                                           char piece, boolean knight, int durationTicks) {
        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(fromRow);
            buf.writeInt(fromCol);
            buf.writeInt(toRow);
            buf.writeInt(toCol);
            buf.writeChar(piece);
            buf.writeBoolean(knight);
            buf.writeInt(durationTicks);
            ServerPlayNetworking.send(player, BOARD_MOVE_ANIMATION, buf);
        }
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
