package com.chess.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Live 2D view of the physical 8x8 chess field. */
public class ChessBoardScreen extends Screen {
    private static String cells = "................................................................";
    private static boolean configured, running, paused, whiteTurn = true;
    private static String ruleMode = "REALISM", matchMode = "ONE_ONE";
    private static BlockPos origin = BlockPos.ORIGIN;
    private static String whiteCaptures = "", blackCaptures = "";
    private static boolean threeDimensional, developerMode, promotionPending;
    private static String promotionChoices = "QRBN";
    private static ChessBoardScreen activeScreen;
    private static AnimatedMove animatedMove;

    private static final class AnimatedMove {
        final int fromRow, fromCol, toRow, toCol, durationTicks;
        final char piece;
        final boolean knight;
        final long startedNanos;
        AnimatedMove(int fr, int fc, int tr, int tc, char piece, boolean knight, int durationTicks) {
            this.fromRow = fr; this.fromCol = fc; this.toRow = tr; this.toCol = tc;
            this.piece = piece; this.knight = knight; this.durationTicks = Math.max(1, durationTicks);
            this.startedNanos = System.nanoTime();
        }
    }

    public static void beginAnimatedMove(int fromRow, int fromCol, int toRow, int toCol, char piece, boolean knight, int durationTicks) {
        if (inside(fromRow, fromCol) && inside(toRow, toCol) && piece != '.')
            animatedMove = new AnimatedMove(fromRow, fromCol, toRow, toCol, piece, knight, durationTicks);
    }

    private static boolean inside(int row, int col) { return row >= 0 && row < 8 && col >= 0 && col < 8; }

    private final String heldItemPath;
    private boolean selectionInitialized;
    private int fromRow = -1, fromCol = -1, toRow = -1, toCol = -1;
    private int refreshTicks;

    public ChessBoardScreen(String heldItemPath) {
        super(Text.literal("Chess — поле"));
        this.heldItemPath = heldItemPath == null ? "" : heldItemPath;
    }

    public static void acceptSnapshot(String board, boolean hasBoard, boolean isRunning, boolean isPaused,
                                      boolean whitesTurn, String rules, String mode, BlockPos boardOrigin,
                                      boolean threeD, boolean devMode, boolean promotion, String choices,
                                      String whiteCaptured, String blackCaptured) {
        if (board != null && board.length() == 64) {
            cells = board;
            if (animatedMove != null
                && cells.charAt(animatedMove.fromRow * 8 + animatedMove.fromCol) == '.'
                && cells.charAt(animatedMove.toRow * 8 + animatedMove.toCol) == animatedMove.piece) {
                animatedMove = null;
            }
        }
        configured = hasBoard;
        running = isRunning;
        paused = isPaused;
        whiteTurn = whitesTurn;
        ruleMode = rules;
        matchMode = mode;
        origin = boardOrigin;
        threeDimensional = threeD;
        developerMode = devMode;
        promotionPending = promotion;
        promotionChoices = choices == null || choices.isEmpty() ? "QRBN" : choices;
        whiteCaptures = whiteCaptured == null ? "" : whiteCaptured;
        blackCaptures = blackCaptured == null ? "" : blackCaptured;
        ChessWorldHighlights.updateBoardState(cells, whiteTurn, ruleMode, threeD, origin);
        if (activeScreen != null && !activeScreen.selectionInitialized) activeScreen.autoSelectHeldPiece();
    }

    public static boolean isThreeDimensional() { return threeDimensional; }
    public static boolean isDeveloperMode() { return developerMode; }
    public static BlockPos getBoardOrigin() { return origin; }
    public static char cellAt(int row, int col) { return row >= 0 && row < 8 && col >= 0 && col < 8 ? cells.charAt(row * 8 + col) : '.'; }
    public static String getPromotionChoices() { return promotionChoices; }

    @Override
    protected void init() {
        super.init();
        activeScreen = this;
        ChessClientNetwork.requestBoard();
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks >= 10) {
            refreshTicks = 0;
            ChessClientNetwork.requestBoard();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        String status = !configured ? "Поле не настроено" : !running ? "Партия остановлена" : paused ? "ПАУЗА" : (whiteTurn ? "ХОД БЕЛЫХ" : "ХОД ЧЁРНЫХ");
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("ШАХМАТЫ  ·  " + status), width / 2, 12, whiteTurn && running && !paused ? 0xFFFFD75E : 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(ruleLabel(ruleMode) + "  ·  " + (matchMode.equals("TWO_TWO") ? "2v2" : "1v1")), width / 2, 28, 0xFFBBBBBB);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Срубили белые: " + whiteCaptures.length() + " · Срубили чёрные: " + blackCaptures.length()), width / 2, 39, 0xFFBBBBBB);

