package com.chess;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

public class ChessSquareBlock extends Block {

    private static final VoxelShape SHAPE = Block.createCuboidShape(
            0,
            0,
            0,
            16,
            2,
            16
    );

    public ChessSquareBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return SHAPE;
    }
}