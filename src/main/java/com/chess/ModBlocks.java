package com.chess;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final Block CHESS_WHITE_SQUARE = register(
            new ChessSquareBlock(
                    AbstractBlock.Settings.copy(Blocks.STONE)
            ),
            "chess_white_square",
            true
    );

    public static final Block CHESS_BLACK_SQUARE = register(
            new ChessSquareBlock(
                    AbstractBlock.Settings.copy(Blocks.STONE)
            ),
            "chess_black_square",
            true
    );

    public static final Block CHESS_WHITE_LADYA = register(
            new ChessFigureBlock(
                    AbstractBlock.Settings.copy(Blocks.STONE)
            ),
            "chess_white_ladya",
            true
    );

    public static final Block CHESS_WHITE_PESHKA = register(
            new ChessFigureBlock(
                    AbstractBlock.Settings.copy(Blocks.STONE)
            ),
            "chess_white_peshka",
            true
    );

    public static void initialize() {
        ChessMod.LOGGER.info(
                "Регистрируем блоки Chess..."
        );
    }

    public static Block register(
            Block block,
            String name,
            boolean shouldRegisterItem
    ) {
        Identifier id = ChessMod.id(name);

        if (shouldRegisterItem) {
            BlockItem blockItem = new BlockItem(
                    block,
                    new Item.Settings()
            );

            Registry.register(
                    Registries.ITEM,
                    id,
                    blockItem
            );
        }

        return Registry.register(
                Registries.BLOCK,
                id,
                block
        );
    }
}