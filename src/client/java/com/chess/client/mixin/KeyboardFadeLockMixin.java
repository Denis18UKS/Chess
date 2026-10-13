package com.chess.client.mixin;

import com.chess.client.ChessFadeHud;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Locks keyboard input for the whole teleport transition, including F1 and chat shortcuts. */
@Mixin(Keyboard.class)
public abstract class KeyboardFadeLockMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void chess$blockKeyboardDuringFade(long window, int key, int scancode, int action,
                                               int modifiers, CallbackInfo ci) {
        if (ChessFadeHud.isActive() && action != GLFW.GLFW_RELEASE) ci.cancel();
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void chess$blockTextInputDuringFade(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (ChessFadeHud.isActive()) ci.cancel();
    }
}
