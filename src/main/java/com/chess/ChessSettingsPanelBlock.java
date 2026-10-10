package com.chess;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;

/** World-placeable control surface for opening the chess settings panel. */
public final class ChessSettingsPanelBlock extends Block {
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 12, 16);

    public ChessSettingsPanelBlock() {
        super(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(2.0f).nonOpaque());
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world.isClient || placer == null) return;
        // Build a 3x3 wall of panel blocks from the placed anchor. Existing blocks are
        // never replaced; the resulting structure acts as one in-world multi-block panel.
        boolean widthAlongX = placer.getHorizontalFacing().getAxis() == Direction.Axis.Z;
        for (int vertical = 0; vertical < 3; vertical++) {
            for (int lateral = -1; lateral <= 1; lateral++) {
                if (vertical == 0 && lateral == 0) continue;
                BlockPos part = widthAlongX
                    ? pos.add(lateral, vertical, 0)
                    : pos.add(0, vertical, lateral);
                if (world.getBlockState(part).isAir())
                    world.setBlockState(part, state, 3);
            }
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        // The client callback opens the GUI; always consume so it behaves as a control panel.
        return ActionResult.success(world.isClient);
    }
}
