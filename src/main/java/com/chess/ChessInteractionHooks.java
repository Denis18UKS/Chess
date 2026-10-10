package com.chess;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

/** Server-side interception for bound interactive blocks and the in-world config tool. */
public final class ChessInteractionHooks {
    private ChessInteractionHooks() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer))
                return ActionResult.PASS;

            if (serverPlayer.getStackInHand(hand).isOf(ModItems.CHESS_CONFIG_TOOL))
                return ActionResult.SUCCESS;

            String command = ChessWorldConfig.get(serverWorld.getServer()).getBoundCommand(serverWorld, hit.getBlockPos());
            if (command == null || command.isBlank()) return ActionResult.PASS;

            try {
                var source = serverPlayer.getCommandSource()
                    .withWorld(serverWorld)
                    .withPosition(hit.getPos())
                    .withLevel(4);
                serverWorld.getServer().getCommandManager().executeWithPrefix(source, ChessWorldConfig.cleanCommand(command));
            } catch (RuntimeException exception) {
                serverPlayer.sendMessage(net.minecraft.text.Text.literal(
                    "Не удалось выполнить команду блока: " + exception.getMessage()), false);
            }
            // Consume server-side interaction so e.g. crafting tables never open and buttons
            // never emit their vanilla redstone pulse when they have a command binding.
            return ActionResult.SUCCESS;
        });
    }
}
