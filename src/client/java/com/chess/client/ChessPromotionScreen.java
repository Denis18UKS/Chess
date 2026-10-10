package com.chess.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Shared pawn-promotion chooser for both 2D and 3D matches. */
public class ChessPromotionScreen extends Screen {
    private final Screen parent;
    private final String choices;
    private final boolean white;
    private final int row, col;
    private boolean chosen;

    public ChessPromotionScreen(Screen parent, String choices, boolean white, int row, int col) {
        super(Text.literal("Превращение пешки"));
        this.parent = parent;
        this.choices = choices == null || choices.isEmpty() ? "QRBN" : choices;
        this.white = white;
        this.row = row;
        this.col = col;
    }

    @Override
    protected void init() {
        int totalWidth = choices.length() * 54 + Math.max(0, choices.length() - 1) * 8;
        int left = (width - totalWidth) / 2;
        for (int i = 0; i < choices.length(); i++) {
            char piece = choices.charAt(i);
            String name = piece == 'Q' ? "Ферзь" : piece == 'R' ? "Ладья" : piece == 'B' ? "Слон" : "Конь";
            final char selectedPiece = piece;
            addDrawableChild(ButtonWidget.builder(Text.literal(name), button -> {
                chosen = true;
                ChessClientNetwork.choosePromotion(selectedPiece);
                close();
            }).dimensions(left + i * 62, height / 2 + 20, 54, 22).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal((white ? "Белая" : "Чёрная") + " пешка · " + (char)('a' + col) + (8 - row)),
            width / 2, height / 2 - 24, 0xFFDDC28A);
        int left = width / 2 - choices.length() * 31;
        for (int i = 0; i < choices.length(); i++) {
            char piece = choices.charAt(i);
            String glyph = piece == 'Q' ? (white ? "♕" : "♛")
                : piece == 'R' ? (white ? "♖" : "♜")
                : piece == 'B' ? (white ? "♗" : "♝")
                : (white ? "♘" : "♞");
            context.getMatrices().push();
            context.getMatrices().translate(left + i * 62 + 16, height / 2 + 2, 0);
            context.getMatrices().scale(2.1f, 2.1f, 1.0f);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(glyph), 0, -4,
                white ? 0xFFF8F2E5 : 0xFF202028);
            context.getMatrices().pop();
        }
        super.render(context, mouseX, mouseY, delta);
    }

    public void closeIfResolved() {
        if (!chosen) close();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
