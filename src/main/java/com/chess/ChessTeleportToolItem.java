package com.chess;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public class ChessTeleportToolItem extends Item {
    public ChessTeleportToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!(context.getWorld() instanceof ServerWorld world) || !(context.getPlayer() instanceof ServerPlayerEntity player))
            return ActionResult.SUCCESS;
        Team team = player.getScoreboardTeam();
        boolean white = team == null || !team.getName().equals("black");
        if (player.isSneaking()) {
            BlockPos destination = ChessGameManager.teleportTarget(world, white);
            if (destination == null) player.sendMessage(Text.literal("Точка команды не настроена. ПКМ по блоку сохраняет точку."), false);
            else {
                player.teleport(world, destination.getX() + 0.5, destination.getY() + 1.0, destination.getZ() + 0.5, player.getYaw(), player.getPitch());
                player.sendMessage(Text.literal("Телепортация выполнена."), false);
            }
        } else {
            ChessGameManager.setTeleport(world, white, context.getBlockPos());
            player.sendMessage(Text.literal("TP-точка сохранена для команды " + (white ? "white" : "black") + "."), false);
        }
        return ActionResult.SUCCESS;
    }
}
