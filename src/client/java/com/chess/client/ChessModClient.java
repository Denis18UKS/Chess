package com.chess.client;

import com.chess.ModBlockEntities;
import com.chess.client.renderer.ChessFigureRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

// Клиентский класс мода.
// Здесь находится код, который работает только на клиенте Minecraft.
public class ChessModClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {

		// Регистрируем renderer шахматных фигур.
		BlockEntityRendererFactories.register(
				ModBlockEntities.CHESS_FIGURE,
				ChessFigureRenderer::new);

		// Регистрируем клавиши.
		ModKeyBindings.register();

		// Регистрируем обработчик клавиш.
		ModKeyHandler.register();
	}
}