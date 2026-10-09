package com.chess;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

/**
 * Rename this tool in an anvil to the desired label, then right-click a block.
 * Sneak-right-click cycles the stored label scale from 0.5x to 3x.
 */
public class ChessGraffitiToolItem extends Item {
    public ChessGraffitiToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!(context.getWorld() instanceof ServerWorld) || context.getPlayer() == null) return ActionResult.SUCCESS;
        NbtCompound tag = context.getStack().getOrCreateNbt();
        float scale = tag.contains("chess_graffiti_scale") ? tag.getFloat("chess_graffiti_scale") : 1.0f;
        if (context.getPlayer().isSneaking()) {
            scale = scale >= 3.0f ? 0.5f : scale + 0.5f;
            tag.putFloat("chess_graffiti_scale", scale);
            context.getPlayer().sendMessage(Text.literal("Размер надписи: " + scale + "x. Переименуйте инструмент в наковальне, чтобы задать текст."), false);
            return ActionResult.SUCCESS;
        }
        if (!context.getStack().hasCustomName()) {
            context.getPlayer().sendMessage(Text.literal("Сначала переименуйте инструмент в наковальне — это будет текст надписи."), false);
            return ActionResult.SUCCESS;
        }
        // The marker is stored as item NBT for use by the client-side graffiti renderer.
        tag.putString("chess_graffiti_text", context.getStack().getName().getString());
        tag.putLong("chess_graffiti_x", context.getBlockPos().asLong());
        context.getPlayer().sendMessage(Text.literal("Надпись подготовлена: «" + context.getStack().getName().getString() + "» (" + scale + "x)."), false);
        return ActionResult.SUCCESS;
    }
}
