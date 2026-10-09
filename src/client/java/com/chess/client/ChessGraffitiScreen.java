package com.chess.client;

import com.chess.ChessNetwork;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** In-game editor for persistent white/colored text display labels. */
public class ChessGraffitiScreen extends Screen {
    private static final int[] COLORS = {0xFFFFFF, 0xF4D58D, 0x8ED7FF, 0xFF8B8B, 0x9EE493, 0xD6A6FF};
    private static final String[] COLOR_NAMES = {"Белый", "Золотой", "Голубой", "Красный", "Зелёный", "Лиловый"};

    private final Screen parent;
    private final BlockPos target;
    private TextFieldWidget textField;
    private float scale = 1.0f;
    private float yaw;
    private int colorIndex;
    private String status = "Надпись будет сохранена в мире как Text Display.";

    public ChessGraffitiScreen(Screen parent, BlockPos target) {
        super(Text.literal("Chess — конфигуратор надписей"));
        this.parent = parent;
        this.target = target.toImmutable();
    }

    @Override
    protected void init() {
        int mid = width / 2;
        textField = new TextFieldWidget(textRenderer, mid - 140, 65, 280, 22, Text.literal("Текст надписи"));
        textField.setMaxLength(180);
        textField.setPlaceholder(Text.literal("Введите текст..."));
        addDrawableChild(textField);
        setInitialFocus(textField);

        addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> {
            scale = Math.max(0.25f, scale - 0.25f);
            clearAndInit();
        }).dimensions(mid - 115, 113, 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            scale = Math.min(4.0f, scale + 0.25f);
            clearAndInit();
        }).dimensions(mid + 91, 113, 24, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Цвет: " + COLOR_NAMES[colorIndex]), b -> {
            colorIndex = (colorIndex + 1) % COLORS.length;
            clearAndInit();
        }).dimensions(mid - 80, 113, 160, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Повернуть −90°"), b -> {
            yaw = (yaw + 270.0f) % 360.0f;
            clearAndInit();
        }).dimensions(mid - 145, 145, 140, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Повернуть +90°"), b -> {
            yaw = (yaw + 90.0f) % 360.0f;
            clearAndInit();
        }).dimensions(mid + 5, 145, 140, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Разместить надпись"), b -> place())
            .dimensions(mid - 100, 183, 200, 24).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), b -> close())
            .dimensions(mid - 50, 213, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 25, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Текст — прямо здесь, больше не нужна наковальня"), width / 2, 43, 0xFFCCCCCC);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Размер: " + String.format(java.util.Locale.ROOT, "%.2f", scale) + "x"), width / 2, 101, 0xFFFFFFFF);
        context.fill(width / 2 - 10, 138, width / 2 + 10, 148, COLORS[colorIndex] | 0xFF000000);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Поворот: " + (int) yaw + "°"), width / 2, 170, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Точка: " + target.getX() + " " + target.getY() + " " + target.getZ()), width / 2, height - 38, 0xFF999999);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height - 25, 0xFFFFE3A3);
        super.render(context, mouseX, mouseY, delta);
    }

    private void place() {
        String text = textField.getText() == null ? "" : textField.getText().trim();
        if (text.isEmpty()) {
            status = "Сначала введи текст надписи.";
            return;
        }
        if (MinecraftClient.getInstance().getNetworkHandler() == null) {
            status = "Нужно войти в одиночный мир или на сервер.";
            return;
        }
        var buf = PacketByteBufs.create();
        buf.writeBlockPos(target);
        buf.writeString(text, 256);
        buf.writeFloat(scale);
        buf.writeInt(COLORS[colorIndex]);
        buf.writeFloat(yaw);
        ClientPlayNetworking.send(ChessNetwork.GRAFFITI_CREATE, buf);
        close();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
