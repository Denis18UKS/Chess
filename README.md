# Chess — Fabric 1.20.1

A Minecraft Fabric chess mini-game mod for Java 17, built on Fabric API and GeckoLib 4.

## Status

This branch adds the foundation for a world-backed chess board, twelve figure block items, a live 2D board screen, GeckoLib movement animations, server-authoritative move validation, teams, commands, board/teleport tools and build-time generated inventory icons. It is under active development; see the limitations at the end before using it on a public server.

## Requirements

- Minecraft Java Edition 1.20.1
- Fabric Loader 0.15.0 or newer
- Fabric API for Minecraft 1.20.1
- GeckoLib 4.4.9 or newer compatible with Fabric 1.20.1

Install the built `chess-1.2.0-1.20.1.jar` into `.minecraft/mods`. Multiplayer servers and all clients need compatible versions of the mod.

## Match lifecycle and lobby (current branch)

### World control panel

Place the **wooden** multi-block Chess Settings Panel in the world. Its face uses a compact scoreboard layout with clean lettering on wood, without block-glyph bars or a translucent rectangle behind every label.

- Choose **1v1**, **2v2** or **С БОТОМ** on the panel.
- Start the match using **НАЧАТЬ ПАРТИЮ**. The roster is checked first; if a required side is empty, the mod reports **«Недостаточно игроков»**.
- Side selection is **not** part of the panel. Use **Настройщик триггерных областей** to configure white and black side zones; players choose a side by entering its area.
- A 3–2–1 countdown is shown before the Fade / teleport transition. Scoreboard membership is created only when the match starts.

Required human roster: 1 white + 1 black for 1v1; 2 white + 2 black for 2v2; 1 white player for vs_bot (the other side is controlled by the existing bot logic).

### Lobby and teleport points

Use **Настройщик точки лобби** to save the lobby spawn/return point. Use **Настройщик командных точек и наблюдения** for the separate **Белые**, **Чёрные** and **Наблюдение** destinations. The block you target is treated as the floor; the direction you are looking is saved as the arrival direction. Set each destination independently. The mod searches for a safe standing position near the configured block and avoids assigning the same block position to multiple players in one teleport group.

Players who are not participating in an active match are sent to the lobby on join. At match end, participants and current spectators are returned to the lobby, temporary side selections/team membership are cleared, and the chessboard is reset.

### Spectators

Players already online in the lobby when a match starts and who are not in its roster are automatically forced to spectate. Participants are placed in Adventure mode for the active match; observers remain in Spectator mode. Players who join while a match is already running remain in the lobby until they opt in. Press **O** (rebindable in Minecraft Controls) or use `/chess spectate` to toggle viewing. Turn state is shown in the action bar (configure it with `/chess turnhud permanent|temporary|off`); turn announcements are not repeated in chat. Spectators are never members of either chess team.

### Fade and team zones

- Use the **Fade & Teleport Configurator** item to set the fade duration and delay until teleportation. The delay is clamped to at least the fade-out duration so a player is not moved before the screen has gone black.
- Use **Настройщик триггерных областей** or WorldEdit-style commands `/chess zone pos1 <x> <y> <z>` and `/chess zone pos2 <x> <y> <z> <имя>`. Toggle the zone boundary display with `/chess zones show|hide|toggle`. It also has dedicated A/B controls for the white and black side-selection areas. Entering a side zone preselects that side outside a match; it does not add a scoreboard member or start a game by itself.
- Scoreboard teams keep their internal IDs `white` / `black`, but their displayed names are localized as colored **Белые** / **Чёрные**.

## Set up an arena

