package com.chess.client.mixin;

import com.chess.client.ChessFadeHud;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents clicks from interacting with the world underneath the teleport fade. */
@Mixin(Mouse.class)
public abstract class MouseFadeLockMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void chess$blockMouseButtonsDuringFade(long window, int button, int action,
                                                   int modifiers, CallbackInfo ci) {
        if (ChessFadeHud.isActive() && action != GLFW.GLFW_RELEASE) ci.cancel();
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void chess$blockMouseScrollDuringFade(long window, double horizontal, double vertical,
                                                  CallbackInfo ci) {
        if (ChessFadeHud.isActive()) ci.cancel();
    }
}
