package com.chess;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
    public static final BlockEntityType<ChessFigureBlockEntity> CHESS_FIGURE =
        Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            ChessMod.id("chess_figure"),
            BlockEntityType.Builder.create(
                ChessFigureBlockEntity::new,
                ModBlocks.CHESS_WHITE_KING, ModBlocks.CHESS_WHITE_FERZ,
                ModBlocks.CHESS_WHITE_LADYA, ModBlocks.CHESS_WHITE_EL,
                ModBlocks.CHESS_WHITE_HORSE, ModBlocks.CHESS_WHITE_PESHKA,
                ModBlocks.CHESS_BLACK_KING, ModBlocks.CHESS_BLACK_FERZ,
                ModBlocks.CHESS_BLACK_LADYA, ModBlocks.CHESS_BLACK_EL,
                ModBlocks.CHESS_BLACK_HORSE, ModBlocks.CHESS_BLACK_PESHKA
            ).build(null)
        );

    private ModBlockEntities() {}

    public static void initialize() {
        ChessMod.LOGGER.info("Зарегистрирован общий BlockEntity для всех шахматных фигур.");
    }
}
