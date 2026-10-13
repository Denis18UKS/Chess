package com.chess.client;

import com.chess.ChessNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** In-game controls for Fade duration and delay before teleportation. */
public final class ChessFadeConfiguratorScreen extends Screen {
    private final Screen parent;
    private final BlockPos targetPos;
    private int fadeTicks = 10;
    private int delayTicks = 14;
    private ButtonWidget fadeValue;
    private ButtonWidget delayValue;

    public ChessFadeConfiguratorScreen(Screen parent, BlockPos targetPos) {
        super(Text.literal("Chess — Fade"));
        this.parent = parent;
        this.targetPos = targetPos.toImmutable();
    }

    @Override
    protected void init() {
        int center = width / 2;
        int left = center - 126;
        addDrawableChild(ButtonWidget.builder(Text.literal("−"), button -> {
            fadeTicks = clampFade(fadeTicks - 2);
            refreshLabels();
        }).dimensions(left, 90, 28, 22).build());
        fadeValue = addDrawableChild(ButtonWidget.builder(Text.literal("0.5 с"), button -> {})
            .dimensions(left + 32, 90, 188, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> {
            fadeTicks = clampFade(fadeTicks + 2);
            refreshLabels();
        }).dimensions(left + 224, 90, 28, 22).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("−"), button -> {
            delayTicks = clampDelay(delayTicks - 2);
            refreshLabels();
        }).dimensions(left, 150, 28, 22).build());
        delayValue = addDrawableChild(ButtonWidget.builder(Text.literal("0.7 с"), button -> {})
            .dimensions(left + 32, 150, 188, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> {
            delayTicks = clampDelay(delayTicks + 2);
            refreshLabels();
        }).dimensions(left + 224, 150, 28, 22).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Получить текущие значения"), button -> requestSettings())
            .dimensions(center - 126, 198, 252, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"), button -> save())
            .dimensions(center - 126, 230, 252, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), button -> close())
            .dimensions(center - 60, 260, 120, 20).build());

        refreshLabels();
        requestSettings();
    }

    private static int clampFade(int ticks) { return Math.max(2, Math.min(100, ticks)); }
    private static int clampDelay(int ticks) { return Math.max(0, Math.min(200, ticks)); }

    private void refreshLabels() {
        if (fadeValue != null) fadeValue.setMessage(Text.literal(seconds(fadeTicks) + " с"));
        if (delayValue != null) {
            int effective = Math.max(fadeTicks, delayTicks);
            delayValue.setMessage(Text.literal(seconds(delayTicks) + " с"));
            delayValue.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                effective == delayTicks ? "Телепортация после указанной задержки."
                    : "Для безопасности телепортация произойдёт не раньше, чем экран полностью затемнится (" + seconds(effective) + " с).")));
        }
    }

    private static String seconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks * 0.05);
    }

    private void requestSettings() {
        var buf = PacketByteBufs.create();
        buf.writeString("get_fade_settings", 64);
        buf.writeBlockPos(targetPos);
        buf.writeString("", 1024); buf.writeString("", 1024); buf.writeString("", 1024);
        buf.writeInt(0); buf.writeInt(0); buf.writeInt(0);
        ClientPlayNetworking.send(ChessNetwork.ADMIN_ACTION, buf);
    }

    private void save() {
        var buf = PacketByteBufs.create();
        buf.writeString("set_fade", 64);
        buf.writeBlockPos(targetPos);
        buf.writeString("", 1024); buf.writeString("", 1024); buf.writeString("", 1024);
        buf.writeInt(fadeTicks); buf.writeInt(delayTicks); buf.writeInt(0);
        ClientPlayNetworking.send(ChessNetwork.ADMIN_ACTION, buf);
        close();
    }

    public static void receiveSettings(int fade, int delay) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof ChessFadeConfiguratorScreen screen) {
            screen.fadeTicks = clampFade(fade);
            screen.delayTicks = clampDelay(delay);
            screen.refreshLabels();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 36, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Длительность затемнения"), width / 2, 68, 0xE0E0E0);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Задержка до телепортации"), width / 2, 128, 0xE0E0E0);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Настройки действуют для всех участников перехода."), width / 2, 177, 0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Сначала Fade достигает чёрного экрана, затем выполняется телепортация."),
            width / 2, 285, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
