package com.chess.client;

import com.chess.ModBlockEntities;
import com.chess.ModBlocks;
import com.chess.client.renderer.ChessFigureRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

public class ChessModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererFactories.register(ModBlockEntities.CHESS_FIGURE, ChessFigureRenderer::new);
        ChessClientNetwork.registerClient();

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!player.isSneaking() && ModBlocks.isFigureItem(player.getStackInHand(hand))) {
                if (world.isClient) openBoard(player.getStackInHand(hand).getItem());
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!player.isSneaking() && ModBlocks.isFigureItem(player.getStackInHand(hand))) {
                if (world.isClient) openBoard(player.getStackInHand(hand).getItem());
                return TypedActionResult.success(player.getStackInHand(hand));
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });

    private static void openBoard(net.minecraft.item.Item item) {
        MinecraftClient.getInstance().setScreen(new ChessBoardScreen(Registries.ITEM.getId(item).getPath()));
        ChessClientNetwork.requestBoard();
    }
}
