package com.chess.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.chess.ChessFigureBlockEntity;
import com.chess.client.model.ChessLadyaModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class ChessFigureRenderer extends GeoBlockRenderer<ChessFigureBlockEntity> {
    public ChessFigureRenderer(BlockEntityRendererFactory.Context context) {
        super(new ChessLadyaModel());
    }

    @Override
    public void preRender(MatrixStack matrices, ChessFigureBlockEntity entity, BakedGeoModel model,
                          VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender,
                          float tickDelta, int light, int overlay, float red, float green, float blue, float alpha) {
        super.preRender(matrices, entity, model, bufferSource, buffer, isReRender,
            tickDelta, light, overlay, red, green, blue, alpha);

        // GeoBlockRenderer centers the model at (0.5, 0, 0.5) later in its render
        // pass. Conjugate the rotation around that center and lower the visual
        // model 14/16 block so its base sits on top of the 2px tile.
        matrices.translate(0.5, -0.875, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.getYawDegrees()));
        matrices.translate(-0.5, 0.0, -0.5);
    }
}
