package com.chess;

import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

/**
 * Click a normal support block to generate an 8x8 board one block above it.
 * Click an existing Chess square to bind the board origin; sneak-clicking an
 * existing Chess square rebuilds the 8x8 pattern from that square.
 */
public class ChessBoardToolItem extends Item {
    public ChessBoardToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!(context.getWorld() instanceof ServerWorld world) || context.getPlayer() == null) {
            return ActionResult.SUCCESS;
        }

        BlockPos clicked = context.getBlockPos();
        BlockState clickedState = world.getBlockState(clicked);
        boolean isChessSquare = clickedState.isOf(ModBlocks.CHESS_WHITE_SQUARE)
            || clickedState.isOf(ModBlocks.CHESS_BLACK_SQUARE);

        if (isChessSquare && !context.getPlayer().isSneaking()) {
            ChessGameManager.configureBoard(world, clicked);
            context.getPlayer().sendMessage(Text.literal("Поле привязано. /chessboard check проверит 64 клетки."), false);
        } else {
            BlockPos firstSquare = isChessSquare ? clicked : clicked.up();
            ChessGameManager.buildBoard(world, firstSquare);
            context.getPlayer().sendMessage(Text.literal("Доска 8×8 создана. Фигуры ставьте на один блок выше; /chessboard check — для проверки."), false);
        }
        return ActionResult.SUCCESS;
    }
}
