package com.chess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/** World-persistent configuration for lobby/teleport points, bound blocks and trigger zones. */
public final class ChessWorldConfig extends PersistentState {
    private static final String STORAGE_ID = "chess_world_config";
    private final Map<String, SavedPos> locations = new HashMap<>();
    private final Map<String, String> blockCommands = new HashMap<>();
    private final Map<String, TriggerZone> zones = new HashMap<>();
    private final Set<UUID> seenPlayers = new HashSet<>();
    private String zoneCornerDimension = "";
    private BlockPos zoneCorner = BlockPos.ORIGIN;
    private int lobbyDelaySeconds = 20;
    private int matchDurationMinutes = 0;
    private ChessGameManager.MatchMode matchMode = ChessGameManager.MatchMode.ONE_ONE;
    private ChessGameManager.RuleMode ruleMode = ChessGameManager.RuleMode.REALISM;

    public static ChessWorldConfig get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
            ChessWorldConfig::fromNbt, ChessWorldConfig::new, STORAGE_ID);
    }

    public SavedPos getLocation(String name) { return locations.get(name); }

    public void setLocation(String name, ServerWorld world, BlockPos pos) {
        locations.put(name, new SavedPos(world.getRegistryKey().getValue().toString(), pos.toImmutable()));
        markDirty();
    }

    public int getLobbyDelaySeconds() { return lobbyDelaySeconds; }

    public void setLobbyDelaySeconds(int seconds) {
        lobbyDelaySeconds = Math.max(0, Math.min(3600, seconds));
        markDirty();
    }

    public int getMatchDurationMinutes() { return matchDurationMinutes; }

    public void setMatchDurationMinutes(int minutes) {
        matchDurationMinutes = Math.max(0, Math.min(1440, minutes));
        markDirty();
    }

    public ChessGameManager.MatchMode getMatchMode() { return matchMode; }

    public void setMatchMode(ChessGameManager.MatchMode mode) {
        matchMode = mode == null ? ChessGameManager.MatchMode.ONE_ONE : mode;
        markDirty();
    }

    public ChessGameManager.RuleMode getRuleMode() { return ruleMode; }

    public void setRuleMode(ChessGameManager.RuleMode mode) {
        ruleMode = mode == null ? ChessGameManager.RuleMode.REALISM : mode;
        markDirty();
    }

    public void rememberPlayer(UUID uuid) {
        if (seenPlayers.add(uuid)) markDirty();
    }

    public boolean hasSeenPlayer(UUID uuid) { return seenPlayers.contains(uuid); }

    public void bindBlock(ServerWorld world, BlockPos pos, String command) {
        String key = blockKey(world, pos);
        String clean = command == null ? "" : command.trim();
        if (clean.startsWith("/")) clean = clean.substring(1);
        if (clean.isBlank()) blockCommands.remove(key);
        else blockCommands.put(key, clean);
        markDirty();
    }

    public String getBoundCommand(ServerWorld world, BlockPos pos) {
        return blockCommands.get(blockKey(world, pos));
    }

    private static String blockKey(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey().getValue() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public void saveZoneCorner(ServerWorld world, BlockPos pos) {
        zoneCornerDimension = world.getRegistryKey().getValue().toString();
        zoneCorner = pos.toImmutable();
        markDirty();
    }

    public boolean hasZoneCorner() { return !zoneCornerDimension.isBlank(); }

    public void defineZone(String name, ServerWorld world, BlockPos otherCorner, String enter, String leave) {
        if (!hasZoneCorner()) throw new IllegalStateException("Сначала назначьте угол A зоны.");
        if (!zoneCornerDimension.equals(world.getRegistryKey().getValue().toString()))
            throw new IllegalStateException("Оба угла триггер-зоны должны быть в одном измерении.");
        String id = name == null || name.isBlank() ? "zone_" + zones.size() : name.trim();
        zones.put(id, new TriggerZone(id, zoneCornerDimension, zoneCorner, otherCorner.toImmutable(),
            cleanCommand(enter), cleanCommand(leave)));
        zoneCornerDimension = "";
        zoneCorner = BlockPos.ORIGIN;
        markDirty();
    }

    public List<TriggerZone> getZones() { return new ArrayList<>(zones.values()); }

    public static String cleanCommand(String command) {
        String value = command == null ? "" : command.trim();
        return value.startsWith("/") ? value.substring(1) : value;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList locationList = new NbtList();
        for (Map.Entry<String, SavedPos> entry : locations.entrySet()) {
            NbtCompound item = new NbtCompound();
            item.putString("name", entry.getKey());
            item.putString("dimension", entry.getValue().dimension);
            item.putLong("pos", entry.getValue().pos.asLong());
            locationList.add(item);
        }
        nbt.put("locations", locationList);

        NbtList bindingList = new NbtList();
        for (Map.Entry<String, String> entry : blockCommands.entrySet()) {
            NbtCompound item = new NbtCompound();
            item.putString("key", entry.getKey());
            item.putString("command", entry.getValue());
            bindingList.add(item);
        }
        nbt.put("bindings", bindingList);

        NbtList zoneList = new NbtList();
        for (TriggerZone zone : zones.values()) {
            NbtCompound item = new NbtCompound();
            item.putString("name", zone.name);
            item.putString("dimension", zone.dimension);
            item.putLong("a", zone.a.asLong());
            item.putLong("b", zone.b.asLong());
            item.putString("enter", zone.enterCommand);
            item.putString("leave", zone.leaveCommand);
            zoneList.add(item);
        }
        nbt.put("zones", zoneList);

        NbtList playerList = new NbtList();
        for (UUID uuid : seenPlayers) playerList.add(NbtString.of(uuid.toString()));
        nbt.put("seenPlayers", playerList);
        nbt.putString("zoneCornerDimension", zoneCornerDimension);
        nbt.putLong("zoneCorner", zoneCorner.asLong());
        nbt.putInt("lobbyDelaySeconds", lobbyDelaySeconds);
        nbt.putInt("matchDurationMinutes", matchDurationMinutes);
        nbt.putString("matchMode", matchMode.name());
        nbt.putString("ruleMode", ruleMode.name());
        return nbt;
    }

    public static ChessWorldConfig fromNbt(NbtCompound nbt) {
        ChessWorldConfig config = new ChessWorldConfig();
        NbtList locationList = nbt.getList("locations", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < locationList.size(); i++) {
            NbtCompound item = locationList.getCompound(i);
            config.locations.put(item.getString("name"),
                new SavedPos(item.getString("dimension"), BlockPos.fromLong(item.getLong("pos"))));
        }
        NbtList bindingList = nbt.getList("bindings", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < bindingList.size(); i++) {
            NbtCompound item = bindingList.getCompound(i);
            config.blockCommands.put(item.getString("key"), item.getString("command"));
        }
        NbtList zoneList = nbt.getList("zones", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < zoneList.size(); i++) {
            NbtCompound item = zoneList.getCompound(i);
            TriggerZone zone = new TriggerZone(item.getString("name"), item.getString("dimension"),
                BlockPos.fromLong(item.getLong("a")), BlockPos.fromLong(item.getLong("b")),
                item.getString("enter"), item.getString("leave"));
            config.zones.put(zone.name, zone);
        }
        NbtList playerList = nbt.getList("seenPlayers", NbtElement.STRING_TYPE);
        for (int i = 0; i < playerList.size(); i++) {
            try { config.seenPlayers.add(UUID.fromString(playerList.getString(i))); }
            catch (IllegalArgumentException ignored) {}
        }
        config.zoneCornerDimension = nbt.getString("zoneCornerDimension");
        config.zoneCorner = BlockPos.fromLong(nbt.getLong("zoneCorner"));
        if (nbt.contains("lobbyDelaySeconds")) config.lobbyDelaySeconds = Math.max(0, Math.min(3600, nbt.getInt("lobbyDelaySeconds")));
        if (nbt.contains("matchDurationMinutes")) config.matchDurationMinutes = Math.max(0, Math.min(1440, nbt.getInt("matchDurationMinutes")));
        try { config.matchMode = ChessGameManager.MatchMode.valueOf(nbt.getString("matchMode")); }
        catch (IllegalArgumentException ignored) {}
        try { config.ruleMode = ChessGameManager.RuleMode.valueOf(nbt.getString("ruleMode")); }
        catch (IllegalArgumentException ignored) {}
        return config;
    }

    public static final class SavedPos {
        public final String dimension;
        public final BlockPos pos;
        public SavedPos(String dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos.toImmutable();
        }
    }

    public static final class TriggerZone {
        public final String name;
        public final String dimension;
        public final BlockPos a, b;
        public final String enterCommand, leaveCommand;
        public TriggerZone(String name, String dimension, BlockPos a, BlockPos b, String enter, String leave) {
            this.name = name; this.dimension = dimension;
            this.a = a.toImmutable(); this.b = b.toImmutable();
            this.enterCommand = cleanCommand(enter); this.leaveCommand = cleanCommand(leave);
        }
        public boolean contains(ServerWorld world, ServerPlayerEntity player) {
            if (!dimension.equals(world.getRegistryKey().getValue().toString())) return false;
            double minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX()) + 1.0;
            double minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY()) + 1.0;
            double minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ()) + 1.0;
            return player.getX() >= minX && player.getX() < maxX
                && player.getY() >= minY && player.getY() < maxY
                && player.getZ() >= minZ && player.getZ() < maxZ;
        }
    }
}
