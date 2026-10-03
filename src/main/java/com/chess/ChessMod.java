package com.chess;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.GeckoLib;

// Главный класс мода.
// Fabric запускает этот класс при загрузке мода.
public class ChessMod implements ModInitializer {

	// Уникальный идентификатор нашего мода.
	public static final String MOD_ID = "chess";

	// Логгер нужен для вывода сообщений в консоль Minecraft.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Запускаем GeckoLib.
		GeckoLib.initialize();

		// Регистрируем блоки.
		ModBlocks.initialize();

		// Регистрируем BlockEntity.
		ModBlockEntities.initialize();

		LOGGER.info("Chess mod initialized!");
	}

	// Создаёт уникальный Identifier внутри нашего мода.
	// Например: id("ladya") -> chess:ladya
	public static Identifier id(String name) {
		return new Identifier(MOD_ID, name);
	}
}