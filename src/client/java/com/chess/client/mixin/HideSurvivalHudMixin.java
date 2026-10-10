package com.chess.client.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides only the survival heart and hunger rows; armor, air, hotbar and XP HUD remain untouched. */
@Mixin(InGameHud.class)
public abstract class HideSurvivalHudMixin {
    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
    private void chess$hideHealth(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void chess$hideHunger(CallbackInfo ci) {
        ci.cancel();
    }
}
