package com.chess.client.model;

import com.chess.ChessMod;
import com.chess.ChessFigureBlockEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

// Модель GeckoLib для ладьи.
public class ChessLadyaModel extends GeoModel<ChessFigureBlockEntity> {

    @Override
    public Identifier getModelResource(ChessFigureBlockEntity animatable) {
        // Geo-модель ладьи.
        return ChessMod.id(
                "geo/models/figures/ladya.geo.json");
    }

    @Override
    public Identifier getTextureResource(ChessFigureBlockEntity animatable) {
        // Текстура ладьи.
        return ChessMod.id(
                "textures/figures/white_figures/white_ladya.png");
    }

    @Override
    public Identifier getAnimationResource(
            ChessFigureBlockEntity animatable) {
        // Общий файл со всеми анимациями ладьи.
        return ChessMod.id(
                "animations/figures/ladya/ladya.animation.json");
    }
}