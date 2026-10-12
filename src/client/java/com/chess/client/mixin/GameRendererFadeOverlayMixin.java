package com.chess.client.mixin;

import com.chess.client.ChessFadeHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs even when F1 hides the HUD, because GameRenderer still renders gameplay. */
@Mixin(GameRenderer.class)
public abstract class GameRendererFadeOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void chess$drawFadeAfterGame(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.currentScreen != null) return;
        DrawContext context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
        ChessFadeHud.renderOverlay(context);
        context.draw();
    }
}
