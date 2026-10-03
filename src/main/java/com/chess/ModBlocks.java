package com.chess; // Пакет нашего мода.

import net.minecraft.block.AbstractBlock; // Настройки блока.
import net.minecraft.block.Block; // Сам блок Minecraft.
import net.minecraft.block.Blocks; // Готовые ванильные блоки Minecraft.
import net.minecraft.item.BlockItem; // Предмет нашего блока.
import net.minecraft.item.Item; // Базовый класс предметов.
import net.minecraft.registry.Registries; // Каталоги Minecraft.
import net.minecraft.registry.Registry; // Регистрация объектов.
import net.minecraft.util.Identifier; // Уникальный адрес объекта.

public class ModBlocks { // Класс, где находятся наши блоки.

    // Создаём белую клетку шахматной доски.
    // Настройки блока копируются с обычного камня.
    public static final Block CHESS_WHITE_SQUARE = register(
            new Block(AbstractBlock.Settings.copy(Blocks.STONE)),
            "chess_white_square",
            true
    );

    // Создаём чёрную клетку шахматной доски.
    // Настройки блока также копируются с обычного камня.
    public static final Block CHESS_BLACK_SQUARE = register(
            new Block(AbstractBlock.Settings.copy(Blocks.STONE)),
            "chess_black_square",
            true
    );

    public static void initialize() { // Этот метод вызывается при запуске мода.
    }

    public static Block register(Block block, String name, boolean shouldRegisterItem) { // Метод добавляет блок в Minecraft.

        Identifier id = ExampleMod.id(name); // Создаём адрес блока через вспомогательный метод ExampleMod.

        if (shouldRegisterItem) { // Если нужен предмет блока.

            BlockItem blockItem = new BlockItem(block, new Item.Settings()); // Создаём предмет блока.

            Registry.register(Registries.ITEM, id, blockItem); // Добавляем предмет в каталог предметов.
        }

        return Registry.register(Registries.BLOCK, id, block); // Добавляем блок в каталог блоков.
    }
}