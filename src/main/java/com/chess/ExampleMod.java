package com.chess;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Главный класс мода.
// Fabric запускает этот класс при загрузке мода.
public class ExampleMod implements ModInitializer {

	// Уникальный идентификатор нашего мода.
	public static final String MOD_ID = "chess";

	// Логгер нужен для вывода сообщений в консоль Minecraft.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Этот метод вызывается при запуске основной части мода.
		LOGGER.info("Chess mod initialized!");
	}

	// Создаёт Identifier нашего мода.
	// Например: id("white_king") -> chess:white_king
	public static Identifier id(String name) {
		return new Identifier(MOD_ID, name);
	}
}