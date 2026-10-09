package com.chess.client.model;

import com.chess.ChessFigureBlockEntity;
import com.chess.ChessMod;
import com.chess.ChessPieceType;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class ChessLadyaModel extends GeoModel<ChessFigureBlockEntity> {
    private ChessPieceType piece(ChessFigureBlockEntity entity) {
        ChessPieceType type = ChessPieceType.fromBlock(entity.getCachedState());
        return type == null ? ChessPieceType.WHITE_ROOK : type;
    }

    @Override
    public Identifier getModelResource(ChessFigureBlockEntity entity) {
        return ChessMod.id("geo/models/figures/" + piece(entity).model() + ".geo.json");
    }

    @Override
    public Identifier getTextureResource(ChessFigureBlockEntity entity) {
        ChessPieceType piece = piece(entity);
        String side = piece.isWhite() ? "white" : "black";
        return ChessMod.id("textures/generated/figures/" + side + "_" + piece.model() + ".png");
    }

    @Override
    public Identifier getAnimationResource(ChessFigureBlockEntity entity) {
        return ChessMod.id("animations/figures/" + piece(entity).model() + "/" + piece(entity).model() + ".animation.json");
    }
}
