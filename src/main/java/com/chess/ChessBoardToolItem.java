package com.chess;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

/** Right-click the first (north-west) tile of an 8x8 field to anchor it. */
public class ChessBoardToolItem extends Item {
    public ChessBoardToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld() instanceof ServerWorld world && context.getPlayer() != null) {
            ChessGameManager.configureBoard(world, context.getBlockPos());
            context.getPlayer().sendMessage(Text.literal("Угол доски сохранён. Клетки идут по +X и +Z; фигуры стоят на блок выше."), false);
        }
        return ActionResult.success(context.getWorld().isClient);
    }
}
