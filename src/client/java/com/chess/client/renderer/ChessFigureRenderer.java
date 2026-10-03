package com.chess.client.renderer;

import com.chess.ChessFigureBlockEntity;
import com.chess.client.model.ChessLadyaModel;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class ChessFigureRenderer extends GeoBlockRenderer<ChessFigureBlockEntity> {

    public ChessFigureRenderer(BlockEntityRendererFactory.Context context) {
        super(new ChessLadyaModel());
    }
}