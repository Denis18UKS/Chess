package com.chess.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides hearts and food icons without removing armor, air bubbles, hotbar or XP HUD. */
@Mixin(InGameHud.class)
public abstract class HideSurvivalHudMixin {
    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
    private void chess$hideHearts(DrawContext context, PlayerEntity player, int x, int y, int lines,
                                  int regeneratingHeartIndex, float maxHealth, int lastHealth,
                                  int health, int absorption, boolean blinking, CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * Cancel the food-only render method rather than redirecting DrawContext.drawTexture.
     * The redirect triggered a MixinExtras wrapper crash with some client mod combinations
     * (including the MixinExtras 0.5.4 stack present in the user's crash report).
     */
    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void chess$hideFood(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo ci) {
        ci.cancel();
    }
}
