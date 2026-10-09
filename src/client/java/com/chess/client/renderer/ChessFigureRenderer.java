package com.chess.client.renderer;

import com.chess.ChessFigureBlockEntity;
import com.chess.client.model.ChessLadyaModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class ChessFigureRenderer extends GeoBlockRenderer<ChessFigureBlockEntity> {
    public ChessFigureRenderer(BlockEntityRendererFactory.Context context) {
        super(new ChessLadyaModel());
    }

    @Override
    public void render(ChessFigureBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertices, int light, int overlay) {
        matrices.push();

        // The block entity is one block above the board for placement/rules, but
        // its model is lowered by 14/16 block so its base lands on the 2px tile.
        // Rotate each placed figure independently around its own vertical axis.
        matrices.translate(0.5, -0.875, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.getYawDegrees()));
        matrices.translate(-0.5, 0.0, -0.5);

        super.render(entity, tickDelta, matrices, vertices, light, overlay);
        matrices.pop();
    }
}
