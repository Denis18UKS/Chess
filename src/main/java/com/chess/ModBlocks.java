package com.chess;

import java.util.Arrays;
import java.util.List;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlocks {
    public static final Block CHESS_WHITE_SQUARE = register(new ChessSquareBlock(AbstractBlock.Settings.copy(Blocks.STONE)), "chess_white_square", true);
    public static final Block CHESS_BLACK_SQUARE = register(new ChessSquareBlock(AbstractBlock.Settings.copy(Blocks.STONE)), "chess_black_square", true);
    public static final Block CHESS_SETTINGS_PANEL = register(new ChessSettingsPanelBlock(), "chess_settings_panel", true);

    public static final Block CHESS_WHITE_KING = piece("chess_white_king");
    public static final Block CHESS_WHITE_FERZ = piece("chess_white_ferz");
    public static final Block CHESS_WHITE_LADYA = piece("chess_white_ladya");
    public static final Block CHESS_WHITE_EL = piece("chess_white_el");
    public static final Block CHESS_WHITE_HORSE = piece("chess_white_horse");
    public static final Block CHESS_WHITE_PESHKA = piece("chess_white_peshka");

    public static final Block CHESS_BLACK_KING = piece("chess_black_king");
    public static final Block CHESS_BLACK_FERZ = piece("chess_black_ferz");
    public static final Block CHESS_BLACK_LADYA = piece("chess_black_ladya");
    public static final Block CHESS_BLACK_EL = piece("chess_black_el");
    public static final Block CHESS_BLACK_HORSE = piece("chess_black_horse");
    public static final Block CHESS_BLACK_PESHKA = piece("chess_black_peshka");

    private ModBlocks() {}

    private static Block piece(String id) {
        return register(new ChessFigureBlock(AbstractBlock.Settings.copy(Blocks.STONE).strength(0.5f)), id, true);
    }

    public static void initialize() {
        ChessMod.LOGGER.info("Регистрируем шахматные клетки и все 12 фигур.");
    }

    public static List<Block> figureBlocks() {
        return Arrays.asList(
            CHESS_WHITE_KING, CHESS_WHITE_FERZ, CHESS_WHITE_LADYA,
            CHESS_WHITE_EL, CHESS_WHITE_HORSE, CHESS_WHITE_PESHKA,
            CHESS_BLACK_KING, CHESS_BLACK_FERZ, CHESS_BLACK_LADYA,
            CHESS_BLACK_EL, CHESS_BLACK_HORSE, CHESS_BLACK_PESHKA
        );
    }

    public static boolean isFigureItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (Block block : figureBlocks()) if (stack.isOf(block.asItem())) return true;
        return false;
    }

    public static Block register(Block block, String name, boolean registerItem) {
        Identifier id = ChessMod.id(name);
        if (registerItem) Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, id, block);
    }
}
