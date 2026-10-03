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

public class ChessFigureBlockEntity
		extends BlockEntity
		implements GeoBlockEntity {

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	// ============================================================
	// ЛАДЬЯ
	// ============================================================

	// Эти имена должны совпадать с ключами внутри:
	// animations/figures/ladya/ladya.animation.json

	public static final RawAnimation LADYA_FORWARD = RawAnimation.begin()
			.thenPlay("ladya_1_forward");

	public static final RawAnimation LADYA_BACK = RawAnimation.begin()
			.thenPlay("ladya_1_back");

	public static final RawAnimation LADYA_LEFT = RawAnimation.begin()
			.thenPlay("ladya_1_left");

	public static final RawAnimation LADYA_RIGHT = RawAnimation.begin()
			.thenPlay("ladya_1_right");

	// ============================================================
	// ПЕШКА
	// ============================================================

	// Эти имена будут находиться внутри:
	// animations/figures/peshka/peshka.animation.json

	public static final RawAnimation PESHKA_FORWARD = RawAnimation.begin()
			.thenPlay("peshka_forward");

	public static final RawAnimation PESHKA_BACK = RawAnimation.begin()
			.thenPlay("peshka_back");

	public static final RawAnimation PESHKA_LEFT = RawAnimation.begin()
			.thenPlay("peshka_left");

	public static final RawAnimation PESHKA_RIGHT = RawAnimation.begin()
			.thenPlay("peshka_right");

	// Анимация, которую нужно запустить.
	private RawAnimation pendingAnimation;

	public ChessFigureBlockEntity(
			BlockPos pos,
			BlockState state) {
		super(
				ModBlockEntities.CHESS_FIGURE,
				pos,
				state);
	}

	@Override
	public void registerControllers(
			AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(
				new AnimationController<>(
						this,
						"figure_controller",
						0,
						this::animationController));
	}

	private <E extends ChessFigureBlockEntity> PlayState animationController(
			AnimationState<E> state) {
		if (pendingAnimation != null) {

			// Позволяет повторно запустить даже ту же
			// самую одноразовую анимацию.
			state.getController().forceAnimationReset();

			state.getController().setAnimation(
					pendingAnimation);

			pendingAnimation = null;

			return PlayState.CONTINUE;
		}

		if (!state.getController().hasAnimationFinished()) {
			return PlayState.CONTINUE;
		}

		return PlayState.STOP;
	}

	public void playAnimation(
			RawAnimation animation) {
		this.pendingAnimation = animation;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}