package com.chess.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the heart row without removing armor, air bubbles, hotbar or XP HUD. */
@Mixin(InGameHud.class)
public abstract class HideSurvivalHudMixin {
    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
    private void chess$hideHearts(DrawContext context, PlayerEntity player, int x, int y, int lines,
                                  int regeneratingHeartIndex, float maxHealth, int lastHealth,
                                  int health, int absorption, boolean blinking, CallbackInfo ci) {
        ci.cancel();
    }
}
