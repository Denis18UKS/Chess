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
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.world.GameMode;

/** Server-authoritative chess board and match controller. */
public final class ChessGameManager {
    public enum RuleMode { NO_REALISM, REALISM, FULL_REALISM }
    public enum MatchMode { ONE_ONE, TWO_TWO, ONE_VS_BOT }

    private static final Map<RegistryKey<World>, BoardState> BOARDS = new HashMap<>();
    private static final Set<UUID> KIT_SLOT_WARNED = new HashSet<>();
    private static final Set<UUID> CHESS_GLOWING = new HashSet<>();
    private static final Map<String, ChessWorldConfig.TriggerZone> ACTIVE_ZONE_VISITS = new HashMap<>();
    private static final Set<UUID> DISCONNECTED_DURING_ACTIVE_MATCH = new HashSet<>();
    private static final UUID BOT_PLAYER_ID = new UUID(0L, 1L);

    private ChessGameManager() {}

    public static void registerTicker() {
        ServerTickEvents.END_SERVER_TICK.register(ChessGameManager::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> handlePlayerJoin(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            server.execute(() -> handlePlayerDisconnect(handler.player)));
    }

    public static BoardState board(World world) {
        return BOARDS.computeIfAbsent(world.getRegistryKey(), key -> {
            BoardState state = new BoardState();
            if (world instanceof ServerWorld serverWorld) {
                ChessWorldConfig config = ChessWorldConfig.get(serverWorld.getServer());
                state.matchMode = config.getMatchMode();
                state.ruleMode = config.getRuleMode();
                state.matchDurationMinutes = config.getMatchDurationMinutes();
                for (String team : List.of("white", "black")) {
                    ChessWorldConfig.SavedPos saved = config.getLocation(team);
                    if (saved != null && saved.dimension.equals(serverWorld.getRegistryKey().getValue().toString()))
                        state.teleportTargets.put(team, saved.pos);
                }
            }
            return state;
        });
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
        if (!state.configured) throw new IllegalStateException("Сначала привяжите шахматное поле конфигуратором.");
        if (state.running || state.countdownActive || state.finishPending) throw new IllegalStateException("Партия уже запущена, готовится к запуску или завершается.");
        boolean anotherMatch = BOARDS.values().stream().anyMatch(other -> other.running || other.countdownActive || other.finishPending);
        if (anotherMatch) throw new IllegalStateException("Другая шахматная партия уже идёт.");

        ChessWorldConfig config = ChessWorldConfig.get(world.getServer());
        state.matchMode = config.getMatchMode();
        state.ruleMode = config.getRuleMode();
        state.matchDurationMinutes = config.getMatchDurationMinutes();
        // A scoreboard team cannot outlive a match. Saved side selections remain in world config.
        clearChessTeams(world.getServer());

        List<UUID> selectedWhite = new ArrayList<>();
        List<UUID> selectedBlack = new ArrayList<>();
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            String selected = config.getSelectedTeam(player.getUuid());
            if ("white".equals(selected)) selectedWhite.add(player.getUuid());
            else if ("black".equals(selected)) selectedBlack.add(player.getUuid());
        }

        int requiredWhite = state.matchMode == MatchMode.TWO_TWO ? 2 : 1;
        int requiredBlack = state.matchMode == MatchMode.TWO_TWO ? 2 : state.matchMode == MatchMode.ONE_VS_BOT ? 0 : 1;
        if (selectedWhite.size() != requiredWhite || selectedBlack.size() != requiredBlack) {
            String reason = "Недостаточно игроков. Для режима "
                + (state.matchMode == MatchMode.TWO_TWO ? "2v2 нужны 2 белых и 2 чёрных."
                    : state.matchMode == MatchMode.ONE_VS_BOT ? "vs_bot нужен 1 игрок, выбравший белых."
                    : "1v1 нужны 1 белый и 1 чёрный.")
                + " Выбери сторону через /chess white или /chess black.";
            showServerTitle(world.getServer(), "Недостаточно игроков");
            broadcast(world, reason);
            throw new IllegalStateException(reason);
        }

        if (state.matchEnded || boardIsEmpty(world, state)) resetBoard(world);

        state.observerOriginalModes.clear();
        for (UUID id : selectedWhite) {
            ServerPlayerEntity selected = world.getServer().getPlayerManager().getPlayer(id);
            if (selected != null) state.observerOriginalModes.put(id, selected.interactionManager.getGameMode());
        }
        for (UUID id : selectedBlack) {
            ServerPlayerEntity selected = world.getServer().getPlayerManager().getPlayer(id);
            if (selected != null) state.observerOriginalModes.put(id, selected.interactionManager.getGameMode());
        }
        state.pendingWhiteParticipants.clear();
        state.pendingWhiteParticipants.addAll(selectedWhite);
        state.pendingBlackParticipants.clear();
        state.pendingBlackParticipants.addAll(selectedBlack);
        state.participants.clear();
        state.lastParticipantTeams.clear();
        state.observers.clear();
        Set<UUID> playersForMatch = new HashSet<>(selectedWhite);
        playersForMatch.addAll(selectedBlack);
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            if (!playersForMatch.contains(player.getUuid())) state.observers.add(player.getUuid());
        }
        state.countdownActive = true;
        state.countdownFadeStarted = false;
        state.countdownTeleportAtTick = -1L;
        state.countdownRemaining = 3;
        state.countdownNextTick = serverTick(world.getServer()) + 1L;
        state.returnToLobbyAtTick = -1L;
        state.matchEnded = false;
        broadcast(world, "Подготовка партии: 3… 2… 1…");
    }

    private static void beginMatch(ServerWorld world) {
        BoardState state = board(world);
        if (!state.configured) throw new IllegalStateException("Сначала привяжите шахматное поле конфигуратором.");
        if (state.running) throw new IllegalStateException("Партия уже запущена.");
        ChessWorldConfig config = ChessWorldConfig.get(world.getServer());
        state.matchMode = config.getMatchMode();
        state.ruleMode = config.getRuleMode();
        state.matchDurationMinutes = config.getMatchDurationMinutes();
        state.running = true;
        state.paused = false;
        state.pausedByDisconnect = false;
        state.matchEnded = false;
        state.returnToLobbyAtTick = -1L;
        state.whiteTurn = true;
        state.pending = null;
        state.promotionPending = false;
        state.botMoveAtTick = state.matchMode == MatchMode.ONE_VS_BOT ? serverTick(world.getServer()) + 20L : -1L;
        state.matchDeadlineAtTick = state.matchDurationMinutes > 0
            ? serverTick(world.getServer()) + state.matchDurationMinutes * 1200L : -1L;
        state.whiteClockTicks = state.blackClockTicks = state.clockMaxTicks();
        state.participants.clear();
        state.lastParticipantTeams.clear();
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            net.minecraft.scoreboard.AbstractTeam team = player.getScoreboardTeam();
            if (team != null && (team.getName().equals("white") || team.getName().equals("black"))) {
                state.participants.add(player.getUuid());
                state.lastParticipantTeams.put(player.getUuid(), team.getName());
            }
        }
        autoConfigureCaptures(world, state);
        broadcast(world, state.matchMode == MatchMode.ONE_VS_BOT
            ? "Шахматная партия началась: ты играешь против бота. Первый ход — белые."
            : "Шахматная партия началась. Первый ход — белые.");
        updateTeamHighlights(world.getServer(), state);
        updateTurnGlow(world, state);
        syncAllTeamPieces(world.getServer());
        ChessNetwork.broadcastBoard(world);
        ChessNetwork.broadcastClockState(world);
    }

    /** Force-completes the active match and schedules the lobby return. */
    public static void stop(ServerWorld world, String message) {
        finishMatch(world, message);
    }

    private static void finishMatch(ServerWorld world, String message) {
        BoardState state = board(world);
        if (state.finishPending || (!state.running && !state.countdownActive)) return;

        state.running = false;
        state.countdownActive = false;
        state.countdownFadeStarted = false;
        state.countdownRemaining = 0;
        state.pending = null;
        state.promotionPending = false;
        state.selected3DPieces.clear();
        state.paused = false;
        state.pausedByDisconnect = false;
        state.disconnectedPlayerId = null;
        state.disconnectedTeamName = "";
        for (UUID participant : state.participants) DISCONNECTED_DURING_ACTIVE_MATCH.remove(participant);

        state.matchEnded = true;
        state.matchDeadlineAtTick = -1L;
        state.returnToLobbyAtTick = -1L;
        state.botMoveAtTick = -1L;
        state.finishPending = true;

        ChessWorldConfig config = ChessWorldConfig.get(world.getServer());
        int fadeTicks = config.getFadeDurationTicks();
        int delayTicks = config.getEffectiveTeleportDelayTicks();
        state.finishTeleportAtTick = serverTick(world.getServer()) + delayTicks;

        updateTurnGlow(world, state);
        syncAllTeamPieces(world.getServer());
        clearChessTeams(world.getServer());
        config.clearSelectedTeams();
        ChessNetwork.broadcastBoard(world);
        ChessNetwork.broadcastClockState(world);
        world.getServer().getPlayerManager().broadcast(Text.literal(message), false);

        for (UUID observerId : new HashSet<>(state.observers)) {
            ServerPlayerEntity observer = world.getServer().getPlayerManager().getPlayer(observerId);
            if (observer != null) ChessNetwork.sendSpectatorState(observer, false, state.whiteTurn);
        }
        beginFadeTransition(world.getServer(), state, fadeTicks, delayTicks);
    }

    private static void beginFadeTransition(MinecraftServer server, BoardState state, int fadeTicks, int delayTicks) {
        int fade = Math.max(2, fadeTicks);
        int delay = Math.max(fade, delayTicks);
        int hold = Math.max(0, delay - fade);
        Set<UUID> affected = new HashSet<>(state.participants);
        affected.addAll(state.observers);
        affected.addAll(state.pendingWhiteParticipants);
        affected.addAll(state.pendingBlackParticipants);
        for (Map.Entry<RegistryKey<World>, BoardState> entry : BOARDS.entrySet()) {
            if (entry.getValue() == state) {
                ServerWorld source = server.getWorld(entry.getKey());
                if (source != null) for (ServerPlayerEntity player : source.getPlayers()) affected.add(player.getUuid());
                break;
            }
        }
        for (UUID uuid : affected) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) ChessNetwork.sendFadeTransition(player, fade, hold, fade);
        }
    }

    private static void completeMatchFinish(ServerWorld world, BoardState state) {
        state.finishPending = false;
        state.finishTeleportAtTick = -1L;
        teleportAllToLobby(world);
        clearChessTeams(world.getServer());
        ChessWorldConfig.get(world.getServer()).clearSelectedTeams();
        state.observers.clear();
        state.observerOriginalModes.clear();
        state.participants.clear();
        state.pendingWhiteParticipants.clear();
        state.pendingBlackParticipants.clear();
        state.countdownActive = false;
        state.countdownFadeStarted = false;
        state.countdownRemaining = 0;
        state.countdownNextTick = -1L;
        state.lastParticipantTeams.clear();
        resetBoard(world);
    }

    public static void togglePause(ServerWorld world) {
        BoardState state = board(world);
        if (!state.running) throw new IllegalStateException("Партия не запущена.");
        state.paused = !state.paused;
        state.pausedByDisconnect = false;
        broadcast(world, state.paused ? "Шахматная партия приостановлена." : "Шахматная партия продолжена.");
        updateTurnGlow(world, state);
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

    public static void handleAdminAction(ServerPlayerEntity player, String action, BlockPos pos,
                                         String a, String b, String c, int v1, int v2, int v3) {
        boolean publicPanelAction = java.util.Set.of("get_panel_state", "panel_mode", "select_white", "select_black",
            "clear_selection", "panel_start").contains(action);
        if (!"get_settings".equals(action) && !"get_fade_settings".equals(action)
            && !publicPanelAction && !player.getCommandSource().hasPermissionLevel(2)) {
            tell(player, "Для настройки панели требуется право оператора.");
            return;
        }
        ServerWorld world = player.getServerWorld();
        ChessWorldConfig config = ChessWorldConfig.get(player.getServer());
        BoardState state = board(world);
        try {
            switch (action) {
                case "get_settings":
                    ChessNetwork.sendAdminState(player, state);
                    return;
                case "get_fade_settings":
                    ChessNetwork.sendFadeSettings(player, config);
                    return;
                case "get_panel_state":
                    ChessNetwork.sendPanelMode(player, config.getMatchMode());
                    return;
                case "panel_mode": {
                    if (state.running || state.countdownActive || state.finishPending) {
                        tell(player, "Нельзя менять режим во время подготовки, партии или завершения.");
                        return;
                    }
                    MatchMode mode = v1 == 1 ? MatchMode.TWO_TWO : v1 == 2 ? MatchMode.ONE_VS_BOT : MatchMode.ONE_ONE;
                    config.setMatchMode(mode);
                    state.matchMode = mode;
                    Map<java.util.UUID, String> selected = config.getSelectedTeams();
                    int sideLimit = mode == MatchMode.TWO_TWO ? 2 : 1;
                    for (String side : new String[] {"white", "black"}) {
                        List<java.util.UUID> owners = selected.entrySet().stream()
                            .filter(choice -> side.equals(choice.getValue()))
                            .map(Map.Entry::getKey)
                            .sorted(java.util.Comparator.comparing(java.util.UUID::toString)).toList();
                        int allowed = mode == MatchMode.ONE_VS_BOT && "black".equals(side) ? 0 : sideLimit;
                        for (int i = allowed; i < owners.size(); i++) config.clearSelectedTeam(owners.get(i));
                    }
                    ChessNetwork.broadcastPanelMode(player.getServer(), mode);
                    tell(player, "Выбран режим: " + (mode == MatchMode.ONE_ONE ? "1v1" : mode == MatchMode.TWO_TWO ? "2v2" : "vs_bot"));
                    return;
                }
                case "select_white":
                    selectTeam(player, "white");
                    return;
                case "select_black":
                    selectTeam(player, "black");
                    return;
                case "clear_selection":
                    clearTeamSelection(player);
                    return;
                case "panel_start":
                    start(world);
                    return;
                case "set_fade":
                    config.setFadeDurationTicks(v1);
                    config.setTeleportDelayTicks(v2);
                    tell(player, "Fade сохранён: затемнение " + String.format(java.util.Locale.ROOT, "%.1f", config.getFadeDurationTicks() * 0.05)
                        + " с; задержка " + String.format(java.util.Locale.ROOT, "%.1f", config.getTeleportDelayTicks() * 0.05) + " с. "
                        + "Фактическая задержка не меньше длительности затемнения.");
                    return;
                case "set_spectator":
                    config.setLocation("spectator", world, pos, player.getYaw(), player.getPitch());
                    tell(player, "Точка наблюдения сохранена: " + pos.toShortString());
                    return;
                case "zone_white_corner_a":
                    config.saveZoneCorner(world, pos, "team_white");
                    tell(player, "Первый угол зоны белых сохранён. Отметь второй угол.");
                    return;
                case "zone_white_define":
                    config.defineZone("team_white", world, pos, "chess white", "");
                    tell(player, "Триггерная зона белых сохранена.");
                    return;
                case "zone_black_corner_a":
                    config.saveZoneCorner(world, pos, "team_black");
                    tell(player, "Первый угол зоны чёрных сохранён. Отметь второй угол.");
                    return;
                case "zone_black_define":
                    config.defineZone("team_black", world, pos, "chess black", "");
                    tell(player, "Триггерная зона чёрных сохранена.");
                    return;
                case "start_match":
                    start(world);
                    return;
                case "settings":
                    MatchMode mode = v1 == 1 ? MatchMode.TWO_TWO : v1 == 2 ? MatchMode.ONE_VS_BOT : MatchMode.ONE_ONE;
                    RuleMode rules = v3 == 0 ? RuleMode.NO_REALISM : v3 == 2 ? RuleMode.FULL_REALISM : RuleMode.REALISM;
                    config.setMatchMode(mode);
                    config.setMatchDurationMinutes(v2);
                    config.setRuleMode(rules);
                    ChessNetwork.broadcastPanelMode(player.getServer(), mode);
                    if (!state.running) {
                        state.matchMode = mode;
                        state.matchDurationMinutes = Math.max(0, Math.min(1440, v2));
                        state.ruleMode = rules;
                    }
                    tell(player, state.running
                        ? "Настройки сохранены для следующей партии."
                        : "Настройки сохранены в мире и применены.");
                    return;
                case "set_lobby":
                    config.setLocation("lobby", world, pos, player.getYaw(), player.getPitch());
                    tell(player, "Точка лобби сохранена в мире: " + pos.toShortString());
                    return;
                case "set_tp_white":
                    state.teleportTargets.put("white", pos.toImmutable());
                    config.setLocation("white", world, pos, player.getYaw(), player.getPitch());
                    tell(player, "Точка белых сохранена: " + pos.toShortString());
                    return;
                case "set_tp_black":
                    state.teleportTargets.put("black", pos.toImmutable());
                    config.setLocation("black", world, pos, player.getYaw(), player.getPitch());
                    tell(player, "Точка чёрных сохранена: " + pos.toShortString());
                    return;
                case "bind_command":
                    config.bindBlock(world, pos, a);
                    tell(player, a == null || a.isBlank()
                        ? "Привязка команды к блоку снята."
                        : "Команда привязана к блоку " + pos.toShortString() + ".");
                    return;
                case "zone_corner_a":
                    config.saveZoneCorner(world, pos);
                    tell(player, "Угол A зоны сохранён. Используй конфигуратор на блоке второго угла и нажми «Создать зону B».");
                    return;
                case "zone_define":
                    config.defineZone(a, world, pos, b, c);
                    tell(player, "Триггер-зона сохранена в данных мира.");
                    return;
                default:
                    tell(player, "Неизвестное действие конфигуратора: " + action);
            }
        } catch (IllegalStateException exception) {
            tell(player, exception.getMessage());
        }
    }

    public static void setLobbyDelay(MinecraftServer server, int seconds) {
        int value = Math.max(0, Math.min(3600, seconds));
        ChessWorldConfig.get(server).setLobbyDelaySeconds(value);
        server.getPlayerManager().broadcast(Text.literal("Задержка возврата в лобби установлена: " + value + " сек."), false);
    }

    /** Stores a pre-match side choice without adding the player to a Minecraft scoreboard team. */
    public static boolean selectTeam(ServerPlayerEntity player, String teamName) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        if (!"white".equals(teamName) && !"black".equals(teamName)) return false;
        if (BOARDS.values().stream().anyMatch(state -> state.running || state.countdownActive || state.finishPending)) {
            player.sendMessage(Text.literal("Нельзя менять сторону во время подготовки или активной партии."), false);
            return false;
        }
        ChessWorldConfig config = ChessWorldConfig.get(server);
        ChessGameManager.MatchMode mode = config.getMatchMode();
        if (mode == MatchMode.ONE_VS_BOT && "black".equals(teamName)) {
            player.sendMessage(Text.literal("В режиме vs_bot чёрными играет бот. Выбери сторону «Белые».").formatted(Formatting.WHITE), false);
            return false;
        }
        int limit = mode == MatchMode.TWO_TWO ? 2 : 1;
        long occupied = config.getSelectedTeams().entrySet().stream()
            .filter(entry -> teamName.equals(entry.getValue()) && !entry.getKey().equals(player.getUuid()))
            .filter(entry -> server.getPlayerManager().getPlayer(entry.getKey()) != null)
            .count();
        if (occupied >= limit) {
            player.sendMessage(Text.literal("Сторона " + (teamName.equals("white") ? "Белые" : "Чёрные")
                + " уже набрала максимум игроков для этого режима."), false);
            return false;
        }
        config.setSelectedTeam(player.getUuid(), teamName);
        Formatting color = teamName.equals("white") ? Formatting.WHITE : Formatting.DARK_GRAY;
        player.sendMessage(Text.literal("Предварительно выбрана сторона: "
            + (teamName.equals("white") ? "Белые" : "Чёрные")
            + ". До начала партии ты не состоишь в Minecraft-команде.").formatted(color), false);
        return true;
    }

    public static boolean clearTeamSelection(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null || BOARDS.values().stream().anyMatch(state -> state.running || state.countdownActive || state.finishPending)) {
            player.sendMessage(Text.literal("Нельзя менять выбор команды во время партии."), false);
            return false;
        }
        ChessWorldConfig.get(server).clearSelectedTeam(player.getUuid());
        clearChessTeam(server, player);
        player.sendMessage(Text.literal("Предварительный выбор команды сброшен."), false);
        return true;
    }

    public static boolean toggleSpectating(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        for (Map.Entry<RegistryKey<World>, BoardState> entry : BOARDS.entrySet()) {
            BoardState state = entry.getValue();
            ServerWorld boardWorld = server.getWorld(entry.getKey());
            if (boardWorld == null || !state.running) continue;
            if (state.participants.contains(player.getUuid())) {
                player.sendMessage(Text.literal("Участник партии не может переключиться в режим зрителя."), false);
                return false;
            }
            if (state.observers.remove(player.getUuid())) {
                ChessNetwork.sendSpectatorState(player, false, state.whiteTurn);
                GameMode oldMode = state.observerOriginalModes.remove(player.getUuid());
                player.changeGameMode(oldMode == null ? GameMode.ADVENTURE : oldMode);
                teleportPlayerToLobby(player);
                player.sendMessage(Text.literal("Ты покинул наблюдение и вернулся в лобби."), false);
                return true;
            }
            state.observers.add(player.getUuid());
            state.observerOriginalModes.put(player.getUuid(), player.interactionManager.getGameMode());
            player.changeGameMode(GameMode.SPECTATOR);
            teleportToSpectator(player, boardWorld, state);
            ChessNetwork.sendSpectatorState(player, true, state.whiteTurn);
            player.sendMessage(Text.literal("Режим наблюдения включён. Нажми O ещё раз, чтобы вернуться в лобби."), false);
            return true;
        }
        player.sendMessage(Text.literal("Сейчас нет активной шахматной партии для наблюдения."), false);
        return false;
    }

    private static void teleportToSpectator(ServerPlayerEntity player, ServerWorld boardWorld, BoardState state) {
        ChessWorldConfig.SavedPos target = ChessWorldConfig.get(player.getServer()).getLocation("spectator");
        if (target != null) {
            ServerWorld destination = resolveWorld(player.getServer(), target.dimension);
            if (destination != null) {
                BlockPos feet = findSafeFeetPosition(destination, target.pos, new HashSet<>(), Set.of(player.getUuid()));
                if (feet != null) {
                    player.teleport(destination, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                        target.yaw, target.pitch);
                    return;
                }
            }
        }
        BlockPos fallback = state.origin.add(4, 4, -3);
        BlockPos feet = findSafeFeetPosition(boardWorld, fallback, new HashSet<>(), Set.of(player.getUuid()));
        if (feet != null) player.teleport(boardWorld, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0f, 25.0f);
        else player.sendMessage(Text.literal("Не найдено безопасное место наблюдения. Настрой точку наблюдения."), false);
    }

    private static void processStartCountdowns(MinecraftServer server, long tick) {
        for (Map.Entry<RegistryKey<World>, BoardState> entry : new ArrayList<>(BOARDS.entrySet())) {
            BoardState state = entry.getValue();
            if (!state.countdownActive) continue;
            if (state.countdownFadeStarted) {
                if (tick < state.countdownTeleportAtTick) continue;
            } else if (tick < state.countdownNextTick) {
                continue;
            }
            ServerWorld world = server.getWorld(entry.getKey());
            if (world == null) continue;
            boolean rosterOnline = state.pendingWhiteParticipants.stream().allMatch(id -> server.getPlayerManager().getPlayer(id) != null)
                && state.pendingBlackParticipants.stream().allMatch(id -> server.getPlayerManager().getPlayer(id) != null);
            if (!rosterOnline) {
                state.countdownActive = false;
                state.countdownRemaining = 0;
                state.countdownNextTick = -1L;
                state.pendingWhiteParticipants.clear();
                state.pendingBlackParticipants.clear();
                state.observers.clear();
                state.observerOriginalModes.clear();
                showServerTitle(server, "Недостаточно игроков");
                broadcast(world, "Недостаточно игроков. Запуск партии отменён.");
                continue;
            }
            if (state.countdownRemaining > 0) {
                showServerTitle(server, Integer.toString(state.countdownRemaining));
                state.countdownRemaining--;
                state.countdownNextTick = tick + 20L;
                continue;
            }
            if (!state.countdownFadeStarted) {
                ChessWorldConfig config = ChessWorldConfig.get(server);
                int fadeTicks = config.getFadeDurationTicks();
                int delayTicks = config.getEffectiveTeleportDelayTicks();
                state.countdownFadeStarted = true;
                state.countdownTeleportAtTick = tick + delayTicks;
                state.countdownNextTick = state.countdownTeleportAtTick;
                beginFadeTransition(server, state, fadeTicks, delayTicks);
                continue;
            }

            state.countdownActive = false;
            state.countdownFadeStarted = false;
            state.countdownTeleportAtTick = -1L;
            Scoreboard scoreboard = server.getScoreboard();
            Team white = ensureChessTeam(scoreboard, "white");
            Team black = ensureChessTeam(scoreboard, "black");
            for (UUID id : state.pendingWhiteParticipants) {
                ServerPlayerEntity selected = server.getPlayerManager().getPlayer(id);
                if (selected != null) {
                    clearChessTeam(server, selected);
                    scoreboard.addPlayerToTeam(selected.getEntityName(), white);
                }
            }
            for (UUID id : state.pendingBlackParticipants) {
                ServerPlayerEntity selected = server.getPlayerManager().getPlayer(id);
                if (selected != null) {
                    clearChessTeam(server, selected);
                    scoreboard.addPlayerToTeam(selected.getEntityName(), black);
                }
            }

            // Everyone who was already online but did not join the roster is forced into
            // observing. Players who join later stay in the lobby until they opt in.
            for (UUID id : new HashSet<>(state.observers)) {
                ServerPlayerEntity observer = server.getPlayerManager().getPlayer(id);
                if (observer == null) continue;
                state.observerOriginalModes.put(id, observer.interactionManager.getGameMode());
                observer.changeGameMode(GameMode.SPECTATOR);
                teleportToSpectator(observer, world, state);
            }
            if (!teleportTeamToConfiguredTarget(world, true)) teleportRosterToBoard(world, state, state.pendingWhiteParticipants, true);
            if (state.matchMode != MatchMode.ONE_VS_BOT && !teleportTeamToConfiguredTarget(world, false))
                teleportRosterToBoard(world, state, state.pendingBlackParticipants, false);

            beginMatch(world);
            for (UUID id : new HashSet<>(state.observers)) {
                ServerPlayerEntity observer = server.getPlayerManager().getPlayer(id);
                if (observer != null) ChessNetwork.sendSpectatorState(observer, true, state.whiteTurn);
            }
            ChessWorldConfig.get(server).clearSelectedTeams();
            state.pendingWhiteParticipants.clear();
            state.pendingBlackParticipants.clear();
        }
    }

    private static void teleportRosterToBoard(ServerWorld world, BoardState state, Set<UUID> ids, boolean white) {
        List<UUID> ordered = ids.stream().sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        Set<UUID> moving = new HashSet<>(ids);
        Set<BlockPos> reserved = new HashSet<>();
        List<ServerPlayerEntity> players = new ArrayList<>();
        List<BlockPos> positions = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(ordered.get(index));
            if (player == null) continue;
            BlockPos base = state.origin.add(index * 2, 0, white ? -3 : 10);
            BlockPos feet = findSafeFeetPosition(world, base, reserved, moving);
            if (feet == null) {
                player.sendMessage(Text.literal("Не найдено безопасное место у шахматной доски. Проверь расположение поля."), false);
                continue;
            }
            reserved.add(feet);
            players.add(player);
            positions.add(feet);
        }
        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity player = players.get(i);
            BlockPos feet = positions.get(i);
            player.teleport(world, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                white ? 180.0f : 0.0f, 0.0f);
        }
    }

    private static void showServerTitle(MinecraftServer server, String message) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(message)));
        }
    }

    private static Team ensureChessTeam(Scoreboard scoreboard, String name) {
        Team team = scoreboard.getTeam(name);
        if (team == null) team = scoreboard.addTeam(name);
        Formatting color = "white".equals(name) ? Formatting.WHITE : Formatting.DARK_GRAY;
        team.setDisplayName(Text.literal("white".equals(name) ? "Белые" : "Чёрные").formatted(color));
        team.setColor(color);
        team.setFriendlyFireAllowed(false);
        return team;
    }

    private static void clearChessTeam(MinecraftServer server, ServerPlayerEntity player) {
        var team = player.getScoreboardTeam();
        if (team != null && ("white".equals(team.getName()) || "black".equals(team.getName())))
            server.getScoreboard().clearPlayerTeam(player.getEntityName());
    }

    private static void clearChessTeams(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        for (String name : new String[] {"white", "black"}) {
            Team team = scoreboard.getTeam(name);
            if (team == null) continue;
            for (String member : new ArrayList<>(team.getPlayerList())) scoreboard.clearPlayerTeam(member);
            team.setDisplayName(Text.literal(name.equals("white") ? "Белые" : "Чёрные")
                .formatted(name.equals("white") ? Formatting.WHITE : Formatting.DARK_GRAY));
            team.setColor(name.equals("white") ? Formatting.WHITE : Formatting.DARK_GRAY);
        }
    }

    public static void handlePlayerJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ChessWorldConfig config = ChessWorldConfig.get(server);
        boolean returningDuringGame = DISCONNECTED_DURING_ACTIVE_MATCH.remove(player.getUuid());
        config.rememberPlayer(player.getUuid());

        boolean anyActiveMatch = BOARDS.values().stream().anyMatch(s -> s.running);
        boolean anyCountdown = BOARDS.values().stream().anyMatch(s -> s.countdownActive);
        boolean activeParticipant = BOARDS.values().stream().anyMatch(s -> s.running && s.participants.contains(player.getUuid()));

        // Only a reconnecting active participant can restore a match state. Spectators and
        // late joiners go back to the lobby and must opt into observing again.
        if (!activeParticipant || !returningDuringGame || (!anyActiveMatch && !anyCountdown)) {
            for (BoardState state : BOARDS.values()) {
                state.observers.remove(player.getUuid());
                state.observerOriginalModes.remove(player.getUuid());
            }
            clearChessTeam(server, player);
            if (!anyActiveMatch && !anyCountdown) config.clearSelectedTeam(player.getUuid());
            teleportPlayerToLobby(player);
            if (anyActiveMatch) player.sendMessage(Text.literal("Партия уже идёт. Нажми O или используй /chess spectate, чтобы смотреть игру."), false);
            return;
        }

        for (Map.Entry<RegistryKey<World>, BoardState> entry : BOARDS.entrySet()) {
            BoardState state = entry.getValue();
            if (!state.pausedByDisconnect) continue;
            boolean keyPlayerReturned = player.getUuid().equals(state.disconnectedPlayerId);
            String teamName = state.lastParticipantTeams.get(player.getUuid());
            boolean teammateReturned = state.matchMode == MatchMode.TWO_TWO
                && state.participants.contains(player.getUuid())
                && teamName != null && teamName.equals(state.disconnectedTeamName);
            if (!keyPlayerReturned && !teammateReturned) continue;
            state.paused = false;
            state.pausedByDisconnect = false;
            state.disconnectedPlayerId = null;
            state.disconnectedTeamName = "";
            ServerWorld boardWorld = server.getWorld(entry.getKey());
            if (boardWorld != null) {
                broadcast(boardWorld, teammateReturned && !keyPlayerReturned
                    ? "Игрок команды вернулся. Партия автоматически продолжена."
                    : "Ключевой игрок вернулся. Партия автоматически продолжена.");
                updateTurnGlow(boardWorld, state);
                ChessNetwork.broadcastClockState(boardWorld);
            }
        }
    }

    public static void handlePlayerDisconnect(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        boolean activeParticipant = BOARDS.values().stream()
            .anyMatch(state -> state.running && state.participants.contains(player.getUuid()));
        if (activeParticipant) {
            DISCONNECTED_DURING_ACTIVE_MATCH.add(player.getUuid());
        } else {
            DISCONNECTED_DURING_ACTIVE_MATCH.remove(player.getUuid());
            ChessWorldConfig.get(server).clearSelectedTeam(player.getUuid());
            clearChessTeam(server, player);
            for (BoardState state : BOARDS.values()) {
                state.observers.remove(player.getUuid());
                state.observerOriginalModes.remove(player.getUuid());
            }
        }
        for (Map.Entry<RegistryKey<World>, BoardState> entry : BOARDS.entrySet()) {
            BoardState state = entry.getValue();
            ServerWorld world = server.getWorld(entry.getKey());
            if (world == null || !state.running || !state.participants.contains(player.getUuid())) continue;
            if (state.paused) continue;
            String teamName = state.lastParticipantTeams.get(player.getUuid());
            if (!"white".equals(teamName) && !"black".equals(teamName)) continue;
            boolean shouldPause;
            if (state.matchMode == MatchMode.ONE_ONE) shouldPause = true;
            else if (state.matchMode == MatchMode.ONE_VS_BOT) shouldPause = "white".equals(teamName);
            else shouldPause = server.getPlayerManager().getPlayerList().stream()
                .noneMatch(other -> !other.getUuid().equals(player.getUuid())
                    && state.participants.contains(other.getUuid())
                    && teamName.equals(state.lastParticipantTeams.get(other.getUuid())));
            if (!shouldPause) continue;
            state.paused = true;
            state.pausedByDisconnect = true;
            state.disconnectedPlayerId = player.getUuid();
            state.disconnectedTeamName = teamName;
            broadcast(world, "Пауза: игрок команды " + ("white".equals(teamName) ? "Белые" : "Чёрные")
                + " отключился. Игра продолжится автоматически после его возвращения.");
            updateTurnGlow(world, state);
            ChessNetwork.broadcastClockState(world);
            break;
        }
    }

    private static void teleportPlayerToLobby(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ChessWorldConfig.SavedPos configuredLobby = ChessWorldConfig.get(server).getLocation("lobby");
        ServerWorld destination = configuredLobby == null ? server.getOverworld()
            : resolveWorld(server, configuredLobby.dimension);
        if (destination == null) {
            configuredLobby = null;
            destination = server.getOverworld();
        }
        BlockPos lobby = configuredLobby == null ? destination.getSpawnPos() : configuredLobby.pos;
        BlockPos feet = findSafeFeetPosition(destination, lobby, new HashSet<>(), Set.of(player.getUuid()));
        if (feet == null) {
            player.sendMessage(Text.literal("Не удалось найти безопасную точку лобби. Проверь настройки лобби."), false);
            return;
        }
        player.teleport(destination, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
            configuredLobby == null ? player.getYaw() : configuredLobby.yaw,
            configuredLobby == null ? player.getPitch() : configuredLobby.pitch);
    }

    /**
     * Find an unoccupied two-block-high position with a solid floor. Player UUIDs in
     * movingPlayers are being relocated in the same batch and are handled by reservedFeet.
     */
    private static BlockPos findSafeFeetPosition(ServerWorld world, BlockPos base,
                                                  Set<BlockPos> reservedFeet, Set<UUID> movingPlayers) {
        // Search increasingly wider rings and several heights, so a configured point
        // above an empty pit or against a wall cannot send a player into blocks.
        int[] verticalOffsets = {0, 1, 2, 3, 4, 5, -1, -2, -3, -4, -5, -6, -8, -10};
        BlockPos found = searchSafeFeet(world, base, 12, verticalOffsets, reservedFeet, movingPlayers);
        if (found != null) return found;

        // A misconfigured custom point may have no walkable floor. In that case look near
        // the world's own spawn rather than falling back to an unchecked coordinate.
        BlockPos worldSpawn = world.getSpawnPos();
        int[] spawnOffsets = {0, 1, 2, 3, 4, 5, 6, 8, 10, 12, -1, -2, -3, -4, -5};
        return searchSafeFeet(world, worldSpawn, 24, spawnOffsets, reservedFeet, movingPlayers);
    }

    private static BlockPos searchSafeFeet(ServerWorld world, BlockPos base, int maxRadius, int[] verticalOffsets,
                                           Set<BlockPos> reservedFeet, Set<UUID> movingPlayers) {
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy : verticalOffsets) {
                        BlockPos floor = new BlockPos(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                        BlockPos feet = floor.up();
                        if (reservedFeet.contains(feet)) continue;
                        if (!world.getBlockState(floor).isSolidBlock(world, floor)) continue;
                        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) continue;
                        BlockPos head = feet.up();
                        if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) continue;
                        net.minecraft.util.math.Box target = new net.minecraft.util.math.Box(
                            feet.getX() + 0.2, feet.getY(), feet.getZ() + 0.2,
                            feet.getX() + 0.8, feet.getY() + 1.8, feet.getZ() + 0.8);
                        boolean occupied = world.getPlayers().stream()
                            .filter(other -> !movingPlayers.contains(other.getUuid()))
                            .anyMatch(other -> other.getBoundingBox().intersects(target));
                        if (!occupied) return feet;
                    }
                }
            }
        }
        return null;
    }

    private static ServerWorld resolveWorld(MinecraftServer server, String dimension) {
        try {
            return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(dimension)));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static boolean teleportTeamToConfiguredTarget(ServerWorld callerWorld, boolean white) {
        MinecraftServer server = callerWorld.getServer();
        String teamName = white ? "white" : "black";
        ChessWorldConfig.SavedPos target = ChessWorldConfig.get(server).getLocation(teamName);
        if (target == null) return false;
        ServerWorld destination = resolveWorld(server, target.dimension);
        if (destination == null) return false;
        List<ServerPlayerEntity> members = server.getPlayerManager().getPlayerList().stream()
            .filter(p -> p.getScoreboardTeam() != null && p.getScoreboardTeam().getName().equals(teamName))
            .sorted(java.util.Comparator.comparing(p -> p.getUuid().toString())).toList();
        if (members.isEmpty()) return false;
        Set<UUID> moving = new HashSet<>();
        members.forEach(p -> moving.add(p.getUuid()));
        Set<BlockPos> reserved = new HashSet<>();
        List<BlockPos> destinations = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            BlockPos feet = findSafeFeetPosition(destination, target.pos, reserved, moving);
            if (feet == null) {
                server.getPlayerManager().broadcast(Text.literal("Не найдено безопасное место появления для команды "
                    + (white ? "Белые" : "Чёрные") + ". Использована резервная позиция у шахматной доски."), false);
                return false;
            }
            reserved.add(feet);
            destinations.add(feet);
        }
        for (int i = 0; i < members.size(); i++) {
            ServerPlayerEntity member = members.get(i);
            BlockPos feet = destinations.get(i);
            member.teleport(destination, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, target.yaw, target.pitch);
        }
        return true;
    }

    private static void teleportAllToLobby(ServerWorld sourceWorld) {
        MinecraftServer server = sourceWorld.getServer();
        ChessWorldConfig.SavedPos configuredLobby = ChessWorldConfig.get(server).getLocation("lobby");
        ServerWorld destination = configuredLobby == null ? server.getOverworld()
            : resolveWorld(server, configuredLobby.dimension);
        if (destination == null) {
            configuredLobby = null;
            destination = server.getOverworld();
        }
        BlockPos lobby = configuredLobby == null ? destination.getSpawnPos() : configuredLobby.pos;

        Set<UUID> returnIds = new HashSet<>(board(sourceWorld).participants);
        returnIds.addAll(board(sourceWorld).observers);
        returnIds.addAll(board(sourceWorld).pendingWhiteParticipants);
        returnIds.addAll(board(sourceWorld).pendingBlackParticipants);
        for (ServerPlayerEntity player : sourceWorld.getPlayers()) returnIds.add(player.getUuid());
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList().stream()
            .filter(player -> returnIds.contains(player.getUuid()))
            .sorted(java.util.Comparator.comparing(player -> player.getUuid().toString())).toList();
        Set<UUID> moving = new HashSet<>();
        players.forEach(p -> moving.add(p.getUuid()));
        Set<BlockPos> reserved = new HashSet<>();
        List<BlockPos> destinations = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            BlockPos feet = findSafeFeetPosition(destination, lobby, reserved, moving);
            if (feet == null) {
                server.getPlayerManager().broadcast(Text.literal("Не найдено безопасное место в лобби для всех игроков. Проверь точку лобби."), false);
                return;
            }
            reserved.add(feet);
            destinations.add(feet);
        }
        BoardState state = board(sourceWorld);
        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity player = players.get(i);
            BlockPos feet = destinations.get(i);
            player.teleport(destination, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                configuredLobby == null ? player.getYaw() : configuredLobby.yaw,
                configuredLobby == null ? player.getPitch() : configuredLobby.pitch);
            player.getCommandTags().remove("chess_lobby_return");
            GameMode oldMode = state.observerOriginalModes.remove(player.getUuid());
            player.changeGameMode(oldMode == null ? GameMode.ADVENTURE : oldMode);
            ChessNetwork.sendSpectatorState(player, false, state.whiteTurn);
        }
    }

    private static void updateTurnGlow(ServerWorld world, BoardState state) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            net.minecraft.scoreboard.AbstractTeam team = player.getScoreboardTeam();
            boolean chessTeam = team != null && (team.getName().equals("white") || team.getName().equals("black"));
            boolean botSide = state.matchMode == MatchMode.ONE_VS_BOT && team != null && team.getName().equals("black");
            boolean isTurnTeam = chessTeam && !botSide && state.running && !state.paused
                && state.whiteTurn == team.getName().equals("white");
            if (isTurnTeam) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, true, false, true));
                CHESS_GLOWING.add(player.getUuid());
            } else if (CHESS_GLOWING.remove(player.getUuid())) {
                player.removeStatusEffect(StatusEffects.GLOWING);
            }
        }
    }

    private static void runConfiguredCommand(MinecraftServer server, ServerWorld world,
                                             ServerPlayerEntity player, BlockPos pos, String command) {
        if (command == null || command.isBlank()) return;
        try {
            var source = player.getCommandSource().withLevel(4).withWorld(world)
                .withPosition(player.getPos());
            server.getCommandManager().executeWithPrefix(source, ChessWorldConfig.cleanCommand(command));
        } catch (RuntimeException exception) {
            player.sendMessage(Text.literal("Команда триггера не выполнена: " + exception.getMessage()), false);
        }
    }

    private static void processTriggerZones(MinecraftServer server) {
        ChessWorldConfig config = ChessWorldConfig.get(server);
        Set<String> nowInside = new HashSet<>();
        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                for (ChessWorldConfig.TriggerZone zone : config.getZones()) {
                    if (!zone.contains(world, player)) continue;
                    String key = player.getUuid() + "|" + zone.name;
                    nowInside.add(key);
                    if (!ACTIVE_ZONE_VISITS.containsKey(key)) {
                        ACTIVE_ZONE_VISITS.put(key, zone);
                        runConfiguredCommand(server, world, player, zone.a, zone.enterCommand);
                    }
                }
            }
        }
        for (String key : new HashSet<>(ACTIVE_ZONE_VISITS.keySet())) {
            if (nowInside.contains(key)) continue;
            ChessWorldConfig.TriggerZone zone = ACTIVE_ZONE_VISITS.remove(key);
            int split = key.indexOf('|');
            if (split <= 0) continue;
            UUID uuid;
            try { uuid = UUID.fromString(key.substring(0, split)); }
            catch (IllegalArgumentException exception) { continue; }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) runConfiguredCommand(server, player.getServerWorld(), player, zone.b, zone.leaveCommand);
        }
    }

    public static void setRuleMode(ServerWorld world, RuleMode mode) {
        BoardState state = board(world);
        state.ruleMode = mode;
        ChessWorldConfig.get(world.getServer()).setRuleMode(mode);
        broadcast(world, "Режим правил: " + mode.name().toLowerCase() + ".");
        ChessNetwork.broadcastBoard(world);
        ChessNetwork.broadcastClockState(world);
    }

    private static boolean boardIsEmpty(ServerWorld world, BoardState state) {
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++)
            if (symbol(world.getBlockState(piecePos(state, row, col))) != '.') return false;
        return true;
    }

    public static void resetBoard(ServerWorld world) {
        BoardState state = board(world);
        // A manual reset must not bypass the fade/teleport cleanup of a running match
        // or cancel a lobby return already scheduled for an end transition.
        if (state.finishPending) return;
        if (state.running || state.countdownActive) {
            finishMatch(world, "Шахматная партия завершена сбросом доски.");
            return;
        }
        if (!state.configured) throw new IllegalStateException("Сначала привяжите доску командой /chess board set <x y z> или конфигуратором.");
        // Manual figure reset during the post-game delay must not cancel the already
        // scheduled lobby return. A regular in-game reset never creates such a delay.
        boolean keepLobbyReturn = state.matchEnded && state.returnToLobbyAtTick >= 0L;
        long savedLobbyReturnTick = state.returnToLobbyAtTick;
        state.pending = null;
        state.promotionPending = false;
        state.promotionPlayerId = null;
        state.promotionRow = state.promotionCol = -1;
        state.whiteTurn = true;
        state.paused = false;
        state.pausedByDisconnect = false;
        state.disconnectedPlayerId = null;
        state.disconnectedTeamName = "";
        for (UUID participant : state.participants) DISCONNECTED_DURING_ACTIVE_MATCH.remove(participant);
        state.running = false;
        state.matchEnded = keepLobbyReturn;
        state.returnToLobbyAtTick = keepLobbyReturn ? savedLobbyReturnTick : -1L;
        state.matchDeadlineAtTick = -1L;
        state.botMoveAtTick = -1L;
        state.whiteKingMoved = state.blackKingMoved = false;
        state.whiteLeftRookMoved = state.whiteRightRookMoved = false;
        state.blackLeftRookMoved = state.blackRightRookMoved = false;
        state.enPassantRow = state.enPassantCol = -1;
        state.whiteClockTicks = state.blackClockTicks = state.clockMaxTicks();
        state.whiteCapturedPieces.clear();
        state.blackCapturedPieces.clear();
        state.selected3DPieces.clear();
        state.countdownActive = false;
        state.countdownFadeStarted = false;
        state.countdownTeleportAtTick = -1L;
        state.finishPending = false;
        state.finishTeleportAtTick = -1L;
        state.countdownRemaining = 0;
        state.pendingWhiteParticipants.clear();
        state.pendingBlackParticipants.clear();
        updateTurnGlow(world, state);

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
        broadcast(world, "Фигуры сброшены в начальную позицию. Партия не запущена; для старта выполни /chess start.");
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
        net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();

        // Players outside the chess teams keep their own inventory completely untouched.
        // This method is called during regular synchronization, so do not repeatedly clear
        // figure items from spectators/unassigned players.
        if (!hasChessTeam) {
            KIT_SLOT_WARNED.remove(player.getUuid());
            return;
        }

        boolean white = team.getName().equals("white");
        boolean shouldHaveKit = state.running && !state.threeDimensional;
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
        updateTurnGlow(world, state);
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
        if (state.matchMode == MatchMode.ONE_VS_BOT && !type.isWhite()) {
            tell(player, "В режиме 1_vs_bot чёрными управляет компьютер.");
            return;
        }
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
        if (state.running || state.countdownActive || state.finishPending)
            throw new IllegalStateException("Режим партии нельзя менять во время подготовки, активной игры или завершения.");
        state.matchMode = mode;
        ChessWorldConfig.get(world.getServer()).setMatchMode(mode);
        String label = mode == MatchMode.ONE_ONE ? "1v1" : mode == MatchMode.TWO_TWO ? "2v2" : "vs_bot";
        broadcast(world, "Режим партии: " + label + ".");
        ChessNetwork.broadcastBoard(world);
    }

    public static void setMatchDuration(ServerWorld world, int minutes) {
        BoardState state = board(world);
        state.matchDurationMinutes = Math.max(0, Math.min(1440, minutes));
        ChessWorldConfig.get(world.getServer()).setMatchDurationMinutes(state.matchDurationMinutes);
        // An operator changing the command during a live game restarts the whole-match
        // deadline from now; panel edits during an active game are instead queued for next time.
        state.matchDeadlineAtTick = state.running && state.matchDurationMinutes > 0
            ? serverTick(world.getServer()) + state.matchDurationMinutes * 1200L : -1L;
        broadcast(world, state.matchDurationMinutes == 0 ? "Общий лимит времени партии отключён." :
            "Общий лимит времени партии: " + state.matchDurationMinutes + " мин.");
    }

    public static boolean transferTurn(ServerWorld world) {
        BoardState state = board(world);
        if (!state.running || state.paused || state.pending != null) return false;
        state.whiteTurn = !state.whiteTurn;
        updateTeamHighlights(world.getServer(), state);
        updateTurnGlow(world, state);
        if (state.matchMode == MatchMode.ONE_VS_BOT && !state.whiteTurn)
            state.botMoveAtTick = serverTick(world.getServer()) + 20L;
        broadcast(world, "Ход передан команде " + (state.whiteTurn ? "white" : "black") + ".");
        ChessNetwork.broadcastSpectatorState(world, state);
        return true;
    }

    public static void setTeleport(ServerWorld world, boolean white, BlockPos pos) {
        BoardState state = board(world);
        String team = white ? "white" : "black";
        state.teleportTargets.put(team, pos.toImmutable());
        ChessWorldConfig.get(world.getServer()).setLocation(team, world, pos);
        broadcast(world, "Точка команды " + team + " сохранена в настройках мира.");
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
        if (state.matchMode == MatchMode.ONE_VS_BOT && !type.isWhite()) {
            tell(player, "В режиме 1_vs_bot чёрными управляет компьютер."); return;
        }
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

        scheduleMove(world, state, fr, fc, tr, tc, player.getUuid(), player);
    }

    private static void scheduleMove(ServerWorld world, BoardState state, int fr, int fc, int tr, int tc,
                                      UUID moverId, ServerPlayerEntity actor) {
        char[][] cells = readBoard(world);
        char moving = cells[fr][fc], target = cells[tr][tc];
        ChessPieceType type = ChessPieceType.fromSymbol(moving);
        if (type == null) return;
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
            type, fr, fc, tr, tc, movingYaw, rookYaw, capturedSymbol,
            serverTick(world.getServer()) + animationTicks, moverId);
        ChessNetwork.broadcastMoveStart(world, fr, fc, tr, tc, moving, type.model().equals("horse"), animationTicks);
        state.enPassantRow = Character.toUpperCase(moving) == 'P' && Math.abs(tr - fr) == 2 ? (tr + fr) / 2 : -1;
        state.enPassantCol = state.enPassantRow < 0 ? -1 : fc;
        updateMovedFlags(state, type, fr, fc);
        if (actor != null)
            actor.sendMessage(Text.literal("Ход выбран: " + (char)('a' + fc) + (8-fr) + " → " + (char)('a' + tc) + (8-tr) + "."), true);
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
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.interactionManager.getGameMode() == GameMode.SURVIVAL)
                player.changeGameMode(GameMode.ADVENTURE);
        }
        if (tick % 20L == 0L) syncAllTeamPieces(server);
        if (tick % 5L == 0L) processTriggerZones(server);
        processStartCountdowns(server, tick);
        boolean anyMatchActive = BOARDS.values().stream().anyMatch(board -> board.running);
        if (!anyMatchActive && BOARDS.values().stream().noneMatch(board -> board.countdownActive)) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) clearChessTeam(server, player);
        }

        for (Map.Entry<RegistryKey<World>, BoardState> entry : BOARDS.entrySet()) {
            ServerWorld world = server.getWorld(entry.getKey());
            BoardState state = entry.getValue();
            if (world == null) continue;

            if (state.finishPending && tick >= state.finishTeleportAtTick) {
                completeMatchFinish(world, state);
                continue;
            }

            if (tick % 10L == 0L) updateTurnGlow(world, state);

            if (state.returnToLobbyAtTick >= 0L && tick >= state.returnToLobbyAtTick) {
                state.returnToLobbyAtTick = -1L;
                teleportAllToLobby(world);
            }

            if (state.running && state.matchDeadlineAtTick >= 0L && tick >= state.matchDeadlineAtTick) {
                finishMatch(world, "Общее время партии истекло. Игра завершена.");
                for (ServerPlayerEntity player : world.getPlayers())
                    playSoundToPlayer(player, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.9f, 0.75f);
            }

            if (tick % 20L == 0L) {
                if (state.running && !state.paused && state.pending == null && !state.promotionPending
                    && state.ruleMode != RuleMode.NO_REALISM) {
                    boolean losingWhite = state.whiteTurn;
                    if (losingWhite) state.whiteClockTicks = Math.max(0L, state.whiteClockTicks - 20L);
                    else state.blackClockTicks = Math.max(0L, state.blackClockTicks - 20L);
                    long remaining = losingWhite ? state.whiteClockTicks : state.blackClockTicks;
                    if (remaining <= 0L) {
                        finishMatch(world, "Время команды " + (losingWhite ? "white" : "black") + " вышло.");
                        notifyCheckTeam(world, losingWhite, "ПРОИГРЫШ", SoundEvents.ENTITY_VILLAGER_NO);
                        notifyCheckTeam(world, !losingWhite, "ПОБЕДА", SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
                    }
                }
                ChessNetwork.broadcastClockState(world);
            }

            if (state.running && state.matchMode == MatchMode.ONE_VS_BOT && !state.whiteTurn
                && !state.paused && state.pending == null && !state.promotionPending) {
                queueBotMove(world, state, tick);
            }

            if (!state.running || state.pending == null || tick < state.pending.executeAt) continue;
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

            String options = needsPromotion ? promotionOptions(state, move.type.isWhite()) : "";
            if (needsPromotion && !options.isEmpty()
                && state.matchMode == MatchMode.ONE_VS_BOT && !move.type.isWhite()
                && BOT_PLAYER_ID.equals(move.playerId)) {
                promoteBotPawn(world, state, options.charAt(0), move.tr, move.tc);
                state.whiteTurn = true;
                updateTeamHighlights(server, state);
                updateTurnGlow(world, state);
                announceTurn(world, state);
                checkEndCondition(world, state);
            } else if (needsPromotion && !options.isEmpty()) {
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
                if (state.matchMode == MatchMode.ONE_VS_BOT && !state.whiteTurn)
                    state.botMoveAtTick = tick + 20L;
                else
                    state.botMoveAtTick = -1L;
                updateTeamHighlights(server, state);
                updateTurnGlow(world, state);
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

    private static void queueBotMove(ServerWorld world, BoardState state, long tick) {
        if (state.botMoveAtTick < 0L) state.botMoveAtTick = tick + 10L;
        if (tick < state.botMoveAtTick) return;
        char[][] cells = readBoard(world);
        List<int[]> bestMoves = new ArrayList<>();
        int bestScore = Integer.MIN_VALUE;
        for (int fr = 0; fr < 8; fr++) for (int fc = 0; fc < 8; fc++) {
            char piece = cells[fr][fc];
            if (piece == '.' || Character.isUpperCase(piece)) continue;
            ChessPieceType type = ChessPieceType.fromSymbol(piece);
            if (type == null) continue;
            for (int tr = 0; tr < 8; tr++) for (int tc = 0; tc < 8; tc++) {
                char target = cells[tr][tc];
                if (target != '.' && Character.toUpperCase(target) == 'K') continue;
                if (!ChessRules.isLegalMove(cells, fr, fc, tr, tc, true,
                    state.enPassantRow, state.enPassantCol, state.blackKingMoved,
                    state.blackLeftRookMoved, state.blackRightRookMoved)) continue;
                int score = target == '.' ? 0 : pieceValue(target) * 10;
                char[][] next = ChessRules.copy(cells);
                next[tr][tc] = piece;
                next[fr][fc] = '.';
                if (Character.toUpperCase(piece) == 'P' && fc != tc && target == '.') next[fr][tc] = '.';
                if (ChessRules.isInCheck(next, true)) score += 7;
                if (Character.toUpperCase(piece) == 'P' && tr == 7) score += 12;
                if (score > bestScore) {
                    bestScore = score;
                    bestMoves.clear();
                    bestMoves.add(new int[]{fr, fc, tr, tc});
                } else if (score == bestScore) {
                    bestMoves.add(new int[]{fr, fc, tr, tc});
                }
            }
        }
        if (bestMoves.isEmpty()) {
            state.botMoveAtTick = -1L;
            checkEndCondition(world, state);
            return;
        }
        int[] move = bestMoves.get(world.getRandom().nextInt(bestMoves.size()));
        state.botMoveAtTick = -1L;
        broadcast(world, "Ход бота.");
        scheduleMove(world, state, move[0], move[1], move[2], move[3], BOT_PLAYER_ID, null);
    }

    private static int pieceValue(char piece) {
        switch (Character.toUpperCase(piece)) {
            case 'Q': return 9;
            case 'R': return 5;
            case 'B':
            case 'N': return 3;
            case 'P': return 1;
            default: return 0;
        }
    }

    private static void promoteBotPawn(ServerWorld world, BoardState state, char option, int row, int col) {
        char kind = Character.toUpperCase(option);
        List<Character> captured = state.blackCapturedPieces;
        int capturedIndex = -1;
        for (int i = 0; i < captured.size(); i++) {
            ChessPieceType candidate = ChessPieceType.fromSymbol(captured.get(i));
            if (candidate != null && candidate.isWhite() && Character.toUpperCase(captured.get(i)) == kind) {
                capturedIndex = i;
                break;
            }
        }
        if (capturedIndex < 0) return;
        BlockPos pos = piecePos(state, row, col);
        float yaw = world.getBlockEntity(pos) instanceof ChessFigureBlockEntity entity ? entity.getYawDegrees() : 0.0f;
        ChessPieceType promoted = ChessPieceType.fromSymbol(Character.toLowerCase(kind));
        if (promoted == null) return;
        world.setBlockState(pos, promoted.block().getDefaultState(), 3);
        if (world.getBlockEntity(pos) instanceof ChessFigureBlockEntity entity) entity.setYawDegrees(yaw);
        captured.set(capturedIndex, 'p');
        if (state.blackCaptureOrigin != null) placeCapturedBlock(world, state.blackCaptureOrigin, capturedIndex, 'p');
    }

    private static void checkEndCondition(ServerWorld world, BoardState state) {
        if (!state.running) return;
        char[][] cells = readBoard(world);
        boolean whiteToMove = state.whiteTurn;
        boolean inCheck = ChessRules.isInCheck(cells, whiteToMove);
        boolean kingMoved = whiteToMove ? state.whiteKingMoved : state.blackKingMoved;
        boolean leftMoved = whiteToMove ? state.whiteLeftRookMoved : state.blackLeftRookMoved;
        boolean rightMoved = whiteToMove ? state.whiteRightRookMoved : state.blackRightRookMoved;
        boolean moves = ChessRules.hasAnyLegalMove(cells, whiteToMove, true,
            state.enPassantRow, state.enPassantCol, kingMoved, leftMoved, rightMoved);
        if (!moves) {
            if (inCheck) {
                finishMatch(world, "Мат! Победа " + (whiteToMove ? "чёрных" : "белых") + ".");
                if (state.matchMode == MatchMode.ONE_VS_BOT) {
                    notifyCheckTeam(world, whiteToMove, "ПРОИГРЫШ", SoundEvents.ENTITY_VILLAGER_NO);
                    if (!whiteToMove) notifyCheckTeam(world, true, "ПОБЕДА", SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
                } else {
                    notifyCheckTeam(world, whiteToMove, "ШАХ И МАТ", SoundEvents.ENTITY_VILLAGER_NO);
                    notifyCheckTeam(world, !whiteToMove, "ПОБЕДА", SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
                }
            } else {
                finishMatch(world, "Пат. Ничья.");
                for (ServerPlayerEntity player : world.getPlayers())
                    playSoundToPlayer(player, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.9f, 0.75f);
            }
        } else if (inCheck) {
            broadcast(world, "Шах " + (whiteToMove ? "белому" : "чёрному") + " королю!");
            notifyCheckTeam(world, whiteToMove, "ШАХ", SoundEvents.BLOCK_NOTE_BLOCK_BELL.value());
        }
        ChessNetwork.broadcastClockState(world);
    }

    private static void tellPlayerByUuid(ServerWorld world, UUID uuid, String message) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getUuid().equals(uuid)) {
                tell(player, message);
                return;
            }
        }
    }

    /**
     * Play end-state sounds directly to each intended player's client. Entity.playSound()
     * uses the player as the world sound source, so the source player is excluded by the
     * normal world broadcast and may not hear their own check/mate/result notification.
     */
    private static void notifyCheckTeam(ServerWorld world, boolean white, String message, net.minecraft.sound.SoundEvent sound) {
        BoardState state = board(world);
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            String teamName = state.lastParticipantTeams.get(player.getUuid());
            if (teamName == null || white != teamName.equals("white")) continue;
            player.sendMessage(Text.literal(message).formatted(
                message.contains("МАТ") ? Formatting.DARK_RED : Formatting.RED, Formatting.BOLD), true);
            playSoundToPlayer(player, sound, 0.9f, message.contains("ШАХ") ? 1.1f : 1.0f);
        }
    }

    private static void playSoundToPlayer(ServerPlayerEntity player, net.minecraft.sound.SoundEvent sound,
                                           float volume, float pitch) {
        player.networkHandler.sendPacket(new PlaySoundS2CPacket(
            Registries.SOUND_EVENT.getEntry(sound),
            SoundCategory.PLAYERS,
            player.getX(), player.getY(), player.getZ(),
            volume, pitch, player.getWorld().getRandom().nextLong()
        ));
    }

    private static void announceTurn(ServerWorld world, BoardState state) {
        broadcast(world, "Ход " + (state.whiteTurn ? "белых" : "чёрных") + ".");
        ChessNetwork.broadcastSpectatorState(world, state);
    }

    private static void updateTeamHighlights(MinecraftServer server, BoardState state) {
        Scoreboard scoreboard = server.getScoreboard();
        ensureChessTeam(scoreboard, "white");
        ensureChessTeam(scoreboard, "black");
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
        public boolean pausedByDisconnect;
        public UUID disconnectedPlayerId;
        public String disconnectedTeamName = "";
        public boolean matchEnded;
        public boolean countdownActive;
        public boolean countdownFadeStarted;
        public long countdownTeleportAtTick = -1L;
        public int countdownRemaining;
        public long countdownNextTick = -1L;
        public boolean finishPending;
        public long finishTeleportAtTick = -1L;
        public final Set<UUID> pendingWhiteParticipants = new HashSet<>();
        public final Set<UUID> pendingBlackParticipants = new HashSet<>();
        public final Set<UUID> observers = new HashSet<>();
        public final Map<UUID, GameMode> observerOriginalModes = new HashMap<>();
        public final Map<UUID, String> lastParticipantTeams = new HashMap<>();
        public long returnToLobbyAtTick = -1L;
        public long matchDeadlineAtTick = -1L;
        public long botMoveAtTick = -1L;
        public int matchDurationMinutes;
        public final Set<UUID> participants = new HashSet<>();
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
