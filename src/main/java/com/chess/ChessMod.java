package com.chess;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.GeckoLib;

public class ChessMod implements ModInitializer {
    public static final String MOD_ID = "chess";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        GeckoLib.initialize();
        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModItems.initialize();
        ChessNetwork.registerServer();
        ChessCommands.register();
        ChessGameManager.registerTicker();
        LOGGER.info("Chess initialized: 12 piece blocks, game state, commands and networking registered.");
    }

    public static Identifier id(String name) {
        return new Identifier(MOD_ID, name);
    }
}
