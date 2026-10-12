package com.chess.client.mixin;

import com.chess.client.ChessFadeHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paints the fade at the end of HUD rendering, regardless of the F1 HUD-hidden option. */
@Mixin(InGameHud.class)
public abstract class FadeOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void chess$renderFadeAboveHud(DrawContext context, float tickDelta, CallbackInfo ci) {
        ChessFadeHud.renderHudOverlay(context);
    }
}
