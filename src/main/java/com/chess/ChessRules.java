package com.chess;

/**
 * Rules engine for the 8x8 logical board. Uppercase symbols are white,
 * lowercase symbols are black, and '.' represents an empty square.
 */
public final class ChessRules {
    private ChessRules() {}

    public static boolean isLegalMove(char[][] board, int fr, int fc, int tr, int tc, boolean fullRules,
                                      int enPassantRow, int enPassantCol,
                                      boolean kingMoved, boolean leftRookMoved, boolean rightRookMoved) {
        if (!inside(fr, fc) || !inside(tr, tc) || (fr == tr && fc == tc)) return false;
        char moving = board[fr][fc], target = board[tr][tc];
        if (moving == '.' || (target != '.' && isWhite(moving) == isWhite(target))) return false;
        if (!pseudoLegal(board, fr, fc, tr, tc, enPassantRow, enPassantCol, kingMoved, leftRookMoved, rightRookMoved)) return false;
        if (!fullRules) return true;

        char[][] copy = copy(board);
        copy[tr][tc] = moving;
        copy[fr][fc] = '.';
        if (Character.toUpperCase(moving) == 'P' && fc != tc && target == '.') copy[fr][tc] = '.';
        if (Character.toUpperCase(moving) == 'K' && Math.abs(tc - fc) == 2) {
            int rookFrom = tc > fc ? 7 : 0;
            int rookTo = tc > fc ? 5 : 3;
            copy[tr][rookTo] = copy[tr][rookFrom];
            copy[tr][rookFrom] = '.';
        }
        return !isInCheck(copy, isWhite(moving));
    }

    private static boolean pseudoLegal(char[][] b, int fr, int fc, int tr, int tc,
                                       int epRow, int epCol, boolean kingMoved,
                                       boolean leftRookMoved, boolean rightRookMoved) {
        char p = b[fr][fc];
        int dr = tr - fr, dc = tc - fc;
        switch (Character.toUpperCase(p)) {
            case 'P': {
                int direction = isWhite(p) ? -1 : 1;
                int start = isWhite(p) ? 6 : 1;
                if (dc == 0 && b[tr][tc] == '.') {
                    if (dr == direction) return true;
                    return fr == start && dr == 2 * direction && b[fr + direction][fc] == '.';
                }
                if (Math.abs(dc) == 1 && dr == direction) {
                    if (b[tr][tc] != '.') return isWhite(p) != isWhite(b[tr][tc]);
                    return tr == epRow && tc == epCol && fr == (isWhite(p) ? 3 : 4);
                }
                return false;
            }
            case 'N': return (Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2);
            case 'K':
                if (Math.max(Math.abs(dr), Math.abs(dc)) == 1) return true;
                if (dr != 0 || Math.abs(dc) != 2 || fc != 4 || kingMoved || isInCheck(b, isWhite(p))) return false;
                int row = fr;
                boolean shortCastle = dc > 0;
                if (shortCastle) {
                    if (rightRookMoved || Character.toUpperCase(b[row][7]) != 'R' || isWhite(b[row][7]) != isWhite(p)) return false;
                    if (b[row][5] != '.' || b[row][6] != '.') return false;
                    return !isAttacked(b, row, 5, !isWhite(p)) && !isAttacked(b, row, 6, !isWhite(p));
                } else {
                    if (leftRookMoved || Character.toUpperCase(b[row][0]) != 'R' || isWhite(b[row][0]) != isWhite(p)) return false;
                    if (b[row][1] != '.' || b[row][2] != '.' || b[row][3] != '.') return false;
                    return !isAttacked(b, row, 3, !isWhite(p)) && !isAttacked(b, row, 2, !isWhite(p));
                }
            case 'R': return (dr == 0 || dc == 0) && pathClear(b, fr, fc, tr, tc);
            case 'B': return Math.abs(dr) == Math.abs(dc) && pathClear(b, fr, fc, tr, tc);
            case 'Q': return (dr == 0 || dc == 0 || Math.abs(dr) == Math.abs(dc)) && pathClear(b, fr, fc, tr, tc);
            default: return false;
        }
    }

    public static boolean hasAnyLegalMove(char[][] board, boolean white, boolean fullRules,
                                          int epRow, int epCol, boolean kingMoved,
                                          boolean leftRookMoved, boolean rightRookMoved) {
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            char p = board[r][c];
            if (p == '.' || isWhite(p) != white) continue;
            for (int tr = 0; tr < 8; tr++) for (int tc = 0; tc < 8; tc++)
                if (isLegalMove(board, r, c, tr, tc, fullRules, epRow, epCol, kingMoved, leftRookMoved, rightRookMoved)) return true;
        }
        return false;
    }

    public static boolean isInCheck(char[][] board, boolean white) {
        char king = white ? 'K' : 'k';
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++)
            if (board[r][c] == king) return isAttacked(board, r, c, !white);
        return true; // a missing king is not a valid full-realism position
    }

    public static boolean isAttacked(char[][] b, int r, int c, boolean byWhite) {
        for (int sr = 0; sr < 8; sr++) for (int sc = 0; sc < 8; sc++) {
            char p = b[sr][sc];
            if (p == '.' || isWhite(p) != byWhite) continue;
            int dr = r - sr, dc = c - sc;
            switch (Character.toUpperCase(p)) {
                case 'P': if (dr == (byWhite ? -1 : 1) && Math.abs(dc) == 1) return true; break;
                case 'N': if ((Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2)) return true; break;
                case 'K': if (Math.max(Math.abs(dr), Math.abs(dc)) == 1) return true; break;
                case 'R': if ((dr == 0 || dc == 0) && pathClear(b, sr, sc, r, c)) return true; break;
                case 'B': if (Math.abs(dr) == Math.abs(dc) && pathClear(b, sr, sc, r, c)) return true; break;
                case 'Q': if ((dr == 0 || dc == 0 || Math.abs(dr) == Math.abs(dc)) && pathClear(b, sr, sc, r, c)) return true; break;
            }
        }
        return false;
    }

    public static char[][] copy(char[][] source) {
        char[][] result = new char[8][8];
        for (int r = 0; r < 8; r++) System.arraycopy(source[r], 0, result[r], 0, 8);
        return result;
    }

    public static boolean isWhite(char p) { return Character.isUpperCase(p); }
    private static boolean inside(int r, int c) { return r >= 0 && r < 8 && c >= 0 && c < 8; }

    private static boolean pathClear(char[][] b, int fr, int fc, int tr, int tc) {
        int dr = Integer.compare(tr, fr), dc = Integer.compare(tc, fc);
        int r = fr + dr, c = fc + dc;
        while (r != tr || c != tc) {
            if (b[r][c] != '.') return false;
            r += dr; c += dc;
        }
        return true;
    }
}
