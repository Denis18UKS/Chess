package com.chess.client;

import com.chess.ChessRules;
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
import org.joml.Matrix4f;

/** Minimal, flat overlays for chess moves; uses one quad per square to avoid debug-box face artifacts. */
public final class ChessWorldHighlights {
    private static BlockPos selected;
    private static int selectedRow = -1, selectedCol = -1;
    private static boolean[] legal = new boolean[64];
    private static String cells = "................................................................";
    private static BlockPos checkKingSquare;
    private static BlockPos boardOrigin = BlockPos.ORIGIN;

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

    public static void updateBoardState(String board, boolean whiteTurn, String rules, boolean threeD, BlockPos origin) {
        if (board != null && board.length() == 64) cells = board;
        boardOrigin = origin == null ? BlockPos.ORIGIN : origin.toImmutable();
        checkKingSquare = null;
        if (cells.length() != 64) return;
        char[][] matrix = new char[8][8];
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++)
            matrix[row][col] = cells.charAt(row * 8 + col);
        if (!ChessRules.isInCheck(matrix, whiteTurn)) return;
        char king = whiteTurn ? 'K' : 'k';
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            if (matrix[row][col] == king) {
                checkKingSquare = boardOrigin.add(col, 0, row);
                return;
            }
        }
    }

    public static boolean isCheckCell(int row, int col) {
        return checkKingSquare != null
            && checkKingSquare.getX() == boardOrigin.getX() + col
            && checkKingSquare.getZ() == boardOrigin.getZ() + row;
    }

    public static BlockPos selectedPosition() { return selected; }
    public static int selectedRow() { return selectedRow; }
    public static int selectedCol() { return selectedCol; }

    private static void render(WorldRenderContext context) {
        if (!ChessClientNetwork.threeDimensional || context.world() == null
            || context.matrixStack() == null || context.consumers() == null) return;
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer fill = consumers.getBuffer(RenderLayer.getDebugQuads());

        // A single flat quad per cell has no side faces or triangulated gradients.
        if (selected != null) {
            for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
                boolean canMove = legal[row * 8 + col];
                int red = canMove ? 68 : 240;
                int green = canMove ? 245 : 44;
                int blue = canMove ? 28 : 40;
                int alpha = canMove ? 92 : 30;
                double x1 = boardOrigin.getX() + col + 0.035 - camera.x;
                double x2 = boardOrigin.getX() + col + 0.965 - camera.x;
                double z1 = boardOrigin.getZ() + row + 0.035 - camera.z;
                double z2 = boardOrigin.getZ() + row + 0.965 - camera.z;
                double y = boardOrigin.getY() + 0.15 - camera.y;
                quad(fill, matrix, x1, z1, x2, z2, y, red, green, blue, alpha);
            }

        }

        if (checkKingSquare != null) {
            double x1 = checkKingSquare.getX() + 0.035 - camera.x;
            double x2 = checkKingSquare.getX() + 0.965 - camera.x;
            double z1 = checkKingSquare.getZ() + 0.035 - camera.z;
            double z2 = checkKingSquare.getZ() + 0.965 - camera.z;
            double y = checkKingSquare.getY() + 0.15 - camera.y;
            quad(fill, matrix, x1, z1, x2, z2, y, 255, 20, 20, 118);
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, double x1, double z1,
                             double x2, double z2, double y, int red, int green, int blue, int alpha) {
        consumer.vertex(matrix, (float)x1, (float)y, (float)z1).color(red, green, blue, alpha).next();
        consumer.vertex(matrix, (float)x1, (float)y, (float)z2).color(red, green, blue, alpha).next();
        consumer.vertex(matrix, (float)x2, (float)y, (float)z2).color(red, green, blue, alpha).next();
        consumer.vertex(matrix, (float)x2, (float)y, (float)z1).color(red, green, blue, alpha).next();
    }
}