        int cell = Math.min(30, Math.min((width - 36) / 8, (height - 122) / 8));
        cell = Math.max(20, cell);
        int boardSize = cell * 8;
        int boardX = (width - boardSize) / 2;
        int boardY = Math.max(44, (height - boardSize - 56) / 2 + 10);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int x = boardX + col * cell, y = boardY + row * cell;
                boolean light = (row + col) % 2 == 0;
                int color = light ? 0xFFE8D9BC : 0xFF78906B;
                if (row == fromRow && col == fromCol) color = 0xFFDBB43F;
                if (row == toRow && col == toCol) color = 0xFF4AAB89;
                context.fill(x, y, x + cell, y + cell, color);
                char piece = cells.charAt(row * 8 + col);
                boolean movingSource = animatedMove != null
                    && row == animatedMove.fromRow && col == animatedMove.fromCol
                    && cells.charAt(row * 8 + col) == animatedMove.piece;
                if (piece != '.' && !movingSource) {
                    String iconKey = iconKey(piece);
                    context.drawTexture(new net.minecraft.util.Identifier("chess", "textures/item/" + iconKey + ".png"),
                        x + cell / 2 - 8, y + cell / 2 - 8, 16, 16, 0, 0, 32, 32, 32, 32);
                }
                if (row == 7) context.drawTextWithShadow(textRenderer, String.valueOf((char)('a' + col)), x + cell - 8, boardY + boardSize + 2, 0xFFCCCCCC);
                if (col == 0) context.drawTextWithShadow(textRenderer, String.valueOf(8 - row), boardX - 10, y + (cell - 8) / 2, 0xFFCCCCCC);
            }
        }

        renderAnimatedPiece(context, boardX, boardY, cell);

        String hint = !configured
            ? "Поставьте инструмент Chess Board Configurator на первую клетку"
            : ruleMode.equals("FULL_REALISM") ? "Полный реализм: выберите фигуру соответствующего предмета"
            : "Выберите фигуру на поле, затем клетку назначения";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(hint), width / 2, boardY + boardSize + 15, 0xFFFFFFFF);

        int buttonY = boardY + boardSize + 32;
        int confirmX = width / 2 - 92, cancelX = width / 2 + 4;
        context.fill(confirmX, buttonY, confirmX + 88, buttonY + 20, fromRow >= 0 && toRow >= 0 ? 0xFF267B50 : 0xFF444444);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Подтвердить"), confirmX + 44, buttonY + 6, 0xFFFFFFFF);
        context.fill(cancelX, buttonY, cancelX + 88, buttonY + 20, 0xFF575757);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Отмена"), cancelX + 44, buttonY + 6, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Поле: " + origin.getX() + " " + origin.getY() + " " + origin.getZ()), width / 2, height - 13, 0xFF888888);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int cell = Math.min(30, Math.min((width - 36) / 8, (height - 122) / 8));
        cell = Math.max(20, cell);
        int boardSize = cell * 8;
        int boardX = (width - boardSize) / 2;
        int boardY = Math.max(44, (height - boardSize - 56) / 2 + 10);
        if (mouseX >= boardX && mouseX < boardX + boardSize && mouseY >= boardY && mouseY < boardY + boardSize) {
            int col = (int)(mouseX - boardX) / cell;
            int row = (int)(mouseY - boardY) / cell;
            char selected = cells.charAt(row * 8 + col);
            if (fromRow < 0) {
                if (selected != '.' && matchesHeldPiece(selected)) {
                    fromRow = row; fromCol = col; toRow = toCol = -1;
                }
            } else if (row == fromRow && col == fromCol) {
                // The held inventory item already defines the selected source piece.
                // Clicking it again only clears the destination.
                toRow = toCol = -1;
            } else {
                // Do not silently switch to a different figure: clicks now always choose a destination.
                toRow = row; toCol = col;
            }
            return true;
        }
        int buttonY = boardY + boardSize + 32;
        if (mouseY >= buttonY && mouseY <= buttonY + 20) {
            if (mouseX >= width / 2 - 92 && mouseX <= width / 2 - 4 && fromRow >= 0 && toRow >= 0) {
                ChessClientNetwork.requestMove(fromRow, fromCol, toRow, toCol);
                fromRow = fromCol = toRow = toCol = -1;
                ChessClientNetwork.requestBoard();
                return true;
            }
            if (mouseX >= width / 2 + 4 && mouseX <= width / 2 + 92) {
                fromRow = fromCol = toRow = toCol = -1;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderAnimatedPiece(DrawContext context, int boardX, int boardY, int cell) {
        AnimatedMove move = animatedMove;
        if (move == null) return;
        long durationNanos = (long)move.durationTicks * 50_000_000L;
        double raw = Math.min(1.0, Math.max(0.0, (System.nanoTime() - move.startedNanos) / (double)durationNanos));
        double eased = raw * raw * (3.0 - 2.0 * raw);
        double row, col;
        if (move.knight && Math.abs(move.toRow - move.fromRow) > 0 && Math.abs(move.toCol - move.fromCol) > 0) {
            boolean longOnCol = Math.abs(move.toCol - move.fromCol) > Math.abs(move.toRow - move.fromRow);
            double longProgress = Math.min(1.0, raw / 0.64);
            longProgress = longProgress * longProgress * (3.0 - 2.0 * longProgress);
            double shortProgress = Math.max(0.0, (raw - 0.64) / 0.36);
            shortProgress = shortProgress * shortProgress * (3.0 - 2.0 * shortProgress);
            col = move.fromCol + (move.toCol - move.fromCol) * (longOnCol ? longProgress : shortProgress);
            row = move.fromRow + (move.toRow - move.fromRow) * (longOnCol ? shortProgress : longProgress);
        } else {
            row = move.fromRow + (move.toRow - move.fromRow) * eased;
            col = move.fromCol + (move.toCol - move.fromCol) * eased;
        }
        int size = Math.min(22, cell - 4);
        int x = (int)Math.round(boardX + (col + 0.5) * cell - size / 2.0);
        int y = (int)Math.round(boardY + (row + 0.5) * cell - size / 2.0);
        context.drawTexture(new net.minecraft.util.Identifier("chess", "textures/item/" + iconKey(move.piece) + ".png"),
            x, y, size, size, 0, 0, 32, 32, 32, 32);
    }

    private String iconKey(char piece) {
        boolean white = Character.isUpperCase(piece);
        String model;
        switch (Character.toUpperCase(piece)) {
            case 'K': model = "king"; break;
            case 'Q': model = "ferz"; break;
            case 'R': model = "ladya"; break;
            case 'B': model = "el"; break;
            case 'N': model = "horse"; break;
            case 'P': model = "peshka"; break;
            default: model = "peshka";
        }
        return "chess_" + (white ? "white_" : "black_") + model;
    }

    private boolean matchesHeldPiece(char piece) {
        String path = heldItemPath.toLowerCase();
        char symbol = path.contains("king") ? 'K' : path.contains("ferz") ? 'Q' : path.contains("ladya") ? 'R'
            : path.contains("_el") ? 'B' : path.contains("horse") ? 'N' : path.contains("peshka") ? 'P' : '?';
        if (symbol == '?' || Character.toUpperCase(piece) != symbol) return false;
        boolean heldWhite = path.contains("white");
        return Character.isUpperCase(piece) == heldWhite;
    }

    private void autoSelectHeldPiece() {
        if (selectionInitialized) return;
        selectionInitialized = true;
        String path = heldItemPath.toLowerCase();
        char desired = path.contains("king") ? 'K' : path.contains("ferz") ? 'Q'
            : path.contains("ladya") ? 'R' : path.contains("_el") ? 'B'
            : path.contains("horse") ? 'N' : path.contains("peshka") ? 'P' : '?';
        if (desired == '?') return;
        boolean wantedWhite = path.contains("white");
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        double px = client.player == null ? origin.getX() + 4 : client.player.getX();
        double pz = client.player == null ? origin.getZ() + 4 : client.player.getZ();
        double nearest = Double.MAX_VALUE;
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            char piece = cellAt(row, col);
            if (piece == '.' || Character.toUpperCase(piece) != desired || Character.isUpperCase(piece) != wantedWhite) continue;
            double dx = origin.getX() + col + 0.5 - px;
            double dz = origin.getZ() + row + 0.5 - pz;
            double distance = dx * dx + dz * dz;
            if (distance < nearest) { nearest = distance; fromRow = row; fromCol = col; }
        }
    }

    private String ruleLabel(String mode) {
        if ("FULL_REALISM".equals(mode)) return "Полный реализм";
        if ("NO_REALISM".equals(mode)) return "Свободный режим";
        return "Реализм";
    }

    @Override
    public void close() {
        if (activeScreen == this) activeScreen = null;
        super.close();
    }

    @Override
    public boolean shouldPause() { return false; }
}
