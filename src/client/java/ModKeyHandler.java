package com.chess.client;

import com.chess.ChessFigureBlockEntity;
import com.chess.ChessFigureBlockEntity.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;

// Здесь обрабатываем нажатия клавиш.
public class ModKeyHandler {

	public static void register() {

		// Выполняем проверку каждый клиентский тик.
		ClientTickEvents.END_CLIENT_TICK.register(
				ModKeyHandler::onClientTick
		);
	}

	private static void onClientTick(MinecraftClient client) {

		// Minecraft ещё не готов.
		if (client.world == null || client.player == null) {
			return;
		}

		// Проверяем клавишу I.
		while (ModKeyBindings.LADYA_FORWARD.wasPressed()) {
			playAnimation(
					client,
					ChessFigureBlockEntity.LADYA_FORWARD
			);
		}

		// Проверяем клавишу K.
		while (ModKeyBindings.LADYA_BACK.wasPressed()) {
			playAnimation(
					client,
					ChessFigureBlockEntity.LADYA_BACK
			);
		}

		// Проверяем клавишу J.
		while (ModKeyBindings.LADYA_LEFT.wasPressed()) {
			playAnimation(
					client,
					ChessFigureBlockEntity.LADYA_LEFT
			);
		}

		// Проверяем клавишу L.
		while (ModKeyBindings.LADYA_RIGHT.wasPressed()) {
			playAnimation(
					client,
					ChessFigureBlockEntity.LADYA_RIGHT
			);
		}
	}

	private static void playAnimation(
			MinecraftClient client,
			software.bernie.geckolib.core.animation.RawAnimation animation
	) {
		// Проверяем, что игрок смотрит на блок.
		if (!(client.crosshairTarget instanceof BlockHitResult hitResult)) {
			return;
		}

		// Получаем BlockEntity под прицелом.
		if (client.world.getBlockEntity(hitResult.getBlockPos())
				instanceof ChessFigureBlockEntity figure) {

			// Запускаем нужную анимацию.
			figure.playAnimation(animation);
		}
	}
}