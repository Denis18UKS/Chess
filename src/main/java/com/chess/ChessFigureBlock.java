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
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Block-entity host for chess models. Shapes approximate the visible per-piece geometry. */
public class ChessFigureBlock extends BlockWithEntity {
    // Geo models are rendered at y=-14/16 of their logical block. The outline must use
    // the same offset; the previous +15px top made the selection box almost two blocks tall.
    private static final VoxelShape BASE = createCuboidShape(5, -14, 5, 11, -12, 11);

    private static final VoxelShape PAWN_SHAPE = VoxelShapes.union(BASE,
        createCuboidShape(6, -12, 6, 10, -8, 10),
        createCuboidShape(5, -8, 5, 11, -6, 11),
        createCuboidShape(6, -6, 6, 10, -4, 10));

    private static final VoxelShape ROOK_SHAPE = VoxelShapes.union(BASE,
        createCuboidShape(6, -12, 6, 10, -6, 10),
        createCuboidShape(5, -6, 5, 11, -5, 11),
        createCuboidShape(6, -5, 6, 10, -3, 10),
        createCuboidShape(5, -5, 5, 6, -2, 6),
        createCuboidShape(10, -5, 5, 11, -2, 6),
        createCuboidShape(5, -5, 10, 6, -2, 11),
        createCuboidShape(10, -5, 10, 11, -2, 11));

    private static final VoxelShape BISHOP_SHAPE = VoxelShapes.union(BASE,
        createCuboidShape(6, -12, 6, 10, -4, 10),
        createCuboidShape(5, -4, 5, 11, -2, 11),
        createCuboidShape(7, -2, 7, 9, 1, 9));

    private static final VoxelShape KNIGHT_SHAPE = VoxelShapes.union(BASE,
        createCuboidShape(6, -12, 6, 10, -10, 10),
        createCuboidShape(6, -10, 5, 10, -5, 11),
        createCuboidShape(6, -5, 5, 10, 0, 11));

    private static final VoxelShape KING_SHAPE = VoxelShapes.union(BASE,
        createCuboidShape(6, -12, 6, 10, -4, 10),
        createCuboidShape(5, -4, 5, 11, -2, 11),
        createCuboidShape(7, -2, 7, 9, 2, 9),
        createCuboidShape(5, -1, 7, 11, 1, 9));

    private static final VoxelShape QUEEN_SHAPE = VoxelShapes.union(BASE,
        createCuboidShape(6, -12, 6, 10, -8, 10),
        createCuboidShape(5, -8, 5, 11, -6, 11),
        createCuboidShape(6, -6, 6, 10, -4, 10),
        createCuboidShape(5, -4, 5, 11, -2, 11),
        createCuboidShape(7, -2, 7, 9, 2, 9),
        createCuboidShape(5, -2, 5, 6, 1, 6),
        createCuboidShape(10, -2, 5, 11, 1, 6),
        createCuboidShape(5, -2, 10, 6, 1, 11),
        createCuboidShape(10, -2, 10, 11, 1, 11));

    public ChessFigureBlock(Settings settings) { super(settings.nonOpaque()); }

    private VoxelShape figureShape() {
        String id = Registries.BLOCK.getId(this).getPath();
        if (id.endsWith("_peshka")) return PAWN_SHAPE;
        if (id.endsWith("_ladya")) return ROOK_SHAPE;
        if (id.endsWith("_el")) return BISHOP_SHAPE;
        if (id.endsWith("_horse")) return KNIGHT_SHAPE;
        if (id.endsWith("_king")) return KING_SHAPE;
        return QUEEN_SHAPE;
    }

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
        return figureShape();
    }

    @Override
    public VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
        return figureShape();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return figureShape();
    }

    private static int dyeRgb(net.minecraft.util.DyeColor color) {
        switch (color.getId()) {
            case 0: return 0xF9FFFE;
            case 1: return 0xF9801D;
            case 2: return 0xC74EBD;
            case 3: return 0x3AB3DA;
            case 4: return 0xFED83D;
            case 5: return 0x80C71F;
            case 6: return 0xF38BAA;
            case 7: return 0x474F52;
            case 8: return 0x9D9D97;
            case 9: return 0x169C9C;
            case 10: return 0x8932B8;
            case 11: return 0x3C44AA;
            case 12: return 0x835432;
            case 13: return 0x5E7C16;
            case 14: return 0xB02E26;
            case 15: return 0x1D1D21;
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
