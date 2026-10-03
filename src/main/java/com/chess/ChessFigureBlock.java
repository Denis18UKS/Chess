package com.chess;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

public class ChessFigureBlock extends BlockWithEntity {

    // ============================================================
    // ФОРМА ЛАДЬИ
    // ============================================================

    private static final VoxelShape LADYA_SHAPE = VoxelShapes.union(
            // Нижняя платформа: 6 x 2 x 6
            VoxelShapes.cuboid(
                    5.0 / 16.0, 0.0 / 16.0, 5.0 / 16.0,
                    11.0 / 16.0, 2.0 / 16.0, 11.0 / 16.0
            ),

            // Центральный корпус: 4 x 6 x 4
            VoxelShapes.cuboid(
                    6.0 / 16.0, 2.0 / 16.0, 6.0 / 16.0,
                    10.0 / 16.0, 8.0 / 16.0, 10.0 / 16.0
            ),

            // Верхняя площадка: 6 x 1 x 6
            VoxelShapes.cuboid(
                    5.0 / 16.0, 8.0 / 16.0, 5.0 / 16.0,
                    11.0 / 16.0, 9.0 / 16.0, 11.0 / 16.0
            ),

            // Левая стенка сверху
            VoxelShapes.cuboid(
                    5.0 / 16.0, 9.0 / 16.0, 5.0 / 16.0,
                    6.0 / 16.0, 11.0 / 16.0, 11.0 / 16.0
            ),

            // Правая стенка сверху
            VoxelShapes.cuboid(
                    10.0 / 16.0, 9.0 / 16.0, 5.0 / 16.0,
                    11.0 / 16.0, 11.0 / 16.0, 11.0 / 16.0
            ),

            // Передняя стенка сверху
            VoxelShapes.cuboid(
                    6.0 / 16.0, 9.0 / 16.0, 5.0 / 16.0,
                    10.0 / 16.0, 11.0 / 16.0, 6.0 / 16.0
            ),

            // Задняя стенка сверху
            VoxelShapes.cuboid(
                    6.0 / 16.0, 9.0 / 16.0, 10.0 / 16.0,
                    10.0 / 16.0, 11.0 / 16.0, 11.0 / 16.0
            )
    );

    // ============================================================
    // ФОРМА ПЕШКИ
    // ============================================================

    private static final VoxelShape PESHKA_SHAPE = VoxelShapes.union(
            // Нижняя платформа: 6 x 2 x 6
            VoxelShapes.cuboid(
                    5.0 / 16.0, 0.0 / 16.0, 5.0 / 16.0,
                    11.0 / 16.0, 2.0 / 16.0, 11.0 / 16.0
            ),

            // Центральная часть: 4 x 4 x 4
            VoxelShapes.cuboid(
                    6.0 / 16.0, 2.0 / 16.0, 6.0 / 16.0,
                    10.0 / 16.0, 6.0 / 16.0, 10.0 / 16.0
            ),

            // Верхняя платформа: 6 x 2 x 6
            VoxelShapes.cuboid(
                    5.0 / 16.0, 6.0 / 16.0, 5.0 / 16.0,
                    11.0 / 16.0, 8.0 / 16.0, 11.0 / 16.0
            ),

            // Верхушка: 4 x 2 x 4
            VoxelShapes.cuboid(
                    6.0 / 16.0, 8.0 / 16.0, 6.0 / 16.0,
                    10.0 / 16.0, 10.0 / 16.0, 10.0 / 16.0
            )
    );

    public ChessFigureBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        return new ChessFigureBlockEntity(pos, state);
    }

    // Фигуру полностью рисует GeckoLib.
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    // Форма подсветки блока при наведении.
    @Override
    public VoxelShape getOutlineShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return getFigureShape(state);
    }

    // Физическая collision-форма.
    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return getFigureShape(state);
    }

    private VoxelShape getFigureShape(BlockState state) {
        if (state.isOf(ModBlocks.CHESS_WHITE_PESHKA)) {
            return PESHKA_SHAPE;
        }

        return LADYA_SHAPE;
    }
}