1. Build an 8×8 flat board using the Chess white/black square blocks. Put the first (north-west) square at the corner you intend to use as the board origin.
2. Hold **Chess Board Configurator** and right-click a support block to generate the 8×8 board one block above it. Right-click an existing Chess square to bind that square as the origin; sneak-right-click an existing Chess square to regenerate the pattern. The board extends along positive X and positive Z; figures occupy the block directly above each square.
3. Place the figure blocks on the upper layer. There are separate white and black King, Queen (Ferz), Rook (Ladya), Bishop (El), Knight (Horse) and Pawn items.
4. Use `/chessboard check` to count board tiles and figures. It checks for 64 registered Chess square blocks, not whether every square is physically arranged in the correct orientation.
5. Use `/chess autoconfig` once to initialize the chess teams and side labels. Then choose the match mode, preselect each player’s side, and start the match as described below.

## Commands

| Command | Purpose |
| --- | --- |
| `/chess start` | Start a match, with white to move first |
| `/chess stop` | Stop the match; starts the configured fade/lobby cleanup |
| `/chess pause` | Pause/resume a running match |
| `/chess termination` | End the current match |
| `/chess realism` | Validate normal piece movement and alternate turns without requiring the held figure |
| `/chess no_realism` | Free movement mode without strict movement/turn enforcement |
| `/chess full_realism` | Enforce piece movement, king safety, check/checkmate/stalemate, castling, en passant and pawn promotion |
| `/chess one_one` | Set 1v1 (default) |
| `/chess two_two` | Set 2v2 |\n| `/chess unselect` | Clear the player’s preselected side before the next match |\n| `/chess spectate` | Toggle watching an active match; press O again or run the command to return to the lobby |
| `/chess white` / `/chess black` | Preselect the white/black side for the next match; does not add the player to a scoreboard team yet |
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
- **Настройщик точки лобби**: opens a separate screen to save the lobby return/spawn point on the targeted block.
- **Настройщик командных точек и наблюдения**: opens the tabbed editor for white, black and spectator destinations.
- **Настройщик триггерных областей**: defines custom trigger volumes and team-side selection zones.
- **Graffiti Tool**: rename it in an anvil to define a label, then right-click; sneak-right-click cycles the stored scale from 0.5× to 3×.

The graffiti tool creates a persistent vanilla Text Display entity with white text, fixed orientation and a horizontal transform. The entity is synced and saved by Minecraft; sneak-right-click cycles scale before placement.

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

- The logical board origin is currently stored in memory per dimension; unlike lobby/teleport/Fade configuration, it is not persisted across server restarts.
- The world-backed logical board is read from the blocks in a fixed 8×8 region. The configurator can generate the tile pattern, but does not automatically arrange the figures.
- Full-realism contains the core move/check rules listed above but does not yet implement all formal tournament draw rules, threefold repetition, the 50-move rule, chess clocks or draw offers.
- The GeckoLib movement animation packet starts from the source figure; block relocation is delayed for an animation window. Fine adjustment of animation duration/axis against the supplied models still needs in-game verification.
- The GUI displays 2D piece icons generated for the inventory sprites; the world figures remain GeckoLib 3D block entities.
- The current generated textures for pieces without original PNGs are simple UV atlases. Replace them with polished model-matched textures when those source PNGs are available.

## License

The repository's original license is CC0-1.0.


## Human vs bot

In `vs_bot` mode, choose either side with `/chess white` or `/chess black` before starting. Exactly one human must select a side; the bot automatically takes the opposite color. The choice can be changed before the match by running the other side-selection command. The bot evaluates its move plus the strongest legal reply, including material balance, exposed pieces, checks, centre control and pawn advancement, rather than preferring every capture by default. White knights are normalized to face the black side; black knights face white.


## Настройка шахматного бота

Настройки сохраняются в конфигурации мира:
- `/chess bot difficulty easy|medium|high|professional` — лёгкий, средний, высокий и профессиональный уровни. Средний анализирует ответ соперника; высокий рассматривает дополнительный ответный ход; профессиональный использует более глубокий поиск с ограниченной шириной для снижения нагрузки сервера.
- `/chess bot style balanced|aggressive|defensive|positional` — сбалансированный, агрессивный, защитный или позиционный стиль.
- `/chess bot status` — посмотреть текущие настройки.

