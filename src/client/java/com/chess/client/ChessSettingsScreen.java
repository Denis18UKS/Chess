package com.chess.client;

import com.chess.ChessNetwork;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Arrow-driven Minecraft-style panel for a new chess match. */
public final class ChessSettingsScreen extends Screen {
    private static final String[] MODES = {"1v1", "2v2", "VS bot"};
    private static final String[] RULES = {"no_realism", "realism", "full_realism"};
    private static final int[] DURATIONS = {0, 10, 20, 30, 45, 60, 90, 120};
    private final Screen parent;
    private final BlockPos panelPos;
    private int modeIndex;
    private int ruleIndex = 1;
    private int durationIndex = 3;
    private ButtonWidget modeValue, ruleValue, durationValue;

    public ChessSettingsScreen(Screen parent, BlockPos pos) {
        super(Text.literal("Chess — настройки партии"));
        this.parent = parent;
        this.panelPos = pos.toImmutable();
    }

    public static void receiveSettings(int mode, int duration, int rule) {
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof ChessSettingsScreen screen) {
            screen.modeIndex = Math.max(0, Math.min(MODES.length - 1, mode));
            screen.ruleIndex = Math.max(0, Math.min(RULES.length - 1, rule));
            int nearest = 0;
            for (int i = 0; i < DURATIONS.length; i++) if (DURATIONS[i] == duration) nearest = i;
            screen.durationIndex = nearest;
            screen.refreshLabels();
        }
    }

    @Override
    protected void init() {
        int center = width / 2;
        int left = center - 120;
        addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> { modeIndex = (modeIndex + MODES.length - 1) % MODES.length; refreshLabels(); })
            .dimensions(left, 65, 26, 20).build());
        modeValue = addDrawableChild(ButtonWidget.builder(Text.literal(MODES[modeIndex]), b -> {})
            .dimensions(left + 30, 65, 180, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> { modeIndex = (modeIndex + 1) % MODES.length; refreshLabels(); })
            .dimensions(left + 214, 65, 26, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> { ruleIndex = (ruleIndex + RULES.length - 1) % RULES.length; refreshLabels(); })
            .dimensions(left, 113, 26, 20).build());
        ruleValue = addDrawableChild(ButtonWidget.builder(Text.literal(RULES[ruleIndex]), b -> {})
            .dimensions(left + 30, 113, 180, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> { ruleIndex = (ruleIndex + 1) % RULES.length; refreshLabels(); })
            .dimensions(left + 214, 113, 26, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> { durationIndex = (durationIndex + DURATIONS.length - 1) % DURATIONS.length; refreshLabels(); })
            .dimensions(left, 161, 26, 20).build());
        durationValue = addDrawableChild(ButtonWidget.builder(Text.literal(durationLabel()), b -> {})
            .dimensions(left + 30, 161, 180, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> { durationIndex = (durationIndex + 1) % DURATIONS.length; refreshLabels(); })
            .dimensions(left + 214, 161, 26, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Применить настройки"), b -> apply())
            .dimensions(center - 120, 215, 240, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Начать партию"), b -> startMatch())
            .dimensions(center - 120, 244, 240, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close())
            .dimensions(center - 60, 273, 120, 20).build());
        var buf = PacketByteBufs.create();
        buf.writeString("get_settings", 64);
        buf.writeBlockPos(panelPos);
        buf.writeString("", 1024); buf.writeString("", 1024); buf.writeString("", 1024);
        buf.writeInt(0); buf.writeInt(0); buf.writeInt(0);
        ClientPlayNetworking.send(ChessNetwork.ADMIN_ACTION, buf);
        refreshLabels();
    }

    private String durationLabel() {
        int value = DURATIONS[durationIndex];
        return value == 0 ? "Без общего лимита" : value + " мин. на всю партию";
    }

    private void refreshLabels() {
        if (modeValue != null) modeValue.setMessage(Text.literal(MODES[modeIndex]));
        if (ruleValue != null) ruleValue.setMessage(Text.literal(RULES[ruleIndex]));
        if (durationValue != null) durationValue.setMessage(Text.literal(durationLabel()));
    }

    private void apply() {
        var buf = PacketByteBufs.create();
        buf.writeString("settings", 64);
        buf.writeBlockPos(panelPos);
        buf.writeString("", 1024); buf.writeString("", 1024); buf.writeString("", 1024);
        buf.writeInt(modeIndex);
        buf.writeInt(DURATIONS[durationIndex]);
        buf.writeInt(ruleIndex);
        ClientPlayNetworking.send(ChessNetwork.ADMIN_ACTION, buf);
        close();
    }

    private void startMatch() {
        var settings = PacketByteBufs.create();
        settings.writeString("settings", 64);
        settings.writeBlockPos(panelPos);
        settings.writeString("", 1024); settings.writeString("", 1024); settings.writeString("", 1024);
        settings.writeInt(modeIndex);
        settings.writeInt(DURATIONS[durationIndex]);
        settings.writeInt(ruleIndex);
        ClientPlayNetworking.send(ChessNetwork.ADMIN_ACTION, settings);

        var start = PacketByteBufs.create();
        start.writeString("start_match", 64);
        start.writeBlockPos(panelPos);
        start.writeString("", 1024); start.writeString("", 1024); start.writeString("", 1024);
        start.writeInt(0); start.writeInt(0); start.writeInt(0);
        ClientPlayNetworking.send(ChessNetwork.ADMIN_ACTION, start);
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 28, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Режим игроков"), width / 2, 51, 0xD0D0D0);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Тип правил"), width / 2, 99, 0xD0D0D0);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Длительность всей партии"), width / 2, 147, 0xD0D0D0);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Настройки применятся к следующему запуску партии"), width / 2, 193, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
