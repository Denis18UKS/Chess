package com.chess;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.DyeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class ChessFigureBlock extends BlockWithEntity {
    // Figure blocks are located one block above a 2px-high board tile.
    // Renderer moves only the visual model down to the tile surface; the
    // invisible block entity itself keeps the board's logical coordinates.
    private static final VoxelShape OUTLINE = createCuboidShape(4, -14, 4, 12, 0, 12);

    public ChessFigureBlock(Settings settings) { super(settings.nonOpaque()); }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ChessFigureBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient && placer != null && world.getBlockEntity(pos) instanceof ChessFigureBlockEntity figure) {
            float yaw = MathHelper.floor((placer.getYaw() * 4.0f / 360.0f) + 0.5f) * 90.0f + 180.0f;
            figure.setYawDegrees(yaw);
        }
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        // Share the model-sized interaction outline with movement collision so
        // pieces are solid/targetable without the old oversized invisible cube.
        return OUTLINE;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);
        if (!(held.getItem() instanceof DyeItem dye)) return ActionResult.PASS;
        if (!world.isClient && world.getBlockEntity(pos) instanceof ChessFigureBlockEntity figure) {
            figure.setTintRgb(dye.getColor().getEntityColor());
            if (!player.getAbilities().creativeMode) held.decrement(1);
            player.sendMessage(Text.literal("Цвет фигуры изменён. Используй другой краситель, чтобы сменить цвет."), true);
        }
        return ActionResult.success(world.isClient);
    }
}
