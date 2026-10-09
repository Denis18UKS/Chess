package com.chess;

import org.joml.Quaternionf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

/** Opens a client-side GUI; actual placement is sent to the server from that GUI. */
public class ChessGraffitiToolItem extends Item {
    public ChessGraffitiToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        // Prevent the previous anvil-based flow. The client callback opens
        // ChessGraffitiScreen and sends the selected text/color/scale via C2S.
        return ActionResult.SUCCESS;
    }

    public static void placeText(ServerPlayerEntity player, BlockPos clicked, String rawText,
                                 float scale, int rgb, float yawDegrees) {
        ServerWorld world = player.getServerWorld();
        String label = rawText == null ? "" : rawText.trim();
        if (label.isEmpty()) {
            player.sendMessage(Text.literal("Введите текст надписи."), false);
            return;
        }
        if (label.length() > 180) label = label.substring(0, 180);
        scale = Math.max(0.25f, Math.min(4.0f, scale));
        rgb &= 0xFFFFFF;

        DisplayEntity.TextDisplayEntity display = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        NbtCompound nbt = new NbtCompound();
        Text styled = Text.literal(label).styled(style -> style.withColor(TextColor.fromRgb(rgb)));
        nbt.putString("text", Text.Serializer.toJson(styled));
        nbt.putString("billboard", "fixed");
        nbt.putInt("background", 0);
        nbt.putBoolean("default_background", false);
        nbt.putByte("text_opacity", (byte) 255);
        nbt.putBoolean("shadow", true);
        nbt.putBoolean("see_through", true);
        nbt.putInt("line_width", 1000);

        float radians = (float) Math.toRadians(yawDegrees);
        Quaternionf orientation = new Quaternionf().rotationY(radians)
            .mul(new Quaternionf().rotationX((float) (Math.PI / 2.0)));
        NbtCompound transform = new NbtCompound();
        transform.put("left_rotation", floatList(orientation.x, orientation.y, orientation.z, orientation.w));
        transform.put("right_rotation", floatList(0.0f, 0.0f, 0.0f, 1.0f));
        transform.put("scale", floatList(scale, scale, scale));
        transform.put("translation", floatList(0.0f, 0.0f, 0.0f));
        nbt.put("transformation", transform);

        display.readNbt(nbt);
        display.refreshPositionAndAngles(clicked.getX() + 0.5, clicked.getY() + 1.002, clicked.getZ() + 0.5, yawDegrees, 0.0f);
        display.setInvulnerable(true);
        world.spawnEntity(display);
        player.sendMessage(Text.literal("Надпись размещена: «" + label + "»."), false);
    }

    private static NbtList floatList(float... values) {
        NbtList list = new NbtList();
        for (float value : values) list.add(NbtFloat.of(value));
        return list;
    }
}
