package com.chess.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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
     * In Minecraft 1.20.1 hunger icons are drawn inline in renderStatusBars from
     * gui/icons.png at v=27. Keep armor and air textures intact.
     */
    @Redirect(method = "renderStatusBars",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"))
    private void chess$hideFood(DrawContext context, Identifier texture, int x, int y,
                                int u, int v, int width, int height) {
        if (texture.getPath().equals("textures/gui/icons.png") && v == 27) return;
        context.drawTexture(texture, x, y, u, v, width, height);
    }
}
