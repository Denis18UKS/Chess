package com.chess;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ChessCommands {
    private ChessCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("chess")
            .then(literal("2d").executes(ctx -> {
                ChessGameManager.setViewMode(ctx.getSource().getWorld(), false);
                ctx.getSource().sendFeedback(() -> Text.literal("Chess: 2D интерфейс"), false);
                return 1;
            }))
            .then(literal("3d").executes(ctx -> {
                ChessGameManager.setViewMode(ctx.getSource().getWorld(), true);
                ctx.getSource().sendFeedback(() -> Text.literal("Chess: 3D режим мира"), false);
                return 1;
            }))
            .then(literal("clock").then(argument("minutes", IntegerArgumentType.integer(1, 180))
                .requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                    int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                    ChessGameManager.configureClock(ctx.getSource().getWorld(), minutes);
                    ctx.getSource().sendFeedback(() -> Text.literal("Chess: таймер " + minutes + " минут на сторону"), false);
                    return 1;
                })))
            .then(literal("captures").then(literal("autoconfig").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                ChessGameManager.autoConfigureCaptures(ctx.getSource().getWorld());
                return 1;
            })))
            .then(literal("reset").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                try {
                    ChessGameManager.resetBoard(ctx.getSource().getWorld());
                    ctx.getSource().sendFeedback(() -> Text.literal("Chess: начальная расстановка восстановлена."), false);
                    return 1;
                } catch (IllegalStateException ex) {
                    ctx.getSource().sendError(Text.literal(ex.getMessage()));
                    return 0;
                }
            }))
            .then(literal("start").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                try {
                    ChessGameManager.start(ctx.getSource().getWorld());
                    ctx.getSource().sendFeedback(() -> Text.literal("Chess: start"), false);
                    return 1;
                } catch (IllegalStateException ex) {
                    ctx.getSource().sendError(Text.literal(ex.getMessage()));
                    return 0;
                }
            }))
            .then(literal("stop").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                ChessGameManager.stop(ctx.getSource().getWorld(), "Шахматная партия остановлена.");
                return 1;
            }))
            .then(literal("pause").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                try { ChessGameManager.togglePause(ctx.getSource().getWorld()); return 1; }
                catch (IllegalStateException ex) { ctx.getSource().sendError(Text.literal(ex.getMessage())); return 0; }
            }))
            .then(literal("termination").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                ChessGameManager.stop(ctx.getSource().getWorld(), "Шахматная партия завершена командой termination.");
                return 1;
            }))
            .then(literal("realism").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                ChessGameManager.setRuleMode(ctx.getSource().getWorld(), ChessGameManager.RuleMode.REALISM); return 1;
            }))
            .then(literal("no_realism").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                ChessGameManager.setRuleMode(ctx.getSource().getWorld(), ChessGameManager.RuleMode.NO_REALISM); return 1;
            }))
            .then(literal("full_realism").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                ChessGameManager.setRuleMode(ctx.getSource().getWorld(), ChessGameManager.RuleMode.FULL_REALISM); return 1;
            }))
            .then(literal("one_one").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                ChessGameManager.setMatchMode(ctx.getSource().getWorld(), ChessGameManager.MatchMode.ONE_ONE); return 1;
            }))
            .then(literal("two_two").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                ChessGameManager.setMatchMode(ctx.getSource().getWorld(), ChessGameManager.MatchMode.TWO_TWO); return 1;
            }))
            .then(literal("1_vs_bot").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                var world = ctx.getSource().getWorld();
                try {
                    ChessGameManager.setMatchMode(world, ChessGameManager.MatchMode.ONE_VS_BOT);
                    ChessGameManager.start(world);
                    ctx.getSource().sendFeedback(() -> Text.literal("Chess: начата партия 1_vs_bot."), false);
                    return 1;
                } catch (IllegalStateException ex) {
                    ctx.getSource().sendError(Text.literal(ex.getMessage()));
                    return 0;
                }
            }))
            .then(literal("match_time").then(argument("minutes", IntegerArgumentType.integer(0, 1440))
                .requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                    int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                    ChessGameManager.setMatchDuration(ctx.getSource().getWorld(), minutes);
                    return 1;
                })))
            .then(literal("lobby_delay").then(argument("seconds", IntegerArgumentType.integer(0, 3600))
                .requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                    int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                    ChessGameManager.setLobbyDelay(ctx.getSource().getServer(), seconds);
                    return 1;
                })) )
            .then(literal("turnhud").requires(s -> s.hasPermissionLevel(2))
                .then(literal("off").executes(ctx -> setTurnHud(ctx.getSource(), 0)))
                .then(literal("temporary").executes(ctx -> setTurnHud(ctx.getSource(), 1)))
                .then(literal("permanent").executes(ctx -> setTurnHud(ctx.getSource(), 2))))
            .then(literal("zones").requires(s -> s.hasPermissionLevel(2))
                .then(literal("show").executes(ctx -> setZonesVisible(ctx.getSource(), true)))
                .then(literal("hide").executes(ctx -> setZonesVisible(ctx.getSource(), false)))
                .then(literal("toggle").executes(ctx -> {
                    ChessWorldConfig config = ChessWorldConfig.get(ctx.getSource().getServer());
                    return setZonesVisible(ctx.getSource(), !config.areTriggerZonesVisible());
                })))
            .then(literal("zone").requires(s -> s.hasPermissionLevel(2))
                .then(literal("pos1").then(argument("pos", BlockPosArgumentType.blockPos()).executes(ctx -> {
                    BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
                    ChessWorldConfig.get(ctx.getSource().getServer()).saveZoneCorner(ctx.getSource().getWorld(), pos);
                    ctx.getSource().sendFeedback(() -> Text.literal("Точка 1 сохранена: " + pos.toShortString()
                        + ". Укажи точку 2 через /chess zone pos2 <pos> <имя>."), false);
                    return 1;
                })))
                .then(literal("pos2").then(argument("pos", BlockPosArgumentType.blockPos())
                    .then(argument("name", StringArgumentType.word()).executes(ctx ->
                        defineZone(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"),
                            StringArgumentType.getString(ctx, "name"), ""))
                    .then(argument("onEnter", StringArgumentType.greedyString()).executes(ctx ->
                        defineZone(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"),
                            StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "onEnter")))))))
            .then(literal("white").executes(ctx -> joinTeam(ctx.getSource(), "white")))
            .then(literal("black").executes(ctx -> joinTeam(ctx.getSource(), "black")))
            .then(literal("unselect").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                return ChessGameManager.clearTeamSelection(player) ? 1 : 0;
            }))
            .then(literal("spectate").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                return ChessGameManager.toggleSpectating(player) ? 1 : 0;
            }))
            .then(literal("autoconfig").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                ensureTeam(ctx.getSource().getServer().getScoreboard(), "white", Formatting.WHITE);
                ensureTeam(ctx.getSource().getServer().getScoreboard(), "black", Formatting.DARK_GRAY);
                ctx.getSource().sendFeedback(() -> Text.literal("Созданы команды «Белые» и «Чёрные». Предварительный выбор: /chess white или /chess black."), false);
                return 1;
            }))
            .then(literal("turn").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                boolean changed = ChessGameManager.transferTurn(ctx.getSource().getWorld());
                if (!changed) { ctx.getSource().sendError(Text.literal("Ход нельзя передать во время паузы, анимации или без активной партии.")); return 0; }
                return 1;
            }))
            .then(literal("board").then(literal("set").requires(s -> s.hasPermissionLevel(2))
                .then(argument("pos", BlockPosArgumentType.blockPos()).executes(ctx -> {
                    BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
                    ChessGameManager.configureBoard(ctx.getSource().getWorld(), pos);
                    return 1;
                }))))
            .then(literal("tp").then(literal("set").requires(s -> s.hasPermissionLevel(2))
                .then(literal("white").then(argument("pos", BlockPosArgumentType.blockPos()).executes(ctx -> {
                    ChessGameManager.setTeleport(ctx.getSource().getWorld(), true, BlockPosArgumentType.getBlockPos(ctx, "pos")); return 1;
                })))
                .then(literal("black").then(argument("pos", BlockPosArgumentType.blockPos()).executes(ctx -> {
                    ChessGameManager.setTeleport(ctx.getSource().getWorld(), false, BlockPosArgumentType.getBlockPos(ctx, "pos")); return 1;
                })))))
            .then(literal("tp").then(literal("go").then(literal("white").executes(ctx -> teleport(ctx.getSource(), true)))
                .then(literal("black").executes(ctx -> teleport(ctx.getSource(), false))))
            )
        );

        // Merge this status child into Minecraft's existing /team command tree.
        dispatcher.register(literal("team").then(literal("status").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            net.minecraft.scoreboard.AbstractTeam team = player.getScoreboardTeam();
            if (team == null) {
                ctx.getSource().sendFeedback(() -> Text.literal("Ты не состоишь ни в одной scoreboard-команде."), false);
                return 1;
            }
            String members = team.getPlayerList().stream().sorted()
                .collect(java.util.stream.Collectors.joining(", "));
            String teamName = team.getName();
            ctx.getSource().sendFeedback(() -> Text.literal("Твоя команда: " + teamName + ". Участники: " + members), false);
            return 1;
        })));

        dispatcher.register(literal("teamstatus").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            net.minecraft.scoreboard.AbstractTeam team = player.getScoreboardTeam();
            if (team == null) {
                ctx.getSource().sendFeedback(() -> Text.literal("Ты не состоишь ни в одной scoreboard-команде."), false);
            } else {
                String members = team.getPlayerList().stream().sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
                ctx.getSource().sendFeedback(() -> Text.literal("Твоя команда: " + team.getName() + ". Участники: " + members), false);
            }
            return 1;
        }));

        dispatcher.register(literal("chessdev").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            if (player.getCommandTags().contains("chess_dev")) {
                player.getCommandTags().remove("chess_dev");
                player.sendMessage(Text.literal("Режим разработчика выключен."), false);
            } else {
                player.addCommandTag("chess_dev");
                player.sendMessage(Text.literal("Режим разработчика включён. Кейбинд смены команды активен."), false);
            }
            ChessNetwork.sendBoard(player);
            return 1;
        }));

        dispatcher.register(literal("chessboard").then(literal("check").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayer();
            return ChessGameManager.checkBoard(ctx.getSource().getWorld(), player) ? 1 : 0;
        })));
    }

    private static int setTurnHud(ServerCommandSource source, int mode) {
        ChessWorldConfig.get(source.getServer()).setTurnHudMode(mode);
        String label = mode == 0 ? "выключен" : mode == 1 ? "временный" : "постоянный";
        source.sendFeedback(() -> Text.literal("Индикатор хода: " + label + "."), false);
        return 1;
    }

    private static int setZonesVisible(ServerCommandSource source, boolean visible) {
        ChessWorldConfig.get(source.getServer()).setTriggerZonesVisible(visible);
        source.sendFeedback(() -> Text.literal("Границы триггерных областей " + (visible ? "показаны" : "скрыты") + "."), false);
        return 1;
    }

    private static int defineZone(ServerCommandSource source, BlockPos pos, String name, String requestedEnter) {
        try {
            ChessWorldConfig config = ChessWorldConfig.get(source.getServer());
            String enter = requestedEnter == null ? "" : requestedEnter.trim();
            if (enter.isBlank()) {
                if ("team_white".equalsIgnoreCase(name) || "white".equalsIgnoreCase(name)) enter = "chess white";
                else if ("team_black".equalsIgnoreCase(name) || "black".equalsIgnoreCase(name)) enter = "chess black";
            }
            config.defineZone(name, source.getWorld(), pos, enter, "");
            final String finalEnter = enter;
            source.sendFeedback(() -> Text.literal("Триггерная область «" + name + "» создана между точками 1 и 2. "
                + (finalEnter.isBlank() ? "Команду при входе можно задать в конфигураторе зон." : "Команда при входе: " + finalEnter)), false);
            return 1;
        } catch (IllegalStateException ex) {
            source.sendError(Text.literal(ex.getMessage()));
            return 0;
        }
    }

    private static int teleport(ServerCommandSource source, boolean white) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        source.getPlayerOrThrow();
        return ChessGameManager.teleportTeamToConfiguredTarget(source.getWorld(), white) ? 1 : 0;
    }

    private static int joinTeam(ServerCommandSource source, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        return ChessGameManager.selectTeam(player, name) ? 1 : 0;
    }

    private static Team ensureTeam(Scoreboard scoreboard, String name, Formatting color) {
        Team team = scoreboard.getTeam(name);
        if (team == null) team = scoreboard.addTeam(name);
        Formatting sideColor = name.equals("white") ? Formatting.WHITE : Formatting.DARK_GRAY;
        team.setDisplayName(Text.literal(name.equals("white") ? "Белые" : "Чёрные").formatted(sideColor));
        team.setColor(sideColor);
        team.setFriendlyFireAllowed(false);
        return team;
    }
}
