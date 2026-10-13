package com.chess.client;

import com.chess.ChessNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Dedicated, discoverable editor for custom trigger areas and team-side zones. */
public final class ChessTriggerZoneConfiguratorScreen extends Screen {
    private final Screen parent;
    private final BlockPos targetPos;
    private TextFieldWidget nameField;
    private TextFieldWidget enterField;
    private TextFieldWidget leaveField;

    public ChessTriggerZoneConfiguratorScreen(Screen parent, BlockPos targetPos) {
        super(Text.literal("Chess — триггерные области"));
        this.parent = parent;
        this.targetPos = targetPos.toImmutable();
    }

    @Override
    protected void init() {
        int available = Math.min(320, width - 16);
        int left = (width - available) / 2;
        int half = (available - 6) / 2;

        nameField = addDrawableChild(new TextFieldWidget(textRenderer, left, 76, available, 20,
            Text.literal("Имя области")));
        nameField.setMaxLength(48);
        nameField.setPlaceholder(Text.literal("например, entrance"));

        enterField = addDrawableChild(new TextFieldWidget(textRenderer, left, 112, available, 20,
            Text.literal("Команда при входе")));
        enterField.setMaxLength(512);
        enterField.setPlaceholder(Text.literal("Команда без /"));

        leaveField = addDrawableChild(new TextFieldWidget(textRenderer, left, 148, available, 20,
            Text.literal("Команда при выходе")));
        leaveField.setMaxLength(512);
        leaveField.setPlaceholder(Text.literal("необязательно"));

        addDrawableChild(ButtonWidget.builder(Text.literal("Угол A здесь"), b ->
            sendAndClose("zone_corner_a", "", "", ""))
            .dimensions(left, 176, half, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Создать угол B"), b ->
            sendAndClose("zone_define", nameField.getText(), enterField.getText(), leaveField.getText()))
            .dimensions(left + half + 6, 176, half, 22).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Угол A белых"), b ->
            sendAndClose("zone_white_corner_a", "", "", ""))
            .dimensions(left, 230, half, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Создать зону белых"), b ->
            sendAndClose("zone_white_define", "", "", ""))
            .dimensions(left + half + 6, 230, half, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Угол A чёрных"), b ->
            sendAndClose("zone_black_corner_a", "", "", ""))
            .dimensions(left, 256, half, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Создать зону чёрных"), b ->
            sendAndClose("zone_black_define", "", "", ""))
            .dimensions(left + half + 6, 256, half, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close())
            .dimensions(width / 2 - 50, 296, 100, 22).build());
    }

    private void sendAndClose(String action, String a, String b, String c) {
        var buf = PacketByteBufs.create();
        buf.writeString(action, 64);
        buf.writeBlockPos(targetPos);
        buf.writeString(a == null ? "" : a, 1024);
        buf.writeString(b == null ? "" : b, 1024);
        buf.writeString(c == null ? "" : c, 1024);
        buf.writeInt(0); buf.writeInt(0); buf.writeInt(0);
        ClientPlayNetworking.send(ChessNetwork.ADMIN_ACTION, buf);
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Текущий блок: " + targetPos.getX() + " " + targetPos.getY() + " " + targetPos.getZ()),
            width / 2, 30, 0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Обычная зона: A, затем открой инструмент на блоке B"),
            width / 2, 49, 0xE4D4AD);
        context.drawTextWithShadow(textRenderer, "Имя зоны", (width - Math.min(320, width - 16)) / 2, 64, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, "Команда при входе", (width - Math.min(320, width - 16)) / 2, 100, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, "Команда при выходе", (width - Math.min(320, width - 16)) / 2, 136, 0xDDDDDD);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Зоны выбора стороны: сначала угол A, затем угол B"),
            width / 2, 213, 0xE4D4AD);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
