package com.chess.client.renderer;

import net.minecraft.client.render.VertexConsumer;
import com.chess.ChessFigureBlockEntity;
import com.chess.client.model.ChessLadyaModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
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
            tickDelta, light, overlay, red * entity.getTintRed(), green * entity.getTintGreen(),
            blue * entity.getTintBlue(), alpha);

        // Interpolate along the real server-provided destination, not mirrored directional clips.
        Vec3d offset = entity.getRenderOffset(tickDelta);
        matrices.translate(offset.x, offset.y, offset.z);
        // Rotate around the figure center and seat the visual model on the 2px tile.
        matrices.translate(0.5, -0.875, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.getYawDegrees()));
        matrices.translate(-0.5, 0.0, -0.5);
    }
}
