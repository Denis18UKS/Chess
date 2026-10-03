package com.chess;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {
    public static final BlockEntityType<ChessFigureBlockEntity> CHESS_FIGURE = 
        Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            ChessMod.id("chess_figure"),
            BlockEntityType.Builder.create(
                ChessFigureBlockEntity::new,
                ModBlocks.CHESS_WHITE_LADYA,
                ModBlocks.CHESS_WHITE_PESHKA
            ).build(null)
        );

    public static void initialize() {
        ChessMod.LOGGER.info("Registration BlockEntity Chess...");
    }
}
