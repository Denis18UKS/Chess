package com.chess;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ChessFigureBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private RawAnimation pendingAnimation;
    private float yawDegrees;
    private BlockPos moveTarget;
    private long moveStartedAt;
    private int moveDurationTicks = 1;
    private boolean knightMove;
    private int tintRgb = 0xFFFFFF;

    public static final RawAnimation LADYA_FORWARD = RawAnimation.begin().thenPlay("ladya_1_forward");
    public static final RawAnimation LADYA_BACK = RawAnimation.begin().thenPlay("ladya_1_back");
    public static final RawAnimation LADYA_LEFT = RawAnimation.begin().thenPlay("ladya_1_left");
    public static final RawAnimation LADYA_RIGHT = RawAnimation.begin().thenPlay("ladya_1_right");
    public static final RawAnimation PESHKA_FORWARD = RawAnimation.begin().thenPlay("hode_1");
    public static final RawAnimation PESHKA_BACK = RawAnimation.begin().thenPlay("hode_2");
    public static final RawAnimation PESHKA_LEFT = RawAnimation.begin().thenPlay("hode_1");
    public static final RawAnimation PESHKA_RIGHT = RawAnimation.begin().thenPlay("hode_1");

    public ChessFigureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHESS_FIGURE, pos, state);
    }

    public float getYawDegrees() { return yawDegrees; }

    public int getTintRgb() { return tintRgb; }
    public float getTintRed() { return ((tintRgb >> 16) & 0xFF) / 255.0f; }
    public float getTintGreen() { return ((tintRgb >> 8) & 0xFF) / 255.0f; }
    public float getTintBlue() { return (tintRgb & 0xFF) / 255.0f; }

    public void setTintRgb(int rgb) {
        tintRgb = rgb & 0xFFFFFF;
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    public void beginMove(BlockPos target, int durationTicks) { beginMove(target, durationTicks, false); }

    public void beginMove(BlockPos target, int durationTicks, boolean knight) {
        if (world == null || target == null) return;
        moveTarget = target.toImmutable();
        moveStartedAt = world.getTime();
        moveDurationTicks = Math.max(1, durationTicks);
        knightMove = knight;
        markDirty();
        if (!world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    public Vec3d getRenderOffset(float tickDelta) {
        if (world == null || moveTarget == null) return Vec3d.ZERO;
        double progress = ((world.getTime() + tickDelta) - moveStartedAt) / (double)Math.max(1, moveDurationTicks);
        progress = Math.max(0.0, Math.min(1.0, progress));
        double eased = smooth(progress);
        double dx = moveTarget.getX() - pos.getX(), dz = moveTarget.getZ() - pos.getZ();
        double x, z;
        if (knightMove && Math.abs(dx) > 0.5 && Math.abs(dz) > 0.5) {
            boolean longOnX = Math.abs(dx) > Math.abs(dz);
            double longProgress = smooth(Math.min(1.0, progress / 0.64));
            double shortProgress = smooth(Math.max(0.0, (progress - 0.64) / 0.36));
            x = longOnX ? dx * longProgress : dx * shortProgress;
            z = longOnX ? dz * shortProgress : dz * longProgress;
        } else {
            x = dx * eased;
            z = dz * eased;
        }
        double y = (moveTarget.getY() - pos.getY()) * eased + Math.sin(Math.PI * progress) * (knightMove ? 0.72 : 0.10);
        return new Vec3d(x, y, z);
    }

    private static double smooth(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return t * t * (3.0 - 2.0 * t);
    }

    public void setYawDegrees(float yawDegrees) {
        this.yawDegrees = (Math.round(yawDegrees / 90.0f) * 90.0f) % 360.0f;
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putFloat("ChessYaw", yawDegrees);
        nbt.putInt("ChessTint", tintRgb);
        if (moveTarget != null) {
            nbt.putLong("ChessMoveTarget", moveTarget.asLong());
            nbt.putLong("ChessMoveStarted", moveStartedAt);
            nbt.putInt("ChessMoveDuration", moveDurationTicks);
            nbt.putBoolean("ChessKnightMove", knightMove);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        yawDegrees = nbt.contains("ChessYaw") ? nbt.getFloat("ChessYaw") : 0.0f;
        tintRgb = nbt.contains("ChessTint") ? nbt.getInt("ChessTint") & 0xFFFFFF : 0xFFFFFF;
        moveTarget = nbt.contains("ChessMoveTarget") ? BlockPos.fromLong(nbt.getLong("ChessMoveTarget")) : null;
        moveStartedAt = nbt.getLong("ChessMoveStarted");
        moveDurationTicks = nbt.contains("ChessMoveDuration") ? Math.max(1, nbt.getInt("ChessMoveDuration")) : 1;
        knightMove = nbt.getBoolean("ChessKnightMove");
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "figure_controller", 0, this::animationController));
    }

    private <E extends ChessFigureBlockEntity> PlayState animationController(AnimationState<E> state) {
        if (pendingAnimation != null) {
            state.getController().forceAnimationReset();
            state.getController().setAnimation(pendingAnimation);
            pendingAnimation = null;
            return PlayState.CONTINUE;
        }
        return state.getController().hasAnimationFinished() ? PlayState.STOP : PlayState.CONTINUE;
    }

    public void playAnimation(RawAnimation animation) {
        this.pendingAnimation = animation;
    }

    public void playAnimation(String animationName) {
        if (animationName != null && !animationName.isBlank()) {
            playAnimation(RawAnimation.begin().thenPlay(animationName));
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
