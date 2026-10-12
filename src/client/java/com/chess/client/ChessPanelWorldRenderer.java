package com.chess.client;

import com.chess.ChessNetwork;
import com.chess.ChessSettingsPanelBlock;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** World-mounted wooden chess control board. Every label is rendered without text backgrounds. */
public final class ChessPanelWorldRenderer {
    private static int modeIndex;
    private static long lastScanTick = Long.MIN_VALUE;
    private static World cachedWorld;
    private static final Set<BlockPos> cachedAnchors = new HashSet<>();
    private static final int PIXELS_PER_BLOCK = 64;
    private static final double PIXEL_SCALE = 1.0 / PIXELS_PER_BLOCK;

    private ChessPanelWorldRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ChessPanelWorldRenderer::renderWorld);
    }

    public static void setModeIndex(int index) {
        modeIndex = Math.max(0, Math.min(2, index));
    }

    public static void requestPanelState() {
        requestCurrentMode();
    }

    private static void requestCurrentMode() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !ClientPlayNetworking.canSend(ChessNetwork.ADMIN_ACTION)) return;
        var buf = PacketByteBufs.create();
        buf.writeString("get_panel_state", 64);
        buf.writeBlockPos(client.player.getBlockPos());
        buf.writeString("", 1024); buf.writeString("", 1024); buf.writeString("", 1024);
        buf.writeInt(0); buf.writeInt(0); buf.writeInt(0);
        ClientPlayNetworking.send(ChessNetwork.ADMIN_ACTION, buf);
    }

    public static boolean handleClick(World world, BlockHitResult hit) {
        if (!world.isClient) return false;
        BlockPos clicked = hit.getBlockPos();
        BlockState clickedState = world.getBlockState(clicked);
        if (!(clickedState.getBlock() instanceof ChessSettingsPanelBlock)) return false;
        Direction facing = clickedState.get(ChessSettingsPanelBlock.FACING);
        if (hit.getSide() != facing) return false;

        BlockPos anchor = findAnchor(world, clicked, facing);
        if (anchor == null) return false;
        double localX = hit.getPos().x - (anchor.getX() + 0.5);
        double localZ = hit.getPos().z - (anchor.getZ() + 0.5);
        double angle = switch (facing) {
            case SOUTH -> 0.0;
            case EAST -> Math.PI / 2.0;
            case NORTH -> Math.PI;
            case WEST -> Math.PI * 1.5;
            default -> 0.0;
        };
        double uLocal = Math.cos(angle) * localX - Math.sin(angle) * localZ;
        double u = (uLocal + 1.5) * PIXELS_PER_BLOCK;
        double v = (anchor.getY() + 3.0 - hit.getPos().y) * PIXELS_PER_BLOCK;

        String action = null;
        int value = 0;
        if (v >= 42 && v <= 82 && u >= 6 && u <= 186) {
            value = u < 64 ? 0 : u < 128 ? 1 : 2;
            action = "panel_mode";
            modeIndex = value;
        } else if (v >= 104 && v <= 145 && u >= 24 && u <= 168) {
            // Side is deliberately not chosen here: players select it by entering trigger zones.
            action = "panel_start";
        }
        if (action == null) return true;
        sendAction(action, anchor, value);
        return true;
    }

    private static void sendAction(String action, BlockPos anchor, int value) {
        var buf = PacketByteBufs.create();
        buf.writeString(action, 64);
        buf.writeBlockPos(anchor);
        buf.writeString("", 1024); buf.writeString("", 1024); buf.writeString("", 1024);
        buf.writeInt(value); buf.writeInt(0); buf.writeInt(0);
        ClientPlayNetworking.send(ChessNetwork.ADMIN_ACTION, buf);
    }

    private static BlockPos findAnchor(World world, BlockPos clicked, Direction facing) {
        Direction lateral = facing.rotateYCounterclockwise();
        List<BlockPos> candidates = new ArrayList<>();
        for (int dy = 0; dy <= 2; dy++) {
            for (int across = -1; across <= 1; across++) {
                BlockPos candidate = clicked.add(-lateral.getOffsetX() * across, -dy,
                    -lateral.getOffsetZ() * across);
                if (!candidateIsPanel(world, candidate, facing)) continue;
                if (!candidates.contains(candidate)) candidates.add(candidate);
            }
        }
        return candidates.stream().min(java.util.Comparator.comparingDouble(pos -> pos.getSquaredDistance(clicked))).orElse(null);
    }

    private static boolean candidateIsPanel(World world, BlockPos anchor, Direction facing) {
        Direction lateral = facing.rotateYCounterclockwise();
        for (int y = 0; y < 3; y++) for (int across = -1; across <= 1; across++) {
            BlockPos pos = anchor.add(lateral.getOffsetX() * across, y, lateral.getOffsetZ() * across);
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof ChessSettingsPanelBlock)
                || state.get(ChessSettingsPanelBlock.FACING) != facing) return false;
        }
        return true;
    }

    private static void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.textRenderer == null) return;
        long worldTick = client.world.getTime();
        if (cachedWorld != client.world || worldTick - lastScanTick >= 5L) {
            cachedWorld = client.world;
            lastScanTick = worldTick;
            cachedAnchors.clear();
            BlockPos playerPos = client.player.getBlockPos();
            for (int x = -12; x <= 12; x++) for (int y = -6; y <= 6; y++) for (int z = -12; z <= 12; z++) {
                BlockPos pos = playerPos.add(x, y, z);
                BlockState state = client.world.getBlockState(pos);
                if (state.getBlock() instanceof ChessSettingsPanelBlock
                    && candidateIsPanel(client.world, pos, state.get(ChessSettingsPanelBlock.FACING))) {
                    cachedAnchors.add(pos.toImmutable());
                }
            }
        }
        if (cachedAnchors.isEmpty()) return;
        VertexConsumerProvider.Immediate buffers = client.getBufferBuilders().getEntityVertexConsumers();
        for (BlockPos anchor : cachedAnchors) {
            BlockState state = client.world.getBlockState(anchor);
            if (state.getBlock() instanceof ChessSettingsPanelBlock)
                renderOne(context, client.textRenderer, buffers, anchor, state.get(ChessSettingsPanelBlock.FACING));
        }
        buffers.draw();
    }

    private static void renderOne(WorldRenderContext context, TextRenderer renderer,
                                  VertexConsumerProvider.Immediate buffers, BlockPos anchor, Direction facing) {
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(anchor.getX() + 0.5 - camera.x, anchor.getY() + 3.0 - camera.y,
            anchor.getZ() + 0.5 - camera.z);
        double angle = switch (facing) {
            case SOUTH -> 0.0;
            case EAST -> 90.0;
            case NORTH -> 180.0;
            case WEST -> 270.0;
            default -> 0.0;
        };
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) angle));
        matrices.translate(0.0, 0.0, 0.505);
        matrices.scale((float) PIXEL_SCALE, (float) -PIXEL_SCALE, (float) PIXEL_SCALE);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Inspired by the supplied wooden scoreboard: simple gold headings and clean text.
        // No repeated block-glyph bars and no per-line translucent rectangles.
        drawCentered(renderer, buffers, matrix, "ШАХМАТЫ", 96, 18, 0xFFFFB52E);
        drawCentered(renderer, buffers, matrix, "РЕЖИМ ПАРТИИ", 96, 42, 0xFFFFD16A);
        String[] modes = {"1v1", "2v2", "С БОТОМ"};
        int[] centers = {32, 96, 160};
        for (int i = 0; i < modes.length; i++) {
            boolean selected = i == modeIndex;
            String label = selected ? "[ " + modes[i] + " ]" : modes[i];
            drawCentered(renderer, buffers, matrix, label, centers[i], 62,
                selected ? 0xFFFFB52E : 0xFFF4E7C6);
        }
        drawCentered(renderer, buffers, matrix, "НАЧАТЬ ПАРТИЮ", 96, 121, 0xFFFFD16A);
        drawCentered(renderer, buffers, matrix, "СТОРОНА — ЧЕРЕЗ ЗОНУ", 96, 158, 0xFFF4E7C6);
        drawCentered(renderer, buffers, matrix, "3 • 2 • 1  →  ИГРА", 96, 178, 0xFFFFB52E);
        matrices.pop();
    }

    private static void drawCentered(TextRenderer renderer, VertexConsumerProvider consumers,
                                     Matrix4f matrix, String text, int centerX, int y, int color) {
        int x = centerX - renderer.getWidth(text) / 2;
        renderer.draw(Text.literal(text), x, y, color, true, matrix, consumers,
            TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }
}
