package com.chess;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.GameMode;

/** Server-authoritative chess board and match controller. */
public final class ChessGameManager {
    public enum RuleMode { NO_REALISM, REALISM, FULL_REALISM }
    public enum MatchMode { ONE_ONE, TWO_TWO }

    private static final Map<RegistryKey<World>, BoardState> BOARDS = new HashMap<>();
    private static final Set<UUID> KIT_SLOT_WARNED = new HashSet<>();

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
        state.promotionPending = false;
        state.whiteClockTicks = state.blackClockTicks = state.clockMaxTicks();
        autoConfigureCaptures(world, state);
        broadcast(world, "Шахматная партия началась. Первый ход — белые.");
        updateTeamHighlights(world.getServer(), state);
        syncAllTeamPieces(world.getServer());
        ChessNetwork.broadcastBoard(world);
        ChessNetwork.broadcastClockState(world);
    }

    public static void stop(ServerWorld world, String message) {
        BoardState state = board(world);
        state.running = false;
        state.paused = false;
        state.pending = null;
        syncAllTeamPieces(world.getServer());
        ChessNetwork.broadcastBoard(world);
        ChessNetwork.broadcastClockState(world);
        broadcast(world, message);
    }

    public static void togglePause(ServerWorld world) {
        BoardState state = board(world);
        if (!state.running) throw new IllegalStateException("Партия не запущена.");
        state.paused = !state.paused;
        broadcast(world, state.paused ? "Шахматная партия приостановлена." : "Шахматная партия продолжена.");
        ChessNetwork.broadcastClockState(world);
    }

    public static void configureClock(ServerWorld world, int minutes) {
        BoardState state = board(world);
        state.clockMinutes = Math.max(1, Math.min(180, minutes));
        state.whiteClockTicks = state.blackClockTicks = state.clockMaxTicks();
        broadcast(world, "Шахматный таймер установлен: " + state.clockMinutes + " мин. на каждую команду. Шкала опыта показывает оставшееся время.");
        ChessNetwork.broadcastClockState(world);
        ChessNetwork.broadcastBoard(world);
    }

    public static void setRuleMode(ServerWorld world, RuleMode mode) {
        BoardState state = board(world);
        state.ruleMode = mode;
        broadcast(world, "Режим правил: " + mode.name().toLowerCase() + ".");
        ChessNetwork.broadcastBoard(world);
        ChessNetwork.broadcastClockState(world);
    }

    public static void resetBoard(ServerWorld world) {
        BoardState state = board(world);
        if (!state.configured) throw new IllegalStateException("Сначала привяжите доску командой /chess board set <x y z> или конфигуратором.");
        state.pending = null;
        state.promotionPending = false;
        state.promotionPlayerId = null;
        state.promotionRow = state.promotionCol = -1;
        state.whiteTurn = true;
        state.paused = false;
        state.running = true;
        state.whiteKingMoved = state.blackKingMoved = false;
        state.whiteLeftRookMoved = state.whiteRightRookMoved = false;
        state.blackLeftRookMoved = state.blackRightRookMoved = false;
        state.enPassantRow = state.enPassantCol = -1;
        state.whiteClockTicks = state.blackClockTicks = state.clockMaxTicks();
        state.whiteCapturedPieces.clear();
        state.blackCapturedPieces.clear();
        state.selected3DPieces.clear();

        String blackBack = "rnbqkbnr";
        String whiteBack = "RNBQKBNR";
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                char symbol = '.';
                if (row == 0) symbol = blackBack.charAt(col);
                else if (row == 1) symbol = 'p';
                else if (row == 6) symbol = 'P';
                else if (row == 7) symbol = whiteBack.charAt(col);
                BlockPos pos = piecePos(state, row, col);
                if (symbol == '.') {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                } else {
                    ChessPieceType type = ChessPieceType.fromSymbol(symbol);
                    world.setBlockState(pos, type.block().getDefaultState(), 3);
                    if (world.getBlockEntity(pos) instanceof ChessFigureBlockEntity figure) {
                        figure.setYawDegrees(0.0f);
                        figure.setTintRgb(0xFFFFFF);
                    }
                }
            }
        }

        if (state.whiteCaptureOrigin != null) clearCaptureTray(world, state.whiteCaptureOrigin);
        if (state.blackCaptureOrigin != null) clearCaptureTray(world, state.blackCaptureOrigin);
        autoConfigureCaptures(world, state);
        updateTeamHighlights(world.getServer(), state);
        syncAllTeamPieces(world.getServer());
        ChessNetwork.broadcastBoard(world);
        ChessNetwork.broadcastClockState(world);
        broadcast(world, "Доска сброшена. Фигуры расставлены в начальную позицию, первый ход — белые.");
    }

    private static void clearCaptureTray(ServerWorld world, BlockPos tray) {
        for (int row = 0; row < 2; row++) for (int col = 0; col < 8; col++) {
            BlockPos piece = tray.add(col, 1, row);
            if (ChessPieceType.fromBlock(world.getBlockState(piece)) != null)
                world.setBlockState(piece, Blocks.AIR.getDefaultState(), 3);
        }
    }

    /** Reconciles the inventory against the player's current team instead of using one-time tags. */
    public static void giveTeamPieces(ServerPlayerEntity player, boolean white) {
        syncPlayerPieceInventory(player);
    }

    public static void syncAllTeamPieces(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) syncPlayerPieceInventory(player);
    }

    private static void syncPlayerPieceInventory(ServerPlayerEntity player) {
        BoardState state = board(player.getWorld());
        net.minecraft.scoreboard.AbstractTeam team = player.getScoreboardTeam();
        boolean hasChessTeam = team != null && (team.getName().equals("white") || team.getName().equals("black"));
        boolean white = hasChessTeam && team.getName().equals("white");
        boolean shouldHaveKit = hasChessTeam && state.running && !state.threeDimensional;
        net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();

        if (!shouldHaveKit) {
            clearFigureItems(inventory);
            KIT_SLOT_WARNED.remove(player.getUuid());
            return;
        }
        if (hasCorrectPieceKit(inventory, white)) {
            KIT_SLOT_WARNED.remove(player.getUuid());
            return;
        }

        clearFigureItems(inventory);
        int emptySlots = 0;
        for (net.minecraft.item.ItemStack stack : inventory.main) if (stack.isEmpty()) emptySlots++;
        int pieceKinds = 0;
        for (ChessPieceType type : ChessPieceType.values()) if (type.isWhite() == white) pieceKinds++;
        if (emptySlots < pieceKinds) {
            if (KIT_SLOT_WARNED.add(player.getUuid()))
                player.sendMessage(Text.literal("Для комплекта шахмат освободи хотя бы " + pieceKinds + " слотов инвентаря."), true);
            return;
        }
        KIT_SLOT_WARNED.remove(player.getUuid());

        for (ChessPieceType type : ChessPieceType.values()) {
            if (type.isWhite() != white) continue;
            int amount = pieceAmount(type);
            net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(type.block().asItem(), amount);
            inventory.insertStack(stack);
        }
        inventory.markDirty();
        player.sendMessage(Text.literal("Комплект шахматных фигур обновлён под команду " + (white ? "white" : "black") + "."), false);
    }

    private static int pieceAmount(ChessPieceType type) {
        char symbol = Character.toUpperCase(type.symbol());
        if (symbol == 'P') return 8;
        if ("RNB".indexOf(symbol) >= 0) return 2;
        return 1;
    }

    private static void clearFigureItems(net.minecraft.entity.player.PlayerInventory inventory) {
        boolean changed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            net.minecraft.item.ItemStack stack = inventory.getStack(slot);
            if (ModBlocks.isFigureItem(stack)) {
                inventory.setStack(slot, net.minecraft.item.ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) inventory.markDirty();
    }

    private static boolean hasCorrectPieceKit(net.minecraft.entity.player.PlayerInventory inventory, boolean white) {
        Map<net.minecraft.item.Item, Integer> actual = new HashMap<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            net.minecraft.item.ItemStack stack = inventory.getStack(slot);
            if (ModBlocks.isFigureItem(stack)) actual.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        for (ChessPieceType type : ChessPieceType.values()) {
            int expected = type.isWhite() == white ? pieceAmount(type) : 0;
            int count = actual.getOrDefault(type.block().asItem(), 0);
            if (count != expected) return false;
        }
        return true;
    }

    public static void setViewMode(ServerWorld world, boolean threeDimensional) {
        BoardState state = board(world);
        state.threeDimensional = threeDimensional;
        state.selected3DPieces.clear();
        broadcast(world, threeDimensional ? "Режим 3D включён." : "Режим 2D включён.");
        for (ServerPlayerEntity player : world.getPlayers())
            ChessNetwork.sendHighlights(player, BlockPos.ORIGIN, -1, -1, new boolean[64]);
        syncAllTeamPieces(world.getServer());
        ChessNetwork.broadcastBoard(world);
    }

    public static boolean switchDevTeam(ServerPlayerEntity player) {
        if (!player.getCommandTags().contains("chess_dev")) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        var scoreboard = server.getScoreboard();
        var oldTeam = player.getScoreboardTeam();
        String nextName = oldTeam != null && oldTeam.getName().equals("white") ? "black" : "white";
        Team team = scoreboard.getTeam(nextName);
        if (team == null) {
            team = scoreboard.addTeam(nextName);
            team.setColor(nextName.equals("white") ? Formatting.WHITE : Formatting.DARK_GRAY);
        }
        scoreboard.addPlayerToTeam(player.getEntityName(), team);
        giveTeamPieces(player, nextName.equals("white"));
        player.sendMessage(Text.literal("DEV: теперь вы за команду " + nextName + "."), false);
        ChessNetwork.sendBoard(player);
        return true;
    }

    public static void autoConfigureCaptures(ServerWorld world) {
        autoConfigureCaptures(world, board(world));
        broadcast(world, "Поле срубленных фигур настроено автоматически.");
    }

    private static void autoConfigureCaptures(ServerWorld world, BoardState state) {
        if (!state.configured) return;
        // Black pieces start on rows 0-1; white pieces start on rows 6-7.
        // Captures belong on the capturer's own side: white at the far (south) end, black at the near (north) end.
        state.whiteCaptureOrigin = state.origin.add(0, 0, 10);
        state.blackCaptureOrigin = state.origin.add(0, 0, -4);
        for (int row = 0; row < 2; row++) for (int col = 0; col < 8; col++) {
            BlockPos whiteTile = state.whiteCaptureOrigin.add(col, 0, row);
            BlockPos blackTile = state.blackCaptureOrigin.add(col, 0, row);
            if (isAirOrChessTile(world.getBlockState(whiteTile)))
                world.setBlockState(whiteTile, ((row + col) % 2 == 0 ? ModBlocks.CHESS_WHITE_SQUARE : ModBlocks.CHESS_BLACK_SQUARE).getDefaultState(), 3);
            if (isAirOrChessTile(world.getBlockState(blackTile)))
                world.setBlockState(blackTile, ((row + col) % 2 == 0 ? ModBlocks.CHESS_WHITE_SQUARE : ModBlocks.CHESS_BLACK_SQUARE).getDefaultState(), 3);
        }
        for (int i = 0; i < state.whiteCapturedPieces.size(); i++)
            placeCapturedBlock(world, state.whiteCaptureOrigin, i, state.whiteCapturedPieces.get(i));
        for (int i = 0; i < state.blackCapturedPieces.size(); i++)
            placeCapturedBlock(world, state.blackCaptureOrigin, i, state.blackCapturedPieces.get(i));
    }

    private static boolean isAirOrChessTile(BlockState state) {
        return state.isAir() || state.isOf(ModBlocks.CHESS_WHITE_SQUARE) || state.isOf(ModBlocks.CHESS_BLACK_SQUARE);
    }

    private static void placeCapturedBlock(ServerWorld world, BlockPos tray, int index, char symbol) {
        ChessPieceType type = ChessPieceType.fromSymbol(symbol);
        if (type == null || tray == null || index >= 16) return;
        world.setBlockState(tray.add(index % 8, 1, index / 8), type.block().getDefaultState(), 3);
    }

    private static void registerCapturedPiece(ServerWorld world, BoardState state, char symbol, boolean capturerWhite) {
        if (ChessPieceType.fromSymbol(symbol) == null) return;
        List<Character> pieces = capturerWhite ? state.whiteCapturedPieces : state.blackCapturedPieces;
        if (pieces.size() >= 16) return;
        pieces.add(symbol);
        if (state.whiteCaptureOrigin == null || state.blackCaptureOrigin == null) autoConfigureCaptures(world, state);
        placeCapturedBlock(world, capturerWhite ? state.whiteCaptureOrigin : state.blackCaptureOrigin, pieces.size() - 1, symbol);
    }

    public static String promotionOptions(BoardState state, boolean white) {
        List<Character> captured = white ? state.whiteCapturedPieces : state.blackCapturedPieces;
        StringBuilder result = new StringBuilder();
        for (char capturedSymbol : captured) {
            ChessPieceType capturedType = ChessPieceType.fromSymbol(capturedSymbol);
            if (capturedType == null || capturedType.isWhite() == white) continue;
            char kind = Character.toUpperCase(capturedSymbol);
            if ("QRBN".indexOf(kind) >= 0 && result.indexOf(String.valueOf(kind)) < 0) result.append(kind);
        }
        return result.toString();
    }

    public static void choosePromotion(ServerPlayerEntity player, String choice) {
        ServerWorld world = player.getServerWorld();
        BoardState state = board(world);
        if (!state.promotionPending || choice == null || choice.isEmpty()) return;
        if (state.promotionPlayerId != null && !state.promotionPlayerId.equals(player.getUuid())) {
            tell(player, "Превращение выбирает игрок, который сделал этот ход.");
            return;
        }
        char kind = Character.toUpperCase(choice.charAt(0));
        if ("QRBN".indexOf(kind) < 0 || !promotionOptions(state, state.promotionWhite).contains(String.valueOf(kind))) {
            tell(player, "Эта фигура недоступна для превращения.");
            return;
        }
        List<Character> captured = state.promotionWhite ? state.whiteCapturedPieces : state.blackCapturedPieces;
        int capturedIndex = -1;
        for (int i = 0; i < captured.size(); i++) {
            ChessPieceType candidate = ChessPieceType.fromSymbol(captured.get(i));
            if (candidate != null && candidate.isWhite() != state.promotionWhite
                && Character.toUpperCase(captured.get(i)) == kind) {
                capturedIndex = i;
                break;
            }
        }
        if (capturedIndex < 0) {
            tell(player, "Такой фигуры больше нет на поле срубленных: сначала нужно взять её.");
            return;
        }
        char symbol = state.promotionWhite ? kind : Character.toLowerCase(kind);
        char pawnSymbol = state.promotionWhite ? 'P' : 'p';
        BlockPos pos = piecePos(state, state.promotionRow, state.promotionCol);
        float yaw = world.getBlockEntity(pos) instanceof ChessFigureBlockEntity entity ? entity.getYawDegrees() : 0.0f;
        ChessPieceType promoted = ChessPieceType.fromSymbol(symbol);
        ChessPieceType pawn = ChessPieceType.fromSymbol(pawnSymbol);
        if (promoted == null || pawn == null) return;
        world.setBlockState(pos, promoted.block().getDefaultState(), 3);
        if (world.getBlockEntity(pos) instanceof ChessFigureBlockEntity entity) entity.setYawDegrees(yaw);
        // The captured piece is consumed by the promotion; replace its tray slot with the promoting pawn.
        captured.set(capturedIndex, pawnSymbol);
        BlockPos tray = state.promotionWhite ? state.whiteCaptureOrigin : state.blackCaptureOrigin;
        if (tray != null) placeCapturedBlock(world, tray, capturedIndex, pawnSymbol);
        state.promotionPending = false;
        state.promotionPlayerId = null;
        state.whiteTurn = !state.whiteTurn;
        updateTeamHighlights(world.getServer(), state);
        announceTurn(world, state);
        checkEndCondition(world, state);
        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.85f, 1.4f);
        ChessNetwork.broadcastBoard(world);
    }

    public static void select3DPiece(ServerPlayerEntity player, BlockPos clicked) {
        ServerWorld world = player.getServerWorld();
        BoardState state = board(world);
        if (!state.threeDimensional || !state.configured || !state.running || state.paused
            || state.pending != null || state.promotionPending) return;
        int row = clicked.getZ() - state.origin.getZ();
        int col = clicked.getX() - state.origin.getX();
        if (!inside(row, col) || clicked.getY() != state.origin.getY() + 1) return;
        char[][] cells = readBoard(world);
        char piece = cells[row][col];
        ChessPieceType type = ChessPieceType.fromSymbol(piece);
        if (type == null) return;
        if (type.isWhite() != state.whiteTurn) {
            tell(player, "Сейчас ход " + (state.whiteTurn ? "белых" : "чёрных") + ".");
            return;
        }
        var team = player.getScoreboardTeam();
        if (team != null && (team.getName().equals("white") || team.getName().equals("black"))
            && type.isWhite() != team.getName().equals("white")) {
            tell(player, "Выберите фигуру своей команды.");
            return;
        }
        int selected = row * 8 + col;
        Integer current = state.selected3DPieces.get(player.getUuid());
        if (current != null && current == selected) {
            state.selected3DPieces.remove(player.getUuid());
            ChessNetwork.sendHighlights(player, BlockPos.ORIGIN, -1, -1, new boolean[64]);
            return;
        }
        state.selected3DPieces.put(player.getUuid(), selected);
        boolean[] legal = new boolean[64];
        for (int tr = 0; tr < 8; tr++) for (int tc = 0; tc < 8; tc++) {
            legal[tr * 8 + tc] = ChessRules.isLegalMove(cells, row, col, tr, tc, true,
                state.enPassantRow, state.enPassantCol,
                type.isWhite() ? state.whiteKingMoved : state.blackKingMoved,
                type.isWhite() ? state.whiteLeftRookMoved : state.blackLeftRookMoved,
                type.isWhite() ? state.whiteRightRookMoved : state.blackRightRookMoved);
        }
        ChessNetwork.sendHighlights(player, clicked, row, col, legal);
    }

    public static void moveSelected3D(ServerPlayerEntity player, BlockPos clicked) {
        ServerWorld world = player.getServerWorld();
        BoardState state = board(world);
        if (!state.threeDimensional || !state.configured || state.promotionPending) return;
        Integer selected = state.selected3DPieces.get(player.getUuid());
        if (selected == null) return;
        int row = clicked.getZ() - state.origin.getZ();
        int col = clicked.getX() - state.origin.getX();
        if (!inside(row, col)) return;
        int fromRow = selected / 8, fromCol = selected % 8;
        char moving = readBoard(world)[fromRow][fromCol];
        char target = readBoard(world)[row][col];
        if (target != '.' && moving != '.' && Character.isUpperCase(target) == Character.isUpperCase(moving)) {
            select3DPiece(player, piecePos(state, row, col));
            return;
        }
        tryMove(player, fromRow, fromCol, row, col);
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
        if (state.promotionPending) { tell(player, "Сначала выберите фигуру для превращения пешки."); return; }
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
        }
        // A free-selection mode still alternates turns; only piece movement restrictions are relaxed.
        if (type.isWhite() != state.whiteTurn) {
            tell(player, "Сейчас ход " + (state.whiteTurn ? "белых" : "чёрных") + ".");
            return;
        }
        if (state.ruleMode == RuleMode.FULL_REALISM && !state.threeDimensional) {
            net.minecraft.item.Item held = player.getMainHandStack().getItem();
            net.minecraft.item.Item offhand = player.getOffHandStack().getItem();
            if (held != type.block().asItem() && offhand != type.block().asItem()) {
                tell(player, "Полный реализм: держи предмет именно этой фигуры, чтобы сделать ей ход.");
                return;
            }
        }

        // All variants keep real chess movement and king-safety rules. FULL_REALISM
        // additionally requires the matching piece item in hand; NO_REALISM disables the clock.
        boolean enforceMovement = true;
        boolean full = true;
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
        char capturedSymbol = enPassant ? cells[fr][tc] : target;
        boolean castle = Character.toUpperCase(moving) == 'K' && Math.abs(tc - fc) == 2;
        BlockPos rookFrom = castle ? piecePos(state, fr, tc > fc ? 7 : 0) : null;
        BlockPos rookTo = castle ? piecePos(state, fr, tc > fc ? 5 : 3) : null;
        BlockState rookState = castle ? world.getBlockState(rookFrom) : Blocks.AIR.getDefaultState();

        float movingYaw = world.getBlockEntity(from) instanceof ChessFigureBlockEntity movingEntity
            ? movingEntity.getYawDegrees() : 0.0f;
        float rookYaw = castle && world.getBlockEntity(rookFrom) instanceof ChessFigureBlockEntity rookEntity
            ? rookEntity.getYawDegrees() : 0.0f;
        int animationTicks = Math.max(6, Math.min(12, Math.max(Math.abs(tr - fr), Math.abs(tc - fc)) * 2
            + (type.model().equals("horse") ? 2 : 0)));
        if (world.getBlockEntity(from) instanceof ChessFigureBlockEntity movingEntity)
            movingEntity.beginMove(to, animationTicks, type.model().equals("horse"));
        if (castle && world.getBlockEntity(rookFrom) instanceof ChessFigureBlockEntity rookEntity)
            rookEntity.beginMove(rookTo, animationTicks);
        state.pending = new PendingMove(from, to, movingState, epCapture, rookFrom, rookTo, rookState,
            type, fr, fc, tr, tc, movingYaw, rookYaw, capturedSymbol, serverTick(world.getServer()) + animationTicks, player.getUuid());
        ChessNetwork.broadcastMoveStart(world, fr, fc, tr, tc, moving, type.model().equals("horse"), animationTicks);
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
        // The minigame is intended for adventure mode: prevent vanilla block breaking/placing
        // from interfering with the board. Only players currently in Survival are changed.
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.interactionManager.getGameMode() == GameMode.SURVIVAL) {
                player.changeGameMode(GameMode.ADVENTURE);
            }
        }
        if (tick % 20L == 0L) syncAllTeamPieces(server);
        for (Map.Entry<RegistryKey<World>, BoardState> entry : BOARDS.entrySet()) {
            ServerWorld world = server.getWorld(entry.getKey());
            BoardState state = entry.getValue();
            if (world == null) continue;
            if (tick % 20L == 0L) {
                if (state.running && !state.paused && state.pending == null && !state.promotionPending
                    && state.ruleMode != RuleMode.NO_REALISM) {
                    boolean losingWhite = state.whiteTurn;
                    if (losingWhite) state.whiteClockTicks = Math.max(0L, state.whiteClockTicks - 20L);
                    else state.blackClockTicks = Math.max(0L, state.blackClockTicks - 20L);
                    long remaining = losingWhite ? state.whiteClockTicks : state.blackClockTicks;
                    if (remaining <= 0L) {
                        state.running = false;
                        broadcast(world, "Время команды " + (losingWhite ? "white" : "black") + " вышло.");
                        notifyCheckTeam(world, losingWhite, "ПРОИГРЫШ", SoundEvents.ENTITY_VILLAGER_NO);
                        notifyCheckTeam(world, !losingWhite, "ПОБЕДА", SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
                        syncAllTeamPieces(server);
                    }
                }
                ChessNetwork.broadcastClockState(world);
            }
            if (state.pending == null || tick < state.pending.executeAt) continue;
            PendingMove move = state.pending;
            ChessPieceType sourcePiece = ChessPieceType.fromBlock(world.getBlockState(move.from));
            if (sourcePiece == null || sourcePiece != move.type) {
                state.pending = null;
                state.selected3DPieces.clear();
                continue;
            }

            boolean needsPromotion = (move.type == ChessPieceType.WHITE_PAWN && move.tr == 0)
                || (move.type == ChessPieceType.BLACK_PAWN && move.tr == 7);
            if (move.capturedSymbol != '.') {
                registerCapturedPiece(world, state, move.capturedSymbol, move.type.isWhite());
                world.playSound(null, move.to, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.9f, 1.05f);
            }

            // Leave a pawn on the back rank until its owner chooses the replacement.
            world.setBlockState(move.to, move.movingState, 3);
            if (world.getBlockEntity(move.to) instanceof ChessFigureBlockEntity movedEntity)
                movedEntity.setYawDegrees(move.movingYaw);
            world.setBlockState(move.from, Blocks.AIR.getDefaultState(), 3);
            if (move.enPassantCapture != null) world.setBlockState(move.enPassantCapture, Blocks.AIR.getDefaultState(), 3);
            if (move.rookFrom != null && ChessPieceType.fromBlock(move.rookState) != null) {
                world.setBlockState(move.rookTo, move.rookState, 3);
                if (world.getBlockEntity(move.rookTo) instanceof ChessFigureBlockEntity movedRook)
                    movedRook.setYawDegrees(move.rookYaw);
                world.setBlockState(move.rookFrom, Blocks.AIR.getDefaultState(), 3);
            }
            state.pending = null;
            state.selected3DPieces.clear();
            world.playSound(null, move.to, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.PLAYERS, 0.65f, 1.0f);

            if (needsPromotion && !promotionOptions(state, move.type.isWhite()).isEmpty()) {
                state.promotionPending = true;
                state.promotionWhite = move.type.isWhite();
                state.promotionRow = move.tr;
                state.promotionCol = move.tc;
                state.promotionPlayerId = move.playerId;
                for (ServerPlayerEntity player : world.getPlayers()) ChessNetwork.sendBoard(player);
                broadcast(world, "Пешка дошла до края доски. Выбери одну из реально срубленных фигур.");
            } else {
                if (needsPromotion) tellPlayerByUuid(world, move.playerId,
                    "Нет доступной срубленной фигуры для превращения: пешка остаётся пешкой.");
                state.whiteTurn = !state.whiteTurn;
                updateTeamHighlights(server, state);
                announceTurn(world, state);
                checkEndCondition(world, state);
            }
            for (ServerPlayerEntity player : world.getPlayers())
                ChessNetwork.sendHighlights(player, BlockPos.ORIGIN, -1, -1, new boolean[64]);
            ChessNetwork.broadcastBoard(world);
            ChessNetwork.broadcastClockState(world);
            if (!state.running) syncAllTeamPieces(server);
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
            if (inCheck) {
                broadcast(world, "Мат! Победа " + (whiteToMove ? "чёрных" : "белых") + ".");
                notifyCheckTeam(world, whiteToMove, "ШАХ И МАТ", SoundEvents.ENTITY_VILLAGER_NO);
                notifyCheckTeam(world, !whiteToMove, "ПОБЕДА", SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
            } else {
                broadcast(world, "Пат. Ничья.");
                for (ServerPlayerEntity player : world.getPlayers())
                    player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.9f, 0.75f);
            }
        } else if (inCheck) {
            broadcast(world, "Шах " + (whiteToMove ? "белому" : "чёрному") + " королю!");
            notifyCheckTeam(world, whiteToMove, "ШАХ", SoundEvents.BLOCK_NOTE_BLOCK_BELL.value());
        }
        ChessNetwork.broadcastClockState(world);
        if (!state.running) syncAllTeamPieces(world.getServer());
    }

    private static void tellPlayerByUuid(ServerWorld world, UUID uuid, String message) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getUuid().equals(uuid)) {
                tell(player, message);
                return;
            }
        }
    }

    private static void notifyCheckTeam(ServerWorld world, boolean white, String message, net.minecraft.sound.SoundEvent sound) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            net.minecraft.scoreboard.AbstractTeam team = player.getScoreboardTeam();
            if (team != null && (team.getName().equals("white") || team.getName().equals("black"))
                && white != team.getName().equals("white")) continue;
            player.sendMessage(Text.literal(message).formatted(
                message.contains("МАТ") ? Formatting.DARK_RED : Formatting.RED, Formatting.BOLD), true);
            player.playSound(sound, 0.9f, message.contains("ШАХ") ? 1.1f : 1.0f);
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
        public int clockMinutes = 10;
        public long whiteClockTicks = 12000L, blackClockTicks = 12000L;

        public long clockMaxTicks() { return clockMinutes * 1200L; }
        public boolean whiteKingMoved, blackKingMoved;
        public boolean whiteLeftRookMoved, whiteRightRookMoved, blackLeftRookMoved, blackRightRookMoved;
        public int enPassantRow = -1, enPassantCol = -1;
        public PendingMove pending;
        public boolean threeDimensional;
        public boolean promotionPending, promotionWhite;
        public int promotionRow = -1, promotionCol = -1;
        public UUID promotionPlayerId;
        public BlockPos whiteCaptureOrigin, blackCaptureOrigin;
        public final List<Character> whiteCapturedPieces = new ArrayList<>();
        public final List<Character> blackCapturedPieces = new ArrayList<>();
        public final Map<UUID, Integer> selected3DPieces = new HashMap<>();
        public final Map<String, BlockPos> teleportTargets = new HashMap<>();
    }

    public static final class PendingMove {
        public final BlockPos from, to, enPassantCapture, rookFrom, rookTo;
        public final BlockState movingState, rookState;
        public final ChessPieceType type;
        public final int fr, fc, tr, tc;
        public final float movingYaw, rookYaw;
        public final char capturedSymbol;
        public final long executeAt;
        public final UUID playerId;
        PendingMove(BlockPos from, BlockPos to, BlockState movingState, BlockPos enPassantCapture,
                    BlockPos rookFrom, BlockPos rookTo, BlockState rookState, ChessPieceType type,
                    int fr, int fc, int tr, int tc, float movingYaw, float rookYaw, char capturedSymbol, long executeAt, UUID playerId) {
            this.from = from; this.to = to; this.movingState = movingState; this.enPassantCapture = enPassantCapture;
            this.rookFrom = rookFrom; this.rookTo = rookTo; this.rookState = rookState; this.type = type;
            this.fr = fr; this.fc = fc; this.tr = tr; this.tc = tc;
            this.movingYaw = movingYaw; this.rookYaw = rookYaw;
            this.capturedSymbol = capturedSymbol;
            this.executeAt = executeAt; this.playerId = playerId;
        }
    }
}
