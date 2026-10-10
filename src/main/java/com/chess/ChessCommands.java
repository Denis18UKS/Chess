package com.chess;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
            .then(literal("white").executes(ctx -> joinTeam(ctx.getSource(), "white")))
            .then(literal("black").executes(ctx -> joinTeam(ctx.getSource(), "black")))
            .then(literal("autoconfig").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                ensureTeam(ctx.getSource().getServer().getScoreboard(), "white", Formatting.WHITE);
                ensureTeam(ctx.getSource().getServer().getScoreboard(), "black", Formatting.DARK_GRAY);
                ctx.getSource().sendFeedback(() -> Text.literal("Созданы команды white и black. Игроки могут использовать /chess white и /chess black."), false);
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
            Team team = player.getScoreboardTeam();
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
            Team team = player.getScoreboardTeam();
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

    private static int teleport(ServerCommandSource source, boolean white) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        BlockPos pos = ChessGameManager.teleportTarget(source.getWorld(), white);
        if (pos == null) { source.sendError(Text.literal("TP-точка не настроена.")); return 0; }
        player.teleport(source.getWorld(), pos.getX() + .5, pos.getY() + 1.0, pos.getZ() + .5, player.getYaw(), player.getPitch());
        return 1;
    }

    private static int joinTeam(ServerCommandSource source, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ChessGameManager.BoardState state = ChessGameManager.board(source.getWorld());
        Scoreboard scoreboard = source.getServer().getScoreboard();
        Team team = ensureTeam(scoreboard, name, name.equals("white") ? Formatting.WHITE : Formatting.DARK_GRAY);
        int limit = state.matchMode == ChessGameManager.MatchMode.ONE_ONE ? 1 : 2;
        boolean alreadyMember = team.getPlayerList().contains(player.getEntityName());
        if (!alreadyMember && team.getPlayerList().size() >= limit) {
            source.sendError(Text.literal("Команда " + name + " заполнена для режима " + (limit == 1 ? "1v1" : "2v2") + "."));
            return 0;
        }
        scoreboard.addPlayerToTeam(player.getEntityName(), team);
        ChessGameManager.giveTeamPieces(player, name.equals("white"));
        source.sendFeedback(() -> Text.literal("Вы присоединились к команде " + name + "."), false);
        return 1;
    }

    private static Team ensureTeam(Scoreboard scoreboard, String name, Formatting color) {
        Team team = scoreboard.getTeam(name);
        if (team == null) team = scoreboard.addTeam(name);
        team.setDisplayName(Text.literal(name));
        team.setColor(color);
        team.setFriendlyFireAllowed(false);
        return team;
    }
}
