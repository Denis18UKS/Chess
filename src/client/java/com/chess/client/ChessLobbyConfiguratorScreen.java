package com.chess.client;

import com.chess.ChessNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Dedicated one-purpose screen for saving the lobby arrival/return location. */
public final class ChessLobbyConfiguratorScreen extends Screen {
    private final Screen parent;
    private final BlockPos targetPos;

    public ChessLobbyConfiguratorScreen(Screen parent, BlockPos targetPos) {
        super(Text.literal("Chess — точка лобби"));
        this.parent = parent;
        this.targetPos = targetPos.toImmutable();
    }

    @Override
    protected void init() {
        int center = width / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить точку лобби"), button -> save())
            .dimensions(center - 110, 120, 220, 24).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), button -> close())
            .dimensions(center - 55, 166, 110, 22).build());
    }

    private void save() {
        var buf = PacketByteBufs.create();
        buf.writeString("set_lobby", 64);
        buf.writeBlockPos(targetPos);
        buf.writeString("", 1024); buf.writeString("", 1024); buf.writeString("", 1024);
        buf.writeInt(0); buf.writeInt(0); buf.writeInt(0);
        ClientPlayNetworking.send(ChessNetwork.ADMIN_ACTION, buf);
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 34, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Опорный блок: " + targetPos.getX() + " " + targetPos.getY() + " " + targetPos.getZ()),
            width / 2, 64, 0xDDDDDD);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Выбранный блок будет полом точки появления в лобби."),
            width / 2, 84, 0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Направление взгляда сохранится как направление появления."),
            width / 2, 100, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
