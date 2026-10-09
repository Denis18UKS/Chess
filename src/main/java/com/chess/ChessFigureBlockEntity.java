package com.chess;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
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
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        yawDegrees = nbt.contains("ChessYaw") ? nbt.getFloat("ChessYaw") : 0.0f;
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
