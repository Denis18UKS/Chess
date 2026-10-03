package com.chess.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

// Здесь находятся все клавиши нашего мода.
public class ModKeyBindings {

	// Анимация движения вперёд.
	public static KeyBinding LADYA_FORWARD;

	// Анимация движения назад.
	public static KeyBinding LADYA_BACK;

	// Анимация движения влево.
	public static KeyBinding LADYA_LEFT;

	// Анимация движения вправо.
	public static KeyBinding LADYA_RIGHT;

	public static void register() {

		// Клавиша I.
		LADYA_FORWARD = KeyBindingHelper.registerKeyBinding(
				new KeyBinding(
						"key.chess.ladya_forward",
						GLFW.GLFW_KEY_I,
						"category.chess"
				)
		);

		// Клавиша K.
		LADYA_BACK = KeyBindingHelper.registerKeyBinding(
				new KeyBinding(
						"key.chess.ladya_back",
						GLFW.GLFW_KEY_K,
						"category.chess"
				)
		);

		// Клавиша J.
		LADYA_LEFT = KeyBindingHelper.registerKeyBinding(
				new KeyBinding(
						"key.chess.ladya_left",
						GLFW.GLFW_KEY_J,
						"category.chess"
				)
		);

		// Клавиша L.
		LADYA_RIGHT = KeyBindingHelper.registerKeyBinding(
				new KeyBinding(
						"key.chess.ladya_right",
						GLFW.GLFW_KEY_L,
						"category.chess"
				)
		);
	}
}