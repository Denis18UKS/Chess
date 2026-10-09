package com.chess;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItems {
    public static final Item CHESS_BOARD_TOOL = register("chess_board_tool", new ChessBoardToolItem(new Item.Settings().maxCount(1)));
    public static final Item CHESS_TELEPORT_TOOL = register("chess_teleport_tool", new ChessTeleportToolItem(new Item.Settings().maxCount(1)));
    public static final Item CHESS_GRAFFITI_TOOL = register("chess_graffiti_tool", new ChessGraffitiToolItem(new Item.Settings().maxCount(1)));
    public static final Item CHESS_ASSET_STUDIO_TOOL = register("chess_asset_studio_tool", new ChessAssetStudioItem(new Item.Settings().maxCount(1)));

    public static final ItemGroup CHESS_GROUP = Registry.register(
        Registries.ITEM_GROUP,
        ChessMod.id("chess"),
        FabricItemGroup.builder()
            .displayName(Text.translatable("itemGroup.chess"))
            .icon(() -> new ItemStack(ModBlocks.CHESS_WHITE_KING))
            .entries((context, entries) -> {
                entries.add(ModBlocks.CHESS_WHITE_SQUARE);
                entries.add(ModBlocks.CHESS_BLACK_SQUARE);
                for (Block block : ModBlocks.figureBlocks()) entries.add(block);
                entries.add(CHESS_BOARD_TOOL);
                entries.add(CHESS_TELEPORT_TOOL);
                entries.add(CHESS_GRAFFITI_TOOL);
                entries.add(CHESS_ASSET_STUDIO_TOOL);
            }).build()
    );

    private ModItems() {}

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, ChessMod.id(name), item);
    }

    public static void initialize() {
        ChessMod.LOGGER.info("Зарегистрирована вкладка Chess и инструменты конфигурации.");
    }
}
