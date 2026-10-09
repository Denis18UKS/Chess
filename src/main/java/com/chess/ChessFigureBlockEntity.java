package com.chess;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
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

    public ChessFigureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHESS_FIGURE, pos, state);
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

    /** Called by the client packet receiver for synchronized movement. */
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
