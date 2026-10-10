package com.chess;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

/** Chess symbols and their world block representation. */
public enum ChessPieceType {
    WHITE_KING('K', true, "king", "Король"),
    WHITE_QUEEN('Q', true, "ferz", "Ферзь"),
    WHITE_ROOK('R', true, "ladya", "Ладья"),
    WHITE_BISHOP('B', true, "el", "Слон"),
    WHITE_KNIGHT('N', true, "horse", "Конь"),
    WHITE_PAWN('P', true, "peshka", "Пешка"),
    BLACK_KING('k', false, "king", "Король"),
    BLACK_QUEEN('q', false, "ferz", "Ферзь"),
    BLACK_ROOK('r', false, "ladya", "Ладья"),
    BLACK_BISHOP('b', false, "el", "Слон"),
    BLACK_KNIGHT('n', false, "horse", "Конь"),
    BLACK_PAWN('p', false, "peshka", "Пешка");

    private final char symbol;
    private final boolean white;
    private final String model;
    private final String russianName;

    ChessPieceType(char symbol, boolean white, String model, String russianName) {
        this.symbol = symbol;
        this.white = white;
        this.model = model;
        this.russianName = russianName;
    }

    public char symbol() { return symbol; }
    public boolean isWhite() { return white; }
    public String model() { return model; }
    public String russianName() { return russianName; }
    public boolean sameSide(ChessPieceType other) { return other != null && white == other.white; }

    public Block block() {
        switch (this) {
            case WHITE_KING: return ModBlocks.CHESS_WHITE_KING;
            case WHITE_QUEEN: return ModBlocks.CHESS_WHITE_FERZ;
            case WHITE_ROOK: return ModBlocks.CHESS_WHITE_LADYA;
            case WHITE_BISHOP: return ModBlocks.CHESS_WHITE_EL;
            case WHITE_KNIGHT: return ModBlocks.CHESS_WHITE_HORSE;
            case WHITE_PAWN: return ModBlocks.CHESS_WHITE_PESHKA;
            case BLACK_KING: return ModBlocks.CHESS_BLACK_KING;
            case BLACK_QUEEN: return ModBlocks.CHESS_BLACK_FERZ;
            case BLACK_ROOK: return ModBlocks.CHESS_BLACK_LADYA;
            case BLACK_BISHOP: return ModBlocks.CHESS_BLACK_EL;
            case BLACK_KNIGHT: return ModBlocks.CHESS_BLACK_HORSE;
            case BLACK_PAWN: return ModBlocks.CHESS_BLACK_PESHKA;
            default: throw new IllegalStateException("Unknown chess piece " + this);
        }
    }

    public static ChessPieceType fromSymbol(char symbol) {
        for (ChessPieceType type : values()) if (type.symbol == symbol) return type;
        return null;
    }

    public static ChessPieceType fromBlock(BlockState state) {
        if (state == null) return null;
        Block block = state.getBlock();
        for (ChessPieceType type : values()) if (type.block() == block) return type;
        return null;
    }

    public static String itemPath(boolean white, String model) {
        return "chess_" + (white ? "white_" : "black_") + model;
    }
}
