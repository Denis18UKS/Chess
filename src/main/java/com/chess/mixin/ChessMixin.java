package com.chess.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin позволяет изменять существующий код Minecraft
// без непосредственного изменения исходников Minecraft.
@Mixin(MinecraftServer.class)
public class ChessMixin {

	// Вставляем наш код в самое начало метода loadWorld().
	// loadWorld() вызывается при загрузке игрового мира на серверной стороне.
	@Inject(at = @At("HEAD"), method = "loadWorld")
	private void init(CallbackInfo info) {
		// Код внутри этого метода выполнится в момент начала загрузки мира.
	}
}