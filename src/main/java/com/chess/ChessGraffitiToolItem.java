package com.chess;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;

/** Opens a client-side GUI; actual placement is sent to the server from that GUI. */
public class ChessGraffitiToolItem extends Item {
    public ChessGraffitiToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        // Prevent the previous anvil-based flow. The client callback opens
        // ChessGraffitiScreen and sends the selected text/color/scale via C2S.
        return ActionResult.SUCCESS;
    }
}
