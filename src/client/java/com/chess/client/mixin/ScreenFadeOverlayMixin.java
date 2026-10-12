package com.chess.client.mixin;

import com.chess.client.ChessFadeHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws after any active screen, including chat, so GUI elements cannot cut through the fade. */
@Mixin(Screen.class)
public abstract class ScreenFadeOverlayMixin {
    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void chess$drawFadeAfterScreen(DrawContext context, int mouseX, int mouseY,
                                           float delta, CallbackInfo ci) {
        ChessFadeHud.renderOverlay(context);
        context.draw();
    }
}
