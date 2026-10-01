package com.chess.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Клиентский Mixin.
// Он позволяет изменять код MinecraftClient только на стороне клиента.
@Mixin(MinecraftClient.class)
public class ExampleClientMixin {

	// Вставляем наш код в самое начало метода run().
	// run() — основной цикл работы клиента Minecraft.
	@Inject(at = @At("HEAD"), method = "run")
	private void init(CallbackInfo info) {
		// Код внутри этого метода выполнится перед началом основного цикла клиента.
	}
}