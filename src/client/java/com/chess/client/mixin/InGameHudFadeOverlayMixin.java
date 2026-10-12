package com.chess.client.mixin;

import com.chess.client.ChessFadeHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draw after HUD rendering on every return path, including the HUD-hidden/F1 path. */
@Mixin(InGameHud.class)
public abstract class InGameHudFadeOverlayMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void chess$drawFadeAfterHud(DrawContext context, float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen != null) return;
        ChessFadeHud.renderHudOverlay(context);
    }
}
