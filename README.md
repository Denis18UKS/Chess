# Chess — Fabric 1.20.1

A Minecraft Fabric chess mini-game mod for Java 17, built on Fabric API and GeckoLib 4.

## Status

This branch adds the foundation for a world-backed chess board, twelve figure block items, a live 2D board screen, GeckoLib movement animations, server-authoritative move validation, teams, commands, board/teleport tools and build-time generated inventory icons. It is under active development; see the limitations at the end before using it on a public server.

## Requirements

- Minecraft Java Edition 1.20.1
- Fabric Loader 0.15.0 or newer
- Fabric API for Minecraft 1.20.1
- GeckoLib 4.4.9 or newer compatible with Fabric 1.20.1

Install the built `chess-1.1.0-1.20.1.jar` into `.minecraft/mods`. Multiplayer users need compatible client/server installations.

## Set up an arena

1. Build an 8×8 flat board using the Chess white/black square blocks. Put the first (north-west) square at the corner you intend to use as the board origin.
2. Hold **Chess Board Configurator** and right-click that first square. The board extends along positive X and positive Z; figure blocks occupy the block directly above each square.
3. Place the figure blocks on the upper layer. There are separate white and black King, Queen (Ferz), Rook (Ladya), Bishop (El), Knight (Horse) and Pawn items.
4. Use `/chessboard check` to count board tiles and figures. It checks for 64 registered Chess square blocks, not whether every square is physically arranged in the correct orientation.
5. Use `/chess autoconfig` once to create the scoreboard teams, join a side, then start the match.

## Commands

| Command | Purpose |
| --- | --- |
| `/chess start` | Start a match, with white to move first |
| `/chess stop` | Stop the match |
| `/chess pause` | Pause/resume a running match |
| `/chess termination` | End the current match |
| `/chess realism` | Validate normal piece movement and alternate turns without requiring the held figure |
| `/chess no_realism` | Free movement mode without strict movement/turn enforcement |
| `/chess full_realism` | Enforce piece movement, king safety, check/checkmate/stalemate, castling, en passant and pawn promotion |
| `/chess one_one` | Set 1v1 (default) |
| `/chess two_two` | Set 2v2 |
| `/chess white` / `/chess black` | Join the white/black scoreboard team |
| `/chess turn` | Transfer the turn to the opposite side |
| `/chess board set <x> <y> <z>` | Set the board origin by command |
| `/chess tp set white <x> <y> <z>` | Save white teleport destination |
| `/chess tp set black <x> <y> <z>` | Save black teleport destination |
| `/chess tp go white` / `/chess tp go black` | Teleport to a saved destination |
| `/chessboard check` | Validate and report the field state |

Use **/chess autoconfig** before **/chess white** or **/chess black**. In 1v1 only one player is admitted to each team; in 2v2 two players are admitted.

## Controls

Hold any Chess figure item and right-click to open the 2D board. Select the figure square, then its destination, and press **Подтвердить**. The screen refreshes its representation from the actual blocks in the world. Sneak-right-click places a held figure block rather than opening the screen.

The piece sprites displayed in inventory are generated as separate 32×32 2D PNG icons during Gradle resource processing. Missing figure texture variants are also generated at build time; original textures in the repository are preserved.

## Developer tools

- **Chess Board Configurator**: anchors the logical 8×8 board when used on the first tile.
- **TP Configurator**: ordinary right-click stores the caller's team destination; sneak-right-click teleports to the saved team destination.
- **Graffiti Tool**: rename it in an anvil to define a label, then right-click; sneak-right-click cycles the stored scale from 0.5× to 3×.

The current graffiti tool records text/position/scale data on the tool. A persistent world-surface hologram renderer/editor is not yet implemented in this branch.

## Build from source

Use a JDK that can run the configured Gradle/Loom version (the compile target remains Java 17):

```sh
./gradlew clean build
```

On Windows:

```bat
gradlew.bat clean build
```

The build generates the inventory icon PNGs, then creates the remapped mod JAR under `build/libs/`. GitHub Actions performs the same build and uploads the JAR as a workflow artifact.

## Current limits

- Board origin and match settings are stored in memory per dimension, not saved across server restarts.
- The world-backed logical board is read from the blocks in a fixed 8×8 region; the board configurator does not automatically construct the tiles for you.
- Full-realism contains the core move/check rules listed above but does not yet implement all formal tournament draw rules, threefold repetition, the 50-move rule, chess clocks or draw offers.
- The GeckoLib movement animation packet starts from the source figure; block relocation is delayed for an animation window. Fine adjustment of animation duration/axis against the supplied models still needs in-game verification.
- The GUI currently uses recognizable chess-letter glyphs to display board occupancy; inventory slots use the generated 2D figure icons.
- The current generated textures for pieces without original PNGs are simple UV atlases. Replace them with polished model-matched textures when those source PNGs are available.

## License

The repository's original license is CC0-1.0.
