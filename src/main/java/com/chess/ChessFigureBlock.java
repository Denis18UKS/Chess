package com.chess;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

// Блок шахматной фигуры.
// Он связан с BlockEntity, потому что GeckoLib использует BlockEntity
// для отображения и проигрывания анимаций.
public class ChessFigureBlock extends BlockWithEntity {

    public ChessFigureBlock(Settings settings) {
        super(settings);
    }

    // Создаём BlockEntity при установке блока в мире.
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ChessFigureBlockEntity(pos, state);
    }

    // Отключаем обычный Minecraft-рендеринг блока.
    // Вместо него фигуру будет рисовать GeckoLib.
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }
}