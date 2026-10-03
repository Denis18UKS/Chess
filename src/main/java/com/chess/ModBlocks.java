package com.chess; // Пакет нашего мода.

import net.minecraft.block.AbstractBlock; // Настройки блока.
import net.minecraft.block.Block; // Сам блок Minecraft.
import net.minecraft.block.Blocks; // Готовые ванильные блоки.
import net.minecraft.item.BlockItem; // Предмет блока.
import net.minecraft.item.Item; // Базовый класс предметов.
import net.minecraft.registry.Registries; // Каталоги Minecraft.
import net.minecraft.registry.Registry; // Регистрация объектов.
import net.minecraft.util.Identifier; // Уникальный адрес объекта.

public class ModBlocks { // Класс, где находятся наши блоки.

    // Белая клетка шахматной доски.
    public static final Block CHESS_WHITE_SQUARE = register(
            new Block(AbstractBlock.Settings.copy(Blocks.STONE)),
            "chess_white_square",
            true);

    // Чёрная клетка шахматной доски.
    public static final Block CHESS_BLACK_SQUARE = register(
            new Block(AbstractBlock.Settings.copy(Blocks.STONE)),
            "chess_black_square",
            true);

    // ============================================================
    // ШАХМАТНЫЕ ФИГУРЫ
    // ============================================================

    // Белая ладья.
    // Это анимируемый GeckoLib-блок.
    public static final Block CHESS_WHITE_LADYA = register(
            new ChessFigureBlock(
                    AbstractBlock.Settings.copy(Blocks.STONE)),
            "chess_white_ladya",
            true);

    // Белая пешка.
    // Это тоже анимируемый GeckoLib-блок.
    public static final Block CHESS_WHITE_PESHKA = register(
            new ChessFigureBlock(
                    AbstractBlock.Settings.copy(Blocks.STONE)),
            "chess_white_peshka",
            true);

    public static void initialize() { // Этот метод вызывается при запуске мода.
        ChessMod.LOGGER.info("Регистрируем блоки Chess...");
    }

    public static Block register(
            Block block,
            String name,
            boolean shouldRegisterItem) {
        // Создаём уникальный адрес блока.
        Identifier id = ChessMod.id(name);

        // Если нужен предмет блока.
        if (shouldRegisterItem) {

            // Создаём предмет, который позволяет держать блок в инвентаре.
            BlockItem blockItem = new BlockItem(
                    block,
                    new Item.Settings());

            // Регистрируем предмет.
            Registry.register(
                    Registries.ITEM,
                    id,
                    blockItem);
        }

        // Регистрируем сам блок.
        return Registry.register(
                Registries.BLOCK,
                id,
                block);
    }
}