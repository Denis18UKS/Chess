package com.chess.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides vanilla hunger icons on Minecraft 1.20.1 by cancelling just the icon-texture
 * draw calls whose V coordinate is the hunger row. Unlike a Redirect, this is compatible
 * with the installed MixinExtras wrapper stack and does not depend on a nonexistent
 * renderFood method in this Minecraft version.
 */
@Mixin(DrawContext.class)
public abstract class HideFoodIconsMixin {
    @Inject(
        method = "drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chess$hideFoodIcons(Identifier texture, int x, int y, int u, int v,
                                     int width, int height, CallbackInfo ci) {
        if (texture.getNamespace().equals("minecraft")
            && texture.getPath().equals("textures/gui/icons.png")
            && v == 27) {
            ci.cancel();
        }
    }
}
