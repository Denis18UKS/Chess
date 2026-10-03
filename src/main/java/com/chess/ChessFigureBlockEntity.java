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

// BlockEntity шахматной фигуры.
// Именно здесь GeckoLib хранит состояние анимации.
public class ChessFigureBlockEntity extends BlockEntity implements GeoBlockEntity {

	// Кэш нужен GeckoLib для хранения состояния анимаций.
	private final AnimatableInstanceCache cache =
			GeckoLibUtil.createInstanceCache(this);

	// Анимации ладьи.
	// ВАЖНО:
	// Названия внутри RawAnimation должны точно совпадать
	// с названиями animation внутри .animation.json.
	public static final RawAnimation LADYA_FORWARD =
			RawAnimation.begin().thenPlay("animation.ladya.forward");

	public static final RawAnimation LADYA_BACK =
			RawAnimation.begin().thenPlay("animation.ladya.back");

	public static final RawAnimation LADYA_LEFT =
			RawAnimation.begin().thenPlay("animation.ladya.left");

	public static final RawAnimation LADYA_RIGHT =
			RawAnimation.begin().thenPlay("animation.ladya.right");

	// Пока здесь хранится анимация, которую нужно запустить.
	private RawAnimation pendingAnimation;

	public ChessFigureBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CHESS_FIGURE, pos, state);
	}

	@Override
	public void registerControllers(
			AnimatableManager.ControllerRegistrar controllers
	) {
		// Создаём контроллер, который отвечает за проигрывание
		// анимаций шахматной фигуры.
		controllers.add(
				new AnimationController<>(
						this,
						"figure_controller",
						0,
						this::animationController
				)
		);
	}

	// Основная логика анимационного контроллера.
	private <E extends ChessFigureBlockEntity> PlayState animationController(
			AnimationState<E> state
	) {
		// Если поступила команда на запуск новой анимации.
		if (pendingAnimation != null) {

			// Передаём анимацию GeckoLib.
			state.getController().setAnimation(pendingAnimation);

			// Удаляем запрос, чтобы анимация не запускалась заново
			// каждый кадр.
			pendingAnimation = null;

			return PlayState.CONTINUE;
		}

		// Пока текущая анимация не закончилась,
		// продолжаем её проигрывать.
		if (!state.getController().hasAnimationFinished()) {
			return PlayState.CONTINUE;
		}

		// После завершения ничего не проигрываем.
		return PlayState.STOP;
	}

	// Запрашивает проигрывание анимации.
	public void playAnimation(RawAnimation animation) {
		this.pendingAnimation = animation;
	}

	// Возвращаем кэш GeckoLib.
	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}