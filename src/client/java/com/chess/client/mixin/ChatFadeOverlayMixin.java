package com.chess.client.mixin;

import com.chess.client.ChessFadeHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat is drawn after InGameHud; redraw the fade last so the chat panel never cuts through it. */
@Mixin(ChatScreen.class)
public abstract class ChatFadeOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void chess$renderFadeAboveChat(DrawContext context, int mouseX, int mouseY,
                                           float delta, CallbackInfo ci) {
        ChessFadeHud.renderOverlay(context);
    }
}
