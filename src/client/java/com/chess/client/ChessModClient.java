package com.chess.client;

import com.chess.ModBlockEntities;
import com.chess.client.renderer.ChessFigureRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class ChessModClient
        implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        BlockEntityRendererFactories.register(
                ModBlockEntities.CHESS_FIGURE,
                ChessFigureRenderer::new
        );

        ModKeyBindings.register();

        ModKeyHandler.register();
    }
}