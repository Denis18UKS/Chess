package com.chess.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {

        public static KeyBinding LADYA_FORWARD;
        public static KeyBinding LADYA_BACK;
        public static KeyBinding LADYA_LEFT;
        public static KeyBinding LADYA_RIGHT;

        public static void register() {

                LADYA_FORWARD = KeyBindingHelper.registerKeyBinding(
                                new KeyBinding(
                                                "key.chess.ladya_forward",
                                                GLFW.GLFW_KEY_I,
                                                "category.chess"));

                LADYA_BACK = KeyBindingHelper.registerKeyBinding(
                                new KeyBinding(
                                                "key.chess.ladya_back",
                                                GLFW.GLFW_KEY_K,
                                                "category.chess"));

                LADYA_LEFT = KeyBindingHelper.registerKeyBinding(
                                new KeyBinding(
                                                "key.chess.ladya_left",
                                                GLFW.GLFW_KEY_J,
                                                "category.chess"));

                LADYA_RIGHT = KeyBindingHelper.registerKeyBinding(
                                new KeyBinding(
                                                "key.chess.ladya_right",
                                                GLFW.GLFW_KEY_L,
                                                "category.chess"));
        }
}