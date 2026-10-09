package com.chess;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;

/** Server-authoritative chess board and match controller. */
public final class ChessGameManager {
    public enum RuleMode { NO_REALISM, REALISM, FULL_REALISM }
    public enum MatchMode { ONE_ONE, TWO_TWO }

    private static final Map<RegistryKey<World>, BoardState> BOARDS = new HashMap<>();

    private ChessGameManager() {}

    public static void registerTicker() {
        ServerTickEvents.END_SERVER_TICK.register(ChessGameManager::tick);
    }

    public static BoardState board(World world) {
        return BOARDS.computeIfAbsent(world.getRegistryKey(), key -> new BoardState());
    }

    public static void configureBoard(ServerWorld world, BlockPos firstSquare) {
        BoardState state = board(world);
        state.origin = firstSquare.toImmutable();
        state.configured = true;
        state.pending = null;
        world.getPlayers().forEach(p -> p.sendMessage(Text.literal(
            "Шахматное поле привязано: " + firstSquare.getX() + " " + firstSquare.getY() + " " + firstSquare.getZ()
        ), false));
    }

    /** Builds an alternating 8x8 board at the supplied first tile coordinate. */
    public static void buildBoard(ServerWorld world, BlockPos firstSquare) {
        configureBoard(world, firstSquare);
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                BlockPos pos = firstSquare.add(col, 0, row);
                boolean light = ((row + col) & 1) == 0;
                world.setBlockState(pos, (light ? ModBlocks.CHESS_WHITE_SQUARE : ModBlocks.CHESS_BLACK_SQUARE).getDefaultState(), 3);
            }
        }
        broadcast(world, "Создано шахматное поле 8×8. Фигуры размещаются на один блок выше клеток.");
    }

    public static BlockPos piecePos(BoardState state, int row, int col) {
        return state.origin.add(col, 1, row);
    }

    public static char[][] readBoard(World world) {
        BoardState state = board(world);
        char[][] chars = new char[8][8];
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            chars[r][c] = state.configured
                ? symbol(world.getBlockState(piecePos(state, r, c)))
                : '.';
        }
        return chars;
    }

    public static String serializeBoard(World world) {
        char[][] board = readBoard(world);
        StringBuilder result = new StringBuilder(64);
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) result.append(board[r][c]);
        return result.toString();
    }

    private static char symbol(BlockState state) {
        ChessPieceType type = ChessPieceType.fromBlock(state);
        return type == null ? '.' : type.symbol();
    }

    public static void start(ServerWorld world) {
        BoardState state = board(world);
        if (!state.configured) throw new IllegalStateException("Сначала привяжите поле предметом Chess Board Configurator.");
        state.running = true;
        state.paused = false;
        state.whiteTurn = true;
        state.pending = null;
        broadcast(world, "Шахматная партия началась. Первый ход — белые.");
        updateTeamHighlights(world.getServer(), state);
    }

    public static void stop(ServerWorld world, String message) {
        BoardState state = board(world);
        state.running = false;
        state.paused = false;
        state.pending = null;
        broadcast(world, message);
    }

    public static void togglePause(ServerWorld world) {
        BoardState state = board(world);
        if (!state.running) throw new IllegalStateException("Партия не запущена.");
        state.paused = !state.paused;
        broadcast(world, state.paused ? "Шахматная партия приостановлена." : "Шахматная партия продолжена.");
    }

    public static void setRuleMode(ServerWorld world, RuleMode mode) {
        BoardState state = board(world);
        state.ruleMode = mode;
        broadcast(world, "Режим правил: " + mode.name().toLowerCase() + ".");
    }

    public static void setMatchMode(ServerWorld world, MatchMode mode) {
        BoardState state = board(world);
        state.matchMode = mode;
        broadcast(world, mode == MatchMode.ONE_ONE ? "Режим 1v1." : "Режим 2v2.");
    }

    public static boolean transferTurn(ServerWorld world) {
        BoardState state = board(world);
        if (!state.running || state.paused || state.pending != null) return false;
        state.whiteTurn = !state.whiteTurn;
        updateTeamHighlights(world.getServer(), state);
        broadcast(world, "Ход передан команде " + (state.whiteTurn ? "white" : "black") + ".");
        return true;
    }

    public static void setTeleport(ServerWorld world, boolean white, BlockPos pos) {
        BoardState state = board(world);
        state.teleportTargets.put(white ? "white" : "black", pos.toImmutable());
    }

    public static BlockPos teleportTarget(ServerWorld world, boolean white) {
        return board(world).teleportTargets.get(white ? "white" : "black");
    }

    public static boolean checkBoard(ServerWorld world, ServerPlayerEntity player) {
        BoardState state = board(world);
        if (!state.configured) {
            player.sendMessage(Text.literal("Поле не настроено. Поставьте Chess Board Configurator на первую клетку."), false);
            return false;
        }
        int squares = 0, pieces = 0;
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            BlockPos tile = state.origin.add(c, 0, r);
            BlockState tileState = world.getBlockState(tile);
            if (tileState.isOf(ModBlocks.CHESS_WHITE_SQUARE) || tileState.isOf(ModBlocks.CHESS_BLACK_SQUARE)) squares++;
            if (symbol(world.getBlockState(piecePos(state, r, c))) != '.') pieces++;
        }
        boolean valid = squares == 64;
        player.sendMessage(Text.literal("Проверка шахматного поля: " + (valid ? "OK" : "ОШИБКА")
            + " — клеток " + squares + "/64, фигур " + pieces + ", верхний слой Y=" + (state.origin.getY() + 1)
            + ", режим " + (state.running ? (state.paused ? "пауза" : "игра") : "остановлено") + "."),
            false);
        return valid;
    }

    public static void tryMove(ServerPlayerEntity player, int fr, int fc, int tr, int tc) {
        ServerWorld world = player.getServerWorld();
        BoardState state = board(world);
        if (!state.configured) { tell(player, "Сначала настройте поле предметом Chess Board Configurator."); return; }
        if (!state.running) { tell(player, "Сначала выполните /chess start."); return; }
        if (state.paused) { tell(player, "Партия приостановлена."); return; }
        if (state.pending != null) { tell(player, "Дождитесь завершения анимации предыдущего хода."); return; }
        if (!inside(fr, fc) || !inside(tr, tc) || (fr == tr && fc == tc)) { tell(player, "Некорректная клетка."); return; }

        char[][] cells = readBoard(world);
        char moving = cells[fr][fc], target = cells[tr][tc];
        ChessPieceType type = ChessPieceType.fromSymbol(moving);
        if (type == null) { tell(player, "На выбранной клетке нет фигуры."); return; }
        if (target != '.' && Character.toUpperCase(target) == 'K') {
            tell(player, "Король не снимается: партия должна завершиться матом."); return;
        }
        if (target != '.' && Character.isUpperCase(target) == Character.isUpperCase(moving)) {
            tell(player, "Нельзя взять собственную фигуру."); return;
        }

        net.minecraft.scoreboard.AbstractTeam team = player.getScoreboardTeam();
        if (team != null && (team.getName().equals("white") || team.getName().equals("black"))) {
            boolean playerWhite = team.getName().equals("white");
            if (playerWhite != type.isWhite()) { tell(player, "Выберите фигуру своей команды."); return; }
            if (state.ruleMode != RuleMode.NO_REALISM && playerWhite != state.whiteTurn) {
                tell(player, "Сейчас ход " + (state.whiteTurn ? "белых" : "чёрных") + "."); return;
            }
        } else if (state.ruleMode != RuleMode.NO_REALISM && type.isWhite() != state.whiteTurn) {
            tell(player, "Сейчас ход " + (state.whiteTurn ? "белых" : "чёрных") + "."); return;
        }

        boolean enforceMovement = state.ruleMode != RuleMode.NO_REALISM;
        boolean full = state.ruleMode == RuleMode.FULL_REALISM;
        boolean kingMoved = type.isWhite() ? state.whiteKingMoved : state.blackKingMoved;
        boolean leftRookMoved = type.isWhite() ? state.whiteLeftRookMoved : state.blackLeftRookMoved;
        boolean rightRookMoved = type.isWhite() ? state.whiteRightRookMoved : state.blackRightRookMoved;
        if (enforceMovement && !ChessRules.isLegalMove(cells, fr, fc, tr, tc, full,
            state.enPassantRow, state.enPassantCol, kingMoved, leftRookMoved, rightRookMoved)) {
            tell(player, full ? "Ход нарушает правила шахмат или оставляет короля под шахом." : "Недопустимый ход этой фигуры.");
            return;
        }

        BlockPos from = piecePos(state, fr, fc);
        BlockPos to = piecePos(state, tr, tc);
        BlockState movingState = world.getBlockState(from);
        boolean enPassant = Character.toUpperCase(moving) == 'P' && fc != tc && target == '.';
        BlockPos epCapture = enPassant ? piecePos(state, fr, tc) : null;
        boolean castle = Character.toUpperCase(moving) == 'K' && Math.abs(tc - fc) == 2;
        BlockPos rookFrom = castle ? piecePos(state, fr, tc > fc ? 7 : 0) : null;
        BlockPos rookTo = castle ? piecePos(state, fr, tc > fc ? 5 : 3) : null;
        BlockState rookState = castle ? world.getBlockState(rookFrom) : Blocks.AIR.getDefaultState();

        float movingYaw = world.getBlockEntity(from) instanceof ChessFigureBlockEntity movingEntity
            ? movingEntity.getYawDegrees() : 0.0f;
        float rookYaw = castle && world.getBlockEntity(rookFrom) instanceof ChessFigureBlockEntity rookEntity
            ? rookEntity.getYawDegrees() : 0.0f;
        String animation = animationFor(type, tr - fr, tc - fc);
        int animationTicks = animation.startsWith("horse") || "hode_2".equals(animation) ? 40
            : "hode_1".equals(animation) ? 20 : 30;
        ChessNetwork.broadcastAnimation(world, from, animation);
        if (castle && ChessPieceType.fromBlock(rookState) != null) {
            ChessNetwork.broadcastAnimation(world, rookFrom, animationFor(ChessPieceType.fromBlock(rookState), 0, tc > fc ? 1 : -1));
        }
        state.pending = new PendingMove(from, to, movingState, epCapture, rookFrom, rookTo, rookState,
            type, fr, fc, tr, tc, movingYaw, rookYaw, serverTick(world.getServer()) + animationTicks, player.getUuid());
        state.enPassantRow = Character.toUpperCase(moving) == 'P' && Math.abs(tr - fr) == 2 ? (tr + fr) / 2 : -1;
        state.enPassantCol = state.enPassantRow < 0 ? -1 : fc;
        updateMovedFlags(state, type, fr, fc);
        player.sendMessage(Text.literal("Ход выбран: " + (char)('a' + fc) + (8-fr) + " → " + (char)('a' + tc) + (8-tr) + "."), true);
    }

    private static void updateMovedFlags(BoardState state, ChessPieceType type, int row, int col) {
        if (type == ChessPieceType.WHITE_KING) state.whiteKingMoved = true;
        if (type == ChessPieceType.BLACK_KING) state.blackKingMoved = true;
        if (type == ChessPieceType.WHITE_ROOK) {
            if (row == 7 && col == 0) state.whiteLeftRookMoved = true;
            if (row == 7 && col == 7) state.whiteRightRookMoved = true;
        }
        if (type == ChessPieceType.BLACK_ROOK) {
            if (row == 0 && col == 0) state.blackLeftRookMoved = true;
            if (row == 0 && col == 7) state.blackRightRookMoved = true;
        }
    }

    private static void tick(MinecraftServer server) {
        long tick = serverTick(server);
        for (Map.Entry<RegistryKey<World>, BoardState> entry : BOARDS.entrySet()) {
            ServerWorld world = server.getWorld(entry.getKey());
            BoardState state = entry.getValue();
            if (world == null || state.pending == null || tick < state.pending.executeAt) continue;
            PendingMove move = state.pending;
            ChessPieceType sourcePiece = ChessPieceType.fromBlock(world.getBlockState(move.from));
            if (sourcePiece == null || sourcePiece != move.type) {
                state.pending = null;
                continue;
            }
            BlockState targetState = move.movingState;
            if (move.type == ChessPieceType.WHITE_PAWN && move.tr == 0) targetState = ModBlocks.CHESS_WHITE_FERZ.getDefaultState();
            if (move.type == ChessPieceType.BLACK_PAWN && move.tr == 7) targetState = ModBlocks.CHESS_BLACK_FERZ.getDefaultState();

            world.setBlockState(move.to, targetState);
            if (world.getBlockEntity(move.to) instanceof ChessFigureBlockEntity movedEntity) {
                movedEntity.setYawDegrees(move.movingYaw);
            }
            world.setBlockState(move.from, Blocks.AIR.getDefaultState(), 3);
            if (move.enPassantCapture != null) world.setBlockState(move.enPassantCapture, Blocks.AIR.getDefaultState(), 3);
            if (move.rookFrom != null && ChessPieceType.fromBlock(move.rookState) != null) {
                world.setBlockState(move.rookTo, move.rookState);
                if (world.getBlockEntity(move.rookTo) instanceof ChessFigureBlockEntity movedRook) {
                    movedRook.setYawDegrees(move.rookYaw);
                }
                world.setBlockState(move.rookFrom, Blocks.AIR.getDefaultState(), 3);
            }
            state.pending = null;
            if (state.ruleMode != RuleMode.NO_REALISM) state.whiteTurn = !state.whiteTurn;
            updateTeamHighlights(server, state);
            announceTurn(world, state);
            if (state.ruleMode == RuleMode.FULL_REALISM) checkEndCondition(world, state);
        }
    }

    private static void checkEndCondition(ServerWorld world, BoardState state) {
        char[][] cells = readBoard(world);
        boolean whiteToMove = state.whiteTurn;
        boolean inCheck = ChessRules.isInCheck(cells, whiteToMove);
        boolean kingMoved = whiteToMove ? state.whiteKingMoved : state.blackKingMoved;
        boolean leftMoved = whiteToMove ? state.whiteLeftRookMoved : state.blackLeftRookMoved;
        boolean rightMoved = whiteToMove ? state.whiteRightRookMoved : state.blackRightRookMoved;
        boolean moves = ChessRules.hasAnyLegalMove(cells, whiteToMove, true,
            state.enPassantRow, state.enPassantCol, kingMoved, leftMoved, rightMoved);
        if (!moves) {
            state.running = false;
            broadcast(world, inCheck
                ? "Мат! Победа " + (whiteToMove ? "чёрных" : "белых") + "."
                : "Пат. Ничья.");
        } else if (inCheck) {
            broadcast(world, "Шах " + (whiteToMove ? "белому" : "чёрному") + " королю!");
        }
    }

    private static String animationFor(ChessPieceType type, int dr, int dc) {
        int distance = Math.max(Math.abs(dr), Math.abs(dc));
        distance = Math.max(1, Math.min(8, distance));
        switch (type.model()) {
            case "ladya":
                if (dr > 0) return "ladya_" + distance + "_forward";
                if (dr < 0) return "ladya_" + distance + "_back";
                if (dc > 0) return "ladya_" + distance + "_right";
                return "ladya_" + distance + "_left";
            case "ferz":
                if (dr == 0) return dc > 0 ? "ferz_" + distance + "_right" : "ferz_" + distance + "_left";
                if (dc == 0) return dr > 0 ? "ferz_" + distance + "_forward" : "ferz_" + distance + "_back";
                return "ferz_" + (dr > 0 ? "forward" : "back") + (dc > 0 ? "_right_diag_" : "_left_diag_") + distance;
            case "el":
                return "el_" + (dr > 0 ? "forward" : "back") + (dc > 0 ? "_right_diag_" : "_left_diag_") + distance;
            case "king":
                if (dr == 0 && dc == 0) return "king_1_forward";
                if (dr == 0) return dc > 0 ? "king_1_right" : "king_1_left";
                if (dc == 0) return dr > 0 ? "king_1_forward" : "king_1_back";
                return "king_" + (dr > 0 ? "forward" : "back") + (dc > 0 ? "_right_diag_1" : "_left_diag_1");
            case "horse":
                if (Math.abs(dc) == 2) return "horse_g_" + (dr > 0 ? "forward" : "back") + (dc > 0 ? "_right_1" : "_left_1");
                return "horse_g_" + (dr > 0 ? "forward" : "back") + (dc > 0 ? "_right_2" : "_left_2");
            case "peshka":
            default: return "hode_" + (Math.abs(dr) == 2 ? "2" : "1");
        }
    }

    private static void announceTurn(ServerWorld world, BoardState state) {
        broadcast(world, "Ход команды " + (state.whiteTurn ? "white" : "black") + ".");
    }

    private static void updateTeamHighlights(MinecraftServer server, BoardState state) {
        Team white = server.getScoreboard().getTeam("white");
        Team black = server.getScoreboard().getTeam("black");
        if (white != null) white.setColor(state.whiteTurn ? Formatting.GOLD : Formatting.WHITE);
        if (black != null) black.setColor(state.whiteTurn ? Formatting.WHITE : Formatting.GOLD);
    }

    private static void broadcast(ServerWorld world, String message) {
        for (ServerPlayerEntity player : world.getPlayers()) player.sendMessage(Text.literal(message), false);
    }

    private static void tell(PlayerEntity player, String message) {
        player.sendMessage(Text.literal(message), false);
    }

    private static boolean inside(int r, int c) { return r >= 0 && r < 8 && c >= 0 && c < 8; }
    private static long serverTick(MinecraftServer server) { return server.getTicks(); }

    public static final class BoardState {
        public BlockPos origin = BlockPos.ORIGIN;
        public boolean configured;
        public boolean running;
        public boolean paused;
        public boolean whiteTurn = true;
        public RuleMode ruleMode = RuleMode.REALISM;
        public MatchMode matchMode = MatchMode.ONE_ONE;
        public boolean whiteKingMoved, blackKingMoved;
        public boolean whiteLeftRookMoved, whiteRightRookMoved, blackLeftRookMoved, blackRightRookMoved;
        public int enPassantRow = -1, enPassantCol = -1;
        public PendingMove pending;
        public final Map<String, BlockPos> teleportTargets = new HashMap<>();
    }

    public static final class PendingMove {
        public final BlockPos from, to, enPassantCapture, rookFrom, rookTo;
        public final BlockState movingState, rookState;
        public final ChessPieceType type;
        public final int fr, fc, tr, tc;
        public final float movingYaw, rookYaw;
        public final long executeAt;
        public final UUID playerId;
        PendingMove(BlockPos from, BlockPos to, BlockState movingState, BlockPos enPassantCapture,
                    BlockPos rookFrom, BlockPos rookTo, BlockState rookState, ChessPieceType type,
                    int fr, int fc, int tr, int tc, float movingYaw, float rookYaw, long executeAt, UUID playerId) {
            this.from = from; this.to = to; this.movingState = movingState; this.enPassantCapture = enPassantCapture;
            this.rookFrom = rookFrom; this.rookTo = rookTo; this.rookState = rookState; this.type = type;
            this.fr = fr; this.fc = fc; this.tr = tr; this.tc = tc;
            this.movingYaw = movingYaw; this.rookYaw = rookYaw;
            this.executeAt = executeAt; this.playerId = playerId;
        }
    }
}
