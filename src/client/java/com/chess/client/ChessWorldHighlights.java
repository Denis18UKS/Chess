package com.chess.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Transparent legal-move overlays for 3D world mode. */
public final class ChessWorldHighlights {
    private static BlockPos selected;
    private static int selectedRow = -1, selectedCol = -1;
    private static boolean[] legal = new boolean[64];

    private ChessWorldHighlights() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ChessWorldHighlights::render);
    }

    public static void update(BlockPos selectedPos, int row, int col, boolean[] legalCells) {
        selected = row < 0 || col < 0 ? null : selectedPos.toImmutable();
        selectedRow = selected == null ? -1 : row;
        selectedCol = selected == null ? -1 : col;
        legal = legalCells == null ? new boolean[64] : legalCells.clone();
    }

    public static BlockPos selectedPosition() { return selected; }
    public static int selectedRow() { return selectedRow; }
    public static int selectedCol() { return selectedCol; }

    private static void render(WorldRenderContext context) {
        if (!ChessClientNetwork.threeDimensional || selected == null || context.world() == null
            || context.matrixStack() == null || context.consumers() == null) return;
        BlockPos origin = ChessBoardScreen.getBoardOrigin();
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider.Immediate consumers = context.consumers();
        VertexConsumer fill = consumers.getBuffer(RenderLayer.getDebugFilledBox());
        char moving = ChessBoardScreen.cellAt(selectedRow, selectedCol);
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            boolean canMove = legal[row * 8 + col];
            // Every cell is explicitly marked: lime means legal, red means forbidden.
            float red = canMove ? 0.30f : 1.0f;
            float green = canMove ? 1.0f : 0.06f;
            float blue = canMove ? 0.10f : 0.06f;
            float alpha = canMove ? 0.40f : 0.16f;
            Box cell = new Box(origin.getX() + col + 0.04, origin.getY() + 0.127, origin.getZ() + row + 0.04,
                origin.getX() + col + 0.96, origin.getY() + 0.152, origin.getZ() + row + 0.96)
                .offset(-camera.x, -camera.y, -camera.z);
            WorldRenderer.drawBox(matrices, fill, cell, red, green, blue, alpha);
        }
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        Box selectedBox = new Box(selected.getX() + 0.08, origin.getY() + 0.125, selected.getZ() + 0.08,
            selected.getX() + 0.92, origin.getY() + 1.38, selected.getZ() + 0.92)
            .offset(-camera.x, -camera.y, -camera.z);
        WorldRenderer.drawBox(matrices, lines, selectedBox, 0.95f, 1.0f, 0.12f, 1.0f);
    }
}
