package com.chess.client.mixin;

import com.chess.client.ChessFadeHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Render at the outermost end of MinecraftClient.render, after GameRenderer, HUD and Screen.
 * This covers F1 and every open screen, including ChatScreen.
 */
@Mixin(MinecraftClient.class)
public abstract class GlobalFadeOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void chess$drawFadeAboveEveryLayer(boolean tick, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        DrawContext context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
        ChessFadeHud.renderOverlay(context);
        client.getBufferBuilders().getEntityVertexConsumers().draw();
    }
}
