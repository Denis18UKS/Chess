package com.chess.client;

import com.chess.ChessGraffitiToolItem;
import com.chess.ChessNetwork;
import com.chess.ModBlockEntities;
import com.chess.ModBlocks;
import com.chess.ModItems;
import com.chess.client.renderer.ChessFigureRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

public class ChessModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererFactories.register(ModBlockEntities.CHESS_FIGURE, ChessFigureRenderer::new);
        ChessClientNetwork.registerClient();
        ChessWorldHighlights.register();
        ChessClockHud.register();
        KeyBinding switchTeamKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.chess.switch_team", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.chess"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (switchTeamKey.wasPressed()) {
                if (ChessClientNetwork.developerMode) ChessClientNetwork.switchDevTeam();
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            var stack = player.getStackInHand(hand);
            if (world.isClient && stack.isOf(ModItems.CHESS_CONFIG_TOOL)) {
                MinecraftClient client = MinecraftClient.getInstance();
                client.setScreen(new ChessConfiguratorScreen(client.currentScreen, hit.getBlockPos()));
                return ActionResult.SUCCESS;
            }
            if (world.isClient && world.getBlockState(hit.getBlockPos()).isOf(ModBlocks.CHESS_SETTINGS_PANEL)) {
                MinecraftClient client = MinecraftClient.getInstance();
                client.setScreen(new ChessSettingsScreen(client.currentScreen, hit.getBlockPos()));
                return ActionResult.SUCCESS;
            }
            // Let the figure block's own onUse handler apply the dye in both 2D and 3D worlds.
            if (stack.getItem() instanceof net.minecraft.item.DyeItem) return ActionResult.PASS;
            if (world.isClient && ChessClientNetwork.threeDimensional && !player.isSneaking()) {
                BlockPos clickedPos = hit.getBlockPos();
                var clickedState = world.getBlockState(clickedPos);
                if (clickedState.isOf(ModBlocks.CHESS_WHITE_SQUARE) || clickedState.isOf(ModBlocks.CHESS_BLACK_SQUARE)) {
                    BlockPos lowerModelHit = findVisibleFigureHit(player, world, hit.getPos());
                    if (lowerModelHit != null) {
                        clickedPos = lowerModelHit;
                        clickedState = world.getBlockState(clickedPos);
                    }
                }
                var clickedPiece = com.chess.ChessPieceType.fromBlock(clickedState);
                if (clickedPiece != null) {
                    BlockPos selected = ChessWorldHighlights.selectedPosition();
                    if (selected == null) ChessClientNetwork.select3D(clickedPos);
                    else {
                        char moving = ChessBoardScreen.cellAt(ChessWorldHighlights.selectedRow(), ChessWorldHighlights.selectedCol());
                        if (moving != '.' && Character.isUpperCase(moving) == clickedPiece.isWhite()) ChessClientNetwork.select3D(clickedPos);
                        else ChessClientNetwork.move3D(clickedPos);
                    }
                    return ActionResult.SUCCESS;
                }
                if (clickedState.isOf(ModBlocks.CHESS_WHITE_SQUARE) || clickedState.isOf(ModBlocks.CHESS_BLACK_SQUARE)) {
                    if (ChessWorldHighlights.selectedPosition() != null) ChessClientNetwork.move3D(clickedPos);
                    return ActionResult.SUCCESS;
                }
            }
            if (!player.isSneaking() && ModBlocks.isFigureItem(stack)) {
                if (world.isClient && ChessClientNetwork.threeDimensional) return ActionResult.PASS;
                if (world.isClient) openBoard(stack.getItem());
                return ActionResult.SUCCESS;
            }
            if (stack.isOf(ModItems.CHESS_ASSET_STUDIO_TOOL)) {
                if (world.isClient) MinecraftClient.getInstance().setScreen(new ChessAssetStudioScreen(MinecraftClient.getInstance().currentScreen));
                return world.isClient ? ActionResult.SUCCESS : ActionResult.PASS;
            }
            if (stack.isOf(ModItems.CHESS_GRAFFITI_TOOL)) {
                if (world.isClient) MinecraftClient.getInstance().setScreen(new ChessGraffitiScreen(
                    MinecraftClient.getInstance().currentScreen, hit.getBlockPos()));
                return world.isClient ? ActionResult.SUCCESS : ActionResult.PASS;
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getStackInHand(hand);
            if (!player.isSneaking() && ModBlocks.isFigureItem(stack)) {
                if (world.isClient && ChessClientNetwork.threeDimensional) return TypedActionResult.pass(stack);
                if (world.isClient) openBoard(stack.getItem());
                return TypedActionResult.success(stack);
            }
            if (stack.isOf(ModItems.CHESS_ASSET_STUDIO_TOOL)) {
                if (world.isClient) MinecraftClient.getInstance().setScreen(new ChessAssetStudioScreen(MinecraftClient.getInstance().currentScreen));
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.pass(stack);
        });
    }

    /**
     * Block raycasts are voxel-based: the chess model is rendered below its logical block.
     * If the board tile wins the normal raycast first, test visible chess model bounds and
     * route the click to the nearest figure that lies in front of the tile hit.
     */
    private static BlockPos findVisibleFigureHit(net.minecraft.entity.player.PlayerEntity player,
                                                  net.minecraft.world.World world, Vec3d tileHitPos) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d end = start.add(player.getRotationVec(1.0F).multiply(6.0));
        double bestDistance = tileHitPos.squaredDistanceTo(start);
        BlockPos best = null;
        BlockPos origin = ChessBoardScreen.getBoardOrigin();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (ChessBoardScreen.cellAt(row, col) == '.') continue;
                BlockPos piecePos = origin.add(col, 1, row);
                if (com.chess.ChessPieceType.fromBlock(world.getBlockState(piecePos)) == null) continue;
                // This AABB follows the model from the 2px board surface to its crown/top.
                Box modelBounds = new Box(piecePos.getX() + 0.06, piecePos.getY() - 0.875, piecePos.getZ() + 0.06,
                    piecePos.getX() + 0.94, piecePos.getY() + 0.95, piecePos.getZ() + 0.94);
                var intercept = modelBounds.raycast(start, end);
                if (intercept.isEmpty()) continue;
                double distance = intercept.get().squaredDistanceTo(start);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = piecePos;
                }
            }
        }
        return best;
    }

    private static void openBoard(net.minecraft.item.Item item) {
        MinecraftClient.getInstance().setScreen(new ChessBoardScreen(Registries.ITEM.getId(item).getPath()));
        ChessClientNetwork.requestBoard();
    }
}
