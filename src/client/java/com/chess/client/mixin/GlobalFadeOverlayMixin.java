package com.chess.client.mixin;

import com.chess.client.ChessFadeHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draw at the outermost end of MinecraftClient.render, after GameRenderer, HUD and screens.
 * This covers F1 and ensures the black overlay is the final layer, with no UI cutting through.
 */
@Mixin(MinecraftClient.class)
public abstract class GlobalFadeOverlayMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void chess$blockScreensDuringFade(Screen screen, CallbackInfo ci) {
        if (screen != null && ChessFadeHud.isActive()) ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void chess$drawFadeAboveEveryLayer(boolean tick, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        DrawContext context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
        ChessFadeHud.renderOverlay(context);
        context.draw();
    }
}
