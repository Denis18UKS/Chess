package com.chess.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Minecraft-native local file browser. Swing/AWT dialogs cannot be used safely
 * in all game launchers and are headless in some Modrinth environments.
 */
public final class ChessFilePickerScreen extends Screen {
    private static final int ROWS = 9;
    private final Screen parent;
    private final String titleText;
    private final String[] extensions;
    private final Consumer<Path> onChoose;
    private Path directory;
    private List<Path> entries = List.of();
    private int page;
    private String status = "Выберите файл или введите полный путь.";
    private TextFieldWidget pathField;

    public ChessFilePickerScreen(Screen parent, String titleText, String[] extensions, Consumer<Path> onChoose) {
        super(Text.literal("Chess — выбор файла"));
        this.parent = parent;
        this.titleText = titleText;
        this.extensions = extensions.clone();
        this.onChoose = onChoose;
        Path start = MinecraftClient.getInstance().runDirectory.toPath().toAbsolutePath().normalize();
        this.directory = Files.isDirectory(start) ? start : Path.of(System.getProperty("user.home", ".")).toAbsolutePath();
    }

    @Override
    protected void init() {
        refreshEntries();
        int widthField = Math.max(140, width - 152);
        pathField = new TextFieldWidget(textRenderer, 12, 33, widthField, 20, Text.literal("Путь"));
        pathField.setMaxLength(512);
        pathField.setText(directory.toString());
        pathField.setPlaceholder(Text.literal("Полный путь к файлу или каталогу"));
        addDrawableChild(pathField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Перейти"), b -> navigatePath())
            .dimensions(width - 132, 33, 120, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("↑ Вверх"), b -> goUp())
            .dimensions(12, 58, 90, 18).build());

        int start = page * ROWS;
        int end = Math.min(entries.size(), start + ROWS);
        for (int i = start; i < end; i++) {
            Path entry = entries.get(i);
            boolean isDirectory = Files.isDirectory(entry);
            String prefix = isDirectory ? "[ПАПКА] " : "[ФАЙЛ] ";
            String label = prefix + shortName(entry.getFileName() == null ? entry.toString() : entry.getFileName().toString(), 52);
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                if (Files.isDirectory(entry)) {
                    directory = entry.toAbsolutePath().normalize();
                    page = 0;
                    refreshEntries();
                    clearAndInit();
                } else {
                    choose(entry);
                }
            }).dimensions(12, 80 + (i - start) * 22, width - 24, 20).build());
        }

        int navY = height - 59;
        addDrawableChild(ButtonWidget.builder(Text.literal("‹ Назад"), b -> {
            page = Math.max(0, page - 1);
            clearAndInit();
        }).dimensions(width / 2 - 100, navY, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Вперёд ›"), b -> {
            if ((page + 1) * ROWS < entries.size()) page++;
            clearAndInit();
        }).dimensions(width / 2 + 5, navY, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), b -> close())
            .dimensions(width / 2 - 50, height - 32, 100, 20).build());
    }

    private void refreshEntries() {
        try {
            if (!Files.isDirectory(directory)) {
                entries = List.of();
                status = "Каталог не найден или недоступен.";
                return;
            }
            List<Path> found = new ArrayList<>();
            try (var stream = Files.list(directory)) {
                stream.filter(p -> Files.isDirectory(p) || isAllowedFile(p))
                    .forEach(found::add);
            }
            found.sort(Comparator.comparing((Path p) -> !Files.isDirectory(p))
                .thenComparing(p -> p.getFileName() == null ? p.toString() : p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
            entries = found;
            int maxPage = Math.max(0, (entries.size() - 1) / ROWS);
            page = Math.min(page, maxPage);
            status = entries.size() + " подходящих элементов в каталоге.";
        } catch (IOException | SecurityException ex) {
            entries = List.of();
            status = "Не удалось прочитать каталог: " + shortName(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), 60);
        }
    }

    private boolean isAllowedFile(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            if (name.endsWith("." + extension.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private void navigatePath() {
        String raw = pathField == null ? "" : pathField.getText().trim();
        if (raw.isEmpty()) return;
        try {
            Path target = Path.of(raw).toAbsolutePath().normalize();
            if (Files.isDirectory(target)) {
                directory = target;
                page = 0;
                refreshEntries();
                clearAndInit();
            } else if (Files.isRegularFile(target) && isAllowedFile(target)) {
                choose(target);
            } else {
                status = "Укажи существующий каталог или файл с нужным расширением.";
            }
        } catch (Exception ex) {
            status = "Некорректный путь: " + shortName(ex.getMessage() == null ? "ошибка" : ex.getMessage(), 60);
        }
    }

    private void goUp() {
        Path parentPath = directory.getParent();
        if (parentPath != null) {
            directory = parentPath;
            page = 0;
            refreshEntries();
            clearAndInit();
        }
    }

    private void choose(Path path) {
        try {
            onChoose.accept(path.toAbsolutePath().normalize());
        } finally {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }

    private String shortName(String name, int limit) {
        if (name == null) return "";
        return name.length() <= limit ? name : name.substring(0, Math.max(0, limit - 1)) + "…";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(titleText), width / 2, 10, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("Папка: " + shortName(directory.toString(), 100)), 12, 22, 0xFFBBBBBB);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height - 76, 0xFFFFE3A3);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
