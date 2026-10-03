package com.chess.client;

import com.chess.ChessFigureBlockEntity;
import com.chess.ModBlocks;
import com.chess.client.ModKeyBindings;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import software.bernie.geckolib.core.animation.RawAnimation;

public class ModKeyHandler {

	public static void register() {

		ClientTickEvents.END_CLIENT_TICK.register(
				ModKeyHandler::onClientTick);
	}

	private static void onClientTick(
			MinecraftClient client) {
		if (client.world == null ||
				client.player == null) {
			return;
		}

		// I — вперёд
		while (ModKeyBindings.LADYA_FORWARD.wasPressed()) {
			playAnimation(
					client,
					ChessFigureBlockEntity.LADYA_FORWARD,
					ChessFigureBlockEntity.PESHKA_FORWARD);
		}

		// K — назад
		while (ModKeyBindings.LADYA_BACK.wasPressed()) {
			playAnimation(
					client,
					ChessFigureBlockEntity.LADYA_BACK,
					ChessFigureBlockEntity.PESHKA_BACK);
		}

		// J — влево
		while (ModKeyBindings.LADYA_LEFT.wasPressed()) {
			playAnimation(
					client,
					ChessFigureBlockEntity.LADYA_LEFT,
					ChessFigureBlockEntity.PESHKA_LEFT);
		}

		// L — вправо
		while (ModKeyBindings.LADYA_RIGHT.wasPressed()) {
			playAnimation(
					client,
					ChessFigureBlockEntity.LADYA_RIGHT,
					ChessFigureBlockEntity.PESHKA_RIGHT);
		}
	}

	private static void playAnimation(
			MinecraftClient client,
			RawAnimation ladyaAnimation,
			RawAnimation peshkaAnimation) {
		if (!(client.crosshairTarget instanceof BlockHitResult hitResult)) {
			return;
		}

		if (!(client.world.getBlockEntity(
				hitResult.getBlockPos()) instanceof ChessFigureBlockEntity figure)) {
			return;
		}

		BlockState state = figure.getCachedState();

		if (state.isOf(
				ModBlocks.CHESS_WHITE_PESHKA)) {
			figure.playAnimation(
					peshkaAnimation);
		} else {
			figure.playAnimation(
					ladyaAnimation);
		}
	}
}