package com.chess.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Imports model geo JSON and texture PNGs from a local file picker in-game. */
public class ChessAssetStudioScreen extends Screen {
    private static final String[] PIECES = {"king", "ferz", "ladya", "el", "horse", "peshka"};
    private final Screen parent;
    private int pieceIndex;
    private boolean white = true;
    private boolean modelsTab;
    private String status = "Выбери фигуру и импортируй PNG или GEO JSON.";

    public ChessAssetStudioScreen(Screen parent) {
        super(Text.literal("Chess — студия моделей и текстур"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int mid = width / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Текстуры"), b -> {
            modelsTab = false;
            clearAndInit();
        }).dimensions(mid - 105, 38, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Модели"), b -> {
            modelsTab = true;
            clearAndInit();
        }).dimensions(mid + 5, 38, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> {
            pieceIndex = (pieceIndex + PIECES.length - 1) % PIECES.length;
            clearAndInit();
        }).dimensions(mid - 112, 82, 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> {
            pieceIndex = (pieceIndex + 1) % PIECES.length;
            clearAndInit();
        }).dimensions(mid + 88, 82, 24, 20).build());

        if (!modelsTab) {
            addDrawableChild(ButtonWidget.builder(Text.literal(white ? "Сторона: белые" : "Сторона: чёрные"), b -> {
                white = !white;
                clearAndInit();
            }).dimensions(mid - 75, 112, 150, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Выбрать PNG и применить в игре"), b -> importTexture())
                .dimensions(mid - 125, 147, 250, 24).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("Загрузить GEO JSON модели"), b -> importModel())
                .dimensions(mid - 125, 147, 250, 24).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), b -> close())
            .dimensions(mid - 50, height - 34, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(modelsTab ? "ВКЛАДКА: МОДЕЛИ" : "ВКЛАДКА: ТЕКСТУРЫ"), width / 2, 26, 0xFFDDC28A);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Фигура: " + russian(PIECES[pieceIndex])), width / 2, 69, 0xFFFFFFFF);

        if (modelsTab) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Заменяет 3D-геометрию фигуры для обеих сторон."), width / 2, 132, 0xFFCCCCCC);
        } else {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Сторона: " + (white ? "белые" : "чёрные")), width / 2, 136, 0xFFCCCCCC);
        }
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height - 58, 0xFFFFE3A3);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Файлы сохраняются в resourcepacks/ChessCustom и включаются автоматически."), width / 2, height - 47, 0xFFAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    private void importTexture() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выбери PNG текстуру для " + russian(PIECES[pieceIndex]));
        chooser.setFileFilter(new FileNameExtensionFilter("PNG texture (*.png)", "png"));
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath();
        try {
            BufferedImage image = ImageIO.read(source.toFile());
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1 || image.getWidth() > 4096 || image.getHeight() > 4096) {
                status = "Ошибка: нужен корректный PNG не больше 4096×4096.";
                return;
            }
            String side = white ? "white" : "black";
            String piece = PIECES[pieceIndex];
            Path target = packRoot().resolve("assets/chess/textures/generated/figures/" + side + "_" + piece + ".png");
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            activatePack();
            status = "Текстура импортирована: " + target.getFileName();
        } catch (Exception ex) {
            status = "Не удалось импортировать PNG: " + shortError(ex);
        }
    }

    private void importModel() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выбери GEO JSON модель " + russian(PIECES[pieceIndex]));
        chooser.setFileFilter(new FileNameExtensionFilter("Minecraft / GeckoLib geo JSON (*.json)", "json"));
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath();
        try {
            String json = Files.readString(source);
            if (!json.contains("minecraft:geometry") || !json.contains("\"bones\"")) {
                status = "Ошибка: нужен GEO JSON с minecraft:geometry и bones.";
                return;
            }
            String piece = PIECES[pieceIndex];
            Path target = packRoot().resolve("assets/chess/geo/models/figures/" + piece + ".geo.json");
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            activatePack();
            status = "3D-модель импортирована: " + target.getFileName();
        } catch (Exception ex) {
            status = "Не удалось импортировать GEO JSON: " + shortError(ex);
        }
    }

    private Path packRoot() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("resourcepacks/ChessCustom");
    }

    private void activatePack() throws IOException {
        Path root = packRoot();
        Files.createDirectories(root);
        Files.writeString(root.resolve("pack.mcmeta"),
            "{\"pack\":{\"pack_format\":15,\"description\":\"Chess Custom Textures & Models\"}}");
        MinecraftClient client = MinecraftClient.getInstance();
        String profile = "file/ChessCustom";
        client.getResourcePackManager().scanPacks();
        client.options.resourcePacks.remove(profile);
        client.options.resourcePacks.add(0, profile);
        client.getResourcePackManager().setEnabledProfiles(client.options.resourcePacks);
        client.options.write();
        client.reloadResources();
    }

    private String shortError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
        return message.length() > 90 ? message.substring(0, 90) : message;
    }

    private String russian(String piece) {
        switch (piece) {
            case "king": return "король";
            case "ferz": return "ферзь";
            case "ladya": return "ладья";
            case "el": return "слон";
            case "horse": return "конь";
            default: return "пешка";
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
