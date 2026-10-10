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
    private static final VoxelShape OUTLINE = createCuboidShape(3, -14, 3, 13, 6, 13);
    private static final VoxelShape RAYCAST_HITBOX = createCuboidShape(1, -14, 1, 15, 16, 15);

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
    public VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
        // Use a generous ray-only volume over the whole rendered model. The visible
        // outline remains tight, and the collision shape remains empty.
        return RAYCAST_HITBOX;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        // Figures must be clickable but must not behave like an invisible wall.
        return net.minecraft.util.shape.VoxelShapes.empty();
    }

    private static int dyeRgb(net.minecraft.util.DyeColor color) {
        switch (color.getId()) {
            case 0: return 0xF9FFFE; // white
            case 1: return 0xF9801D; // orange
            case 2: return 0xC74EBD; // magenta
            case 3: return 0x3AB3DA; // light blue
            case 4: return 0xFED83D; // yellow
            case 5: return 0x80C71F; // lime
            case 6: return 0xF38BAA; // pink
            case 7: return 0x474F52; // gray
            case 8: return 0x9D9D97; // light gray
            case 9: return 0x169C9C; // cyan
            case 10: return 0x8932B8; // purple
            case 11: return 0x3C44AA; // blue
            case 12: return 0x835432; // brown
            case 13: return 0x5E7C16; // green
            case 14: return 0xB02E26; // red
            case 15: return 0x1D1D21; // black
            default: return 0xFFFFFF;
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);
        if (!(held.getItem() instanceof DyeItem dye)) return ActionResult.PASS;
        if (!world.isClient && world.getBlockEntity(pos) instanceof ChessFigureBlockEntity figure) {
            figure.setTintRgb(dyeRgb(dye.getColor()));
            if (!player.getAbilities().creativeMode) held.decrement(1);
            player.sendMessage(Text.literal("Цвет фигуры изменён. Используй другой краситель, чтобы сменить цвет."), true);
        }
        return ActionResult.success(world.isClient);
    }
}
