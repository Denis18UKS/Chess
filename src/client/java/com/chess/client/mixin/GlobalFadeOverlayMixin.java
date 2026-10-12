package com.chess.client.mixin;

import com.chess.client.ChessFadeHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The last render hook in the frame, after GameRenderer has drawn world, HUD and screen.
 * Unlike InGameHud.render this still runs with F1; unlike Screen.render it also runs with
 * no screen open. Flush the GUI buffer here so chat panels cannot be drawn over the fade.
 */
@Mixin(GameRenderer.class)
public abstract class GlobalFadeOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void chess$drawFadeAboveEveryLayer(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        DrawContext context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
        ChessFadeHud.renderOverlay(context);
        client.getBufferBuilders().getEntityVertexConsumers().draw();
    }
}
