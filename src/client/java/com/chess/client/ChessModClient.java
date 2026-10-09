package com.chess.client;

import com.chess.ChessGraffitiToolItem;
import com.chess.ChessNetwork;
import com.chess.ModBlockEntities;
import com.chess.ModBlocks;
import com.chess.ModItems;
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
            var stack = player.getStackInHand(hand);
            if (!player.isSneaking() && ModBlocks.isFigureItem(stack)) {
                if (world.isClient) openBoard(stack.getItem());
                return ActionResult.SUCCESS;
            }
            if (stack.isOf(ModItems.CHESS_ASSET_STUDIO_TOOL)) {
                if (world.isClient) MinecraftClient.getInstance().setScreen(new ChessAssetStudioScreen(MinecraftClient.getInstance().currentScreen));
                return world.isClient ? ActionResult.SUCCESS : ActionResult.PASS;
            }
            if (stack.isOf(ModItems.CHESS_GRAFFITI_TOOL)) {
                if (world.isClient) MinecraftClient.getInstance().setScreen(new ChessGraffitiScreen(
                    MinecraftClient.getInstance().currentScreen, hit.getBlockPos()));
                return world.isClient ? ActionResult.SUCCESS : ActionResult.PASS;
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getStackInHand(hand);
            if (!player.isSneaking() && ModBlocks.isFigureItem(stack)) {
                if (world.isClient) openBoard(stack.getItem());
                return TypedActionResult.success(stack);
            }
            if (stack.isOf(ModItems.CHESS_ASSET_STUDIO_TOOL)) {
                if (world.isClient) MinecraftClient.getInstance().setScreen(new ChessAssetStudioScreen(MinecraftClient.getInstance().currentScreen));
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.pass(stack);
        });
    }

    private static void openBoard(net.minecraft.item.Item item) {
        MinecraftClient.getInstance().setScreen(new ChessBoardScreen(Registries.ITEM.getId(item).getPath()));
        ChessClientNetwork.requestBoard();
    }
}
