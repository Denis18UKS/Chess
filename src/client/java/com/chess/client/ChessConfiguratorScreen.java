package com.chess.client;

import com.chess.ChessNetwork;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/** In-world configurator for the lobby, team spawns, command bindings and trigger zones. */
public final class ChessConfiguratorScreen extends Screen {
    private static final List<String> COMMANDS = Arrays.asList(
        "chess start", "chess reset", "chess termination", "chess pause", "chess turn",
        "chess white", "chess black", "chess 1_vs_bot", "chess one_one", "chess two_two",
        "chess realism", "chess no_realism", "chess full_realism", "chess tp go white",
        "chess tp go black", "team status", "tp @p 0 80 0", "spreadplayers 0 0 1.5 5 false @a",
        "gamemode adventure @p", "gamemode spectator @p", "effect give @p minecraft:glowing 10 0 true",
        "effect clear @p minecraft:glowing", "title @p actionbar {\"text\":\"Выбрана команда\"}",
        "playsound minecraft:block.note_block.pling master @p"
    );

    private final Screen parent;
    private final BlockPos targetPos;
    private TextFieldWidget commandField, zoneNameField, enterField, leaveField;
    private int suggestionOffset;

    public ChessConfiguratorScreen(Screen parent, BlockPos targetPos) {
        super(Text.literal("Chess — конфигуратор"));
        this.parent = parent;
        this.targetPos = targetPos.toImmutable();
    }

    @Override
    protected void init() {
        int x = Math.max(8, width / 2 - 145);
        int w = Math.min(290, width - 16);
        commandField = addDrawableChild(new TextFieldWidget(textRenderer, x, 54, w, 20, Text.literal("Команда блока")));
        commandField.setMaxLength(512);
        zoneNameField = addDrawableChild(new TextFieldWidget(textRenderer, x, 135, w, 20, Text.literal("Имя зоны")));
        zoneNameField.setMaxLength(48);
        enterField = addDrawableChild(new TextFieldWidget(textRenderer, x, 170, w, 20, Text.literal("Команда при входе")));
        enterField.setMaxLength(512);
        leaveField = addDrawableChild(new TextFieldWidget(textRenderer, x, 205, w, 20, Text.literal("Команда при выходе")));
        leaveField.setMaxLength(512);

        addDrawableChild(ButtonWidget.builder(Text.literal("Привязать команду к блоку"), b -> send("bind_command",
            commandField.getText(), "", "", 0, 0, 0)).dimensions(x, 235, w, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Снять привязку"), b -> {
            commandField.setText("");
            send("bind_command", "", "", "", 0, 0, 0);
        }).dimensions(x, 258, w, 20).build());

        int half = (w - 4) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Лобби здесь"), b -> send("set_lobby", "", "", "", 0, 0, 0))
            .dimensions(x, 286, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("ТП white"), b -> send("set_tp_white", "", "", "", 0, 0, 0))
            .dimensions(x + half + 4, 286, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("ТП black"), b -> send("set_tp_black", "", "", "", 0, 0, 0))
            .dimensions(x, 309, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Угол A зоны"), b -> send("zone_corner_a", "", "", "", 0, 0, 0))
            .dimensions(x + half + 4, 309, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Создать зону B"), b -> send("zone_define",
            zoneNameField.getText(), enterField.getText(), leaveField.getText(), 0, 0, 0))
            .dimensions(x, 332, w, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close())
            .dimensions(x + w / 2 - 50, 356, 100, 20).build());
    }

    private void send(String action, String a, String b, String c, int v1, int v2, int v3) {
        var buf = PacketByteBufs.create();
        buf.writeString(action, 64);
        buf.writeBlockPos(targetPos);
        buf.writeString(a == null ? "" : a, 1024);
        buf.writeString(b == null ? "" : b, 1024);
        buf.writeString(c == null ? "" : c, 1024);
        buf.writeInt(v1); buf.writeInt(v2); buf.writeInt(v3);
        ClientPlayNetworking.send(ChessNetwork.ADMIN_ACTION, buf);
    }

    private List<String> suggestions() {
        String search = commandField == null ? "" : commandField.getText().toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String command : COMMANDS) if (search.isBlank() || command.toLowerCase(Locale.ROOT).contains(search)) matches.add(command);
        return matches;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB && commandField != null && commandField.isFocused()) {
            List<String> found = suggestions();
            if (!found.isEmpty()) {
                suggestionOffset = (suggestionOffset + 1) % found.size();
                commandField.setText(found.get(suggestionOffset));
                commandField.setCursorToEnd();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (commandField != null && mouseY >= 77 && mouseY < 131) {
            List<String> found = suggestions();
            int idx = (int)((mouseY - 77) / 18);
            if (idx >= 0 && idx < Math.min(3, found.size())) {
                commandField.setText(found.get(idx));
                commandField.setCursorToEnd();
                commandField.setFocused(true);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Целевой блок: " + targetPos.getX() + " " + targetPos.getY() + " " + targetPos.getZ()),
            width / 2, 29, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, "Команда (Tab — автодополнение, щёлкни подсказку):",
            Math.max(8, width / 2 - 145), 41, 0xDDDDDD);

        List<String> found = suggestions();
        int count = Math.min(3, found.size());
        int sx = Math.max(8, width / 2 - 145);
        for (int i = 0; i < count; i++) {
            int sy = 77 + i * 18;
            int color = mouseX >= sx && mouseX <= sx + Math.min(290, width - 16)
                && mouseY >= sy && mouseY < sy + 17 ? 0xFF777777 : 0xFF303030;
            context.fill(sx, sy, sx + Math.min(290, width - 16), sy + 17, color);
            context.drawTextWithShadow(textRenderer, found.get(i), sx + 5, sy + 4, 0xEEEEEE);
        }
        context.drawTextWithShadow(textRenderer, "Имя зоны", Math.max(8, width / 2 - 145), 123, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, "Команда при входе", Math.max(8, width / 2 - 145), 158, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, "Команда при выходе (отмена эффекта)", Math.max(8, width / 2 - 145), 193, 0xDDDDDD);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