## DEV-инструменты проверки бота и превращения

Включи `/chessdev`, затем:
- `/chessdev botpieces pawn` — заставить бота ходить только пешками; поддерживаются `pawn`, `rook`, `knight`, `bishop`, `queen`, `king` и списки через запятую, например `pawn,knight`. `all` возвращает весь набор, `none` временно запрещает любые ходы бота. Это фильтр тестирования, не правило шахмат; если у выбранных фигур нет легального хода, матч не завершается автоматически.
- `/chessdev promotionstock white queen` — добавить тестового белого ферзя именно в белый лоток (и `/chessdev promotionstock black queen` — чёрного ферзя в чёрный). Тестовый запас позволяет проверить обмен без предварительного взятия.
- Превращение разрешено только обменом строго одинаковых фигур одного цвета: белая пешка меняется с белым ферзём/ладьёй/слоном/конём из белого лотка, чёрная — с чёрной фигурой из чёрного лотка. Например, белая пешка сможет стать ферзём только если снятый белый ферзь лежит в белом лотке. Если снятого ферзя нет, второй белый ферзь не создаётся; этот вариант не показывается.


## Сохранение поля и переключение вида партии

- При первой привязке координаты поля и измерение сохраняются в PersistentState мира; после перезапуска привязка автоматически восстанавливается.
- При обновлении старого мира мод ищет существующую 8×8 шахматную раскладку рядом со спавном и игроками. Если поиск с первого раза не нашёл её, подойди к полю и запусти `/chessboard auto`; конфигуратор нужен только если автоматическое обнаружение не может определить раскладку.
- В деревянную панель добавлен пункт **«ВИД ПАРТИИ» — 2D / 3D**. Выбор сохраняется в мире.
- Отсчёт `3, 2, 1` перед стартом отображается только в title; строка отсчёта убрана из чата.
- Fade теперь рисуется через конец HUD-рендера (включая путь скрытого HUD/F1), конец игрового кадра как резервный путь и конец отрисовки GUI-экрана/чата. Это обеспечивает верхний слой независимо от наличия HUD и открытого экрана.
- Каждый ход бота повторно проверяется правилами шахмат перед созданием анимации. Недопустимый ход отклоняется на сервере до взятия/перемещения; первые два хода также получают штраф за повторное развитие коней, чтобы бот не начинал партии одним и тем же шаблоном.


## Поле, вид партии, Fade и дополнительные превращения

- Привязка поля теперь хранится в `chess_world_config` (измерение + координаты первой клетки). При остановке сервера очищается только оперативный кэш состояния; после следующего запуска поле поднимается из PersistentState. Для миров, созданных старой версией до сохранения привязки, есть автообнаружение по рисунку всех 64 клеток и команда `/chessboard auto`.
- При обнаружении поле привязывается к северо-западной клетке, а не к произвольному сдвинутому окну 8×8 внутри той же шахматной раскладки.
- Панель партии включает настройку **«ВИД ПАРТИИ» — 2D / 3D**; выбор сохраняется в настройках мира и восстанавливается при перезапуске.
- Отсчёт `3, 2, 1` при запуске показывается только титрами; сообщение `Подготовка партии: 3… 2… 1…` не дублируется в чате.
- Fade использует общий HUD callback, возврат из InGameHud, резервный рендер в конце GameRenderer (для F1) и возврат из Screen (для чата/экранов). Слои используют один и тот же альфа-рендер и не должны зависеть от скрытого HUD.
- Бот ходит только легальными ходами из текущей позиции. Сервер перепроверяет исходную и целевую клетки перед началом и завершением анимации; устаревший или невозможный захват отклоняется до изменения доски. Первые ходы получили сильный штраф за повторное развитие коней, поэтому дебют должен быть разнообразнее.
- Количество фигур каждого типа не увеличивается при превращении: фигура из лотка возвращается на доску, а пешка занимает её место в лотке. Список лотка определяется цветом снятой фигуры, а не цветом игрока, который её взял.
