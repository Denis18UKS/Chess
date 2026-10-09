package com.chess;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Rename the tool in an anvil and right-click a block to place a persistent
 * horizontal, white text-display entity. Sneak-right-click cycles its scale
 * before placement. The entity is vanilla and therefore synchronized and saved
 * by Minecraft without a client-only render hack.
 */
public class ChessGraffitiToolItem extends Item {
    public ChessGraffitiToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!(context.getWorld() instanceof ServerWorld world) || context.getPlayer() == null) {
            return ActionResult.SUCCESS;
        }

        NbtCompound tag = context.getStack().getOrCreateNbt();
        float scale = tag.contains("chess_graffiti_scale") ? tag.getFloat("chess_graffiti_scale") : 0.5f;

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

        String label = context.getStack().getName().getString();
        if (label.length() > 180) label = label.substring(0, 180);
        BlockPos clicked = context.getBlockPos();

        DisplayEntity.TextDisplayEntity display = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        NbtCompound nbt = new NbtCompound();
        nbt.putString("text", Text.Serializer.toJson(Text.literal(label).formatted(Formatting.WHITE)));
        nbt.putString("billboard", "fixed");
        nbt.putInt("background", 0);
        nbt.putBoolean("default_background", false);
        nbt.putByte("text_opacity", (byte) 255);
        nbt.putBoolean("shadow", true);
        nbt.putBoolean("see_through", true);
        nbt.putInt("line_width", 1000);

        NbtCompound transform = new NbtCompound();
        transform.put("left_rotation", floatList(0.70710677f, 0.0f, 0.0f, 0.70710677f));
        transform.put("right_rotation", floatList(0.0f, 0.0f, 0.0f, 1.0f));
        transform.put("scale", floatList(scale, scale, scale));
        transform.put("translation", floatList(0.0f, 0.0f, 0.0f));
        nbt.put("transformation", transform);

        display.readNbt(nbt);
        display.refreshPositionAndAngles(clicked.getX() + 0.5, clicked.getY() + 1.002, clicked.getZ() + 0.5, 0.0f, 0.0f);
        display.setInvulnerable(true);
        world.spawnEntity(display);
        context.getPlayer().sendMessage(Text.literal("Надпись размещена на блоке: «" + label + "» (" + scale + "x)."), false);
        return ActionResult.SUCCESS;
    }

    private static NbtList floatList(float... values) {
        NbtList list = new NbtList();
        for (float value : values) list.add(NbtFloat.of(value));
        return list;
    }
}
