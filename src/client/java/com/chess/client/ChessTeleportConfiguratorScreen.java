package com.chess.client;

import com.chess.ChessNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Tabbed editor for lobby, both team spawn points and the spectator position. */
public final class ChessTeleportConfiguratorScreen extends Screen {
    private final Screen parent;
    private final BlockPos targetPos;
    private final int tab;

    public ChessTeleportConfiguratorScreen(Screen parent, BlockPos targetPos) {
        this(parent, targetPos, 0);
    }

    private ChessTeleportConfiguratorScreen(Screen parent, BlockPos targetPos, int tab) {
        super(Text.literal("Chess — точки телепортации"));
        this.parent = parent;
        this.targetPos = targetPos.toImmutable();
        this.tab = Math.max(0, Math.min(3, tab));
    }

    @Override
    protected void init() {
        int center = width / 2;
        int left = Math.max(8, center - 145);
        int widthAvailable = Math.min(290, width - 16);
        int tabWidth = (widthAvailable - 6) / 4;
        String[] tabs = {"Лобби", "Белые", "Чёрные", "Наблюдение"};
        for (int i = 0; i < tabs.length; i++) {
            final int next = i;
            String label = i == tab ? "▶ " + tabs[i] : tabs[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(label),
                button -> client.setScreen(new ChessTeleportConfiguratorScreen(parent, targetPos, next)))
                .dimensions(left + i * (tabWidth + 2), 76, tabWidth, 22).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить точку на этом блоке"), button -> save())
            .dimensions(center - 130, 156, 260, 24).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), button -> close())
            .dimensions(center - 60, 204, 120, 22).build());
    }

    private String action() {
        return switch (tab) {
            case 1 -> "set_tp_white";
            case 2 -> "set_tp_black";
            case 3 -> "set_spectator";
            default -> "set_lobby";
        };
    }

    private void save() {
        var buf = PacketByteBufs.create();
        buf.writeString(action(), 64);
        buf.writeBlockPos(targetPos);
        buf.writeString("", 1024); buf.writeString("", 1024); buf.writeString("", 1024);
        buf.writeInt(0); buf.writeInt(0); buf.writeInt(0);
        ClientPlayNetworking.send(ChessNetwork.ADMIN_ACTION, buf);
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 28, 0xFFFFFF);
        String[] labels = {
            "ЛОББИ — появление вне партии и возврат после игры",
            "БЕЛЫЕ — стартовая позиция белых",
            "ЧЁРНЫЕ — стартовая позиция чёрных",
            "НАБЛЮДЕНИЕ — безопасная точка обзора"
        };
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(labels[tab]), width / 2, 54, 0xE5E5E5);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Опорный блок: " + targetPos.getX() + " " + targetPos.getY() + " " + targetPos.getZ()),
            width / 2, 117, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Наведи инструмент на блок, который будет полом в точке появления."),
            width / 2, 137, 0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Направление взгляда сохранится по направлению, куда смотрит настройщик."),
            width / 2, 188, 0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Для каждой вкладки выбери свой блок и сохрани его отдельно."),
            width / 2, 240, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
