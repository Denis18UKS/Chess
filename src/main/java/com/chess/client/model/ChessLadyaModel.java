package com.chess.client.model;

import com.chess.ChessFigureBlockEntity;
import com.chess.ChessMod;
import com.chess.ModBlocks;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class ChessLadyaModel
        extends GeoModel<ChessFigureBlockEntity> {

    private static final Identifier LADYA_MODEL = ChessMod.id(
            "geo/models/figures/ladya.geo.json");

    private static final Identifier LADYA_TEXTURE = ChessMod.id(
            "textures/figures/white_figures/white_ladya.png");

    private static final Identifier LADYA_ANIMATION = ChessMod.id(
            "animations/figures/ladya/ladya.animation.json");

    private static final Identifier PESHKA_MODEL = ChessMod.id(
            "geo/models/figures/peshka.geo.json");

    private static final Identifier PESHKA_TEXTURE = ChessMod.id(
            "textures/figures/white_figures/white_peshka.png");

    private static final Identifier PESHKA_ANIMATION = ChessMod.id(
            "animations/figures/peshka/peshka.animation.json");

    private boolean isPeshka(
            ChessFigureBlockEntity animatable) {
        return animatable.getCachedState().isOf(
                ModBlocks.CHESS_WHITE_PESHKA);
    }

    @Override
    public Identifier getModelResource(
            ChessFigureBlockEntity animatable) {
        return isPeshka(animatable)
                ? PESHKA_MODEL
                : LADYA_MODEL;
    }

    @Override
    public Identifier getTextureResource(
            ChessFigureBlockEntity animatable) {
        return isPeshka(animatable)
                ? PESHKA_TEXTURE
                : LADYA_TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(
            ChessFigureBlockEntity animatable) {
        return isPeshka(animatable)
                ? PESHKA_ANIMATION
                : LADYA_ANIMATION;
    }
}