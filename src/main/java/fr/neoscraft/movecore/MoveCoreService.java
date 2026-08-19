package fr.neoscraft.movecore;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class MoveCoreService implements Listener {
    private final MoveCorePlugin plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pendingOrigins = new ConcurrentHashMap<>();
    private final Map<UUID, TpaRequest> requests = new ConcurrentHashMap<>();
    private DataStore store;
    private StorageState state;

    public MoveCoreService(MoveCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        store = createStore();
        state = store.load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        MoveCoreCommand command = new MoveCoreCommand(this);
        for (String name : List.of("home", "sethome", "delhome", "homes", "warp", "setwarp", "delwarp",
                "pwarp", "setpwarp", "delpwarp", "spawn", "setspawn", "tpa", "tpahere", "tpaccept",
                "tpdeny", "tpalock", "tpaunlock", "rtp", "back")) {
            if (plugin.getCommand(name) != null) {
                plugin.getCommand(name).setExecutor(command);
                plugin.getCommand(name).setTabCompleter(command);
            }
        }
    }

    public void disable() {
        if (store != null) {
            store.save(state);
            store.close();
        }
    }

    private DataStore createStore() {
        String type = plugin.getConfig().getString("storage.type", "json").toLowerCase(Locale.ROOT);
        if (type.equals("mysql") || type.equals("mariadb")) {
            return new MysqlDataStore(plugin.getConfig().getString("storage.mysql.jdbc-url"),
                    plugin.getConfig().getString("storage.mysql.username"),
                    plugin.getConfig().getString("storage.mysql.password"),
                    plugin.getConfig().getString("storage.mysql.table-prefix", "movecore_"));
        }
        return new JsonDataStore(Path.of(plugin.getDataFolder().getPath(),
                plugin.getConfig().getString("storage.json-file", "data.json")));
    }

    public Component message(String key, Map<String, String> replacements) {
        String text = plugin.getConfig().getString("messages." + key, key);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            text = text.replace("{" + replacement.getKey() + "}", replacement.getValue());
        }
        return color(plugin.getConfig().getString("messages.prefix", "") + text);
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(message(key, Map.of()));
    }

    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        sender.sendMessage(message(key, replacements));
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,32}");
    }

    public Map<String, StoredLocation> homes(UUID owner) {
        return state.homes.computeIfAbsent(owner, ignored -> new java.util.HashMap<>());
    }

    public Map<String, StoredLocation> playerWarps(UUID owner) {
        return state.playerWarps.computeIfAbsent(owner, ignored -> new java.util.HashMap<>());
    }

    public int homeLimit(Player player) {
        return limit(player, "homes", "default-limit");
    }

    public int playerWarpLimit(Player player) {
        return limit(player, "warps.player-limits", "warps.default-player-limit");
    }
    private int limit(Player player, String path, String defaultPath) {
        if (path.equals("homes")) {
            for (String grade : plugin.getConfig().getConfigurationSection("homes.limits").getKeys(false)) {
                if (player.hasPermission("movecore.homes." + grade)) {
                    return plugin.getConfig().getInt("homes.limits." + grade);
                }
            }
            return plugin.getConfig().getInt("homes.default-limit", 1);
        }
        for (String grade : plugin.getConfig().getConfigurationSection("warps.player-limits").getKeys(false)) {
            if (player.hasPermission("movecore.pwarps." + grade)) {
                return plugin.getConfig().getInt("warps.player-limits." + grade);
            }
        }
        return plugin.getConfig().getInt(defaultPath, 1);
    }

    public void save() {
        store.save(state);
    }

    public boolean teleport(Player player, Location destination, String reason) {
        if (destination == null || destination.getWorld() == null) {
            return false;
        }
        long remaining = cooldowns.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis();
        if (remaining > 0) {
            send(player, "teleport-cooldown", Map.of("seconds", String.valueOf((remaining + 999) / 1000)));
            return false;
        }
        cancelPending(player, false);
        Location origin = player.getLocation().clone();
        long warmup = plugin.getConfig().getLong("teleport.warmup-millis", 0);
        if (warmup <= 0) {
            completeTeleport(player, origin, destination, reason);
            return true;
        }
        pendingOrigins.put(player.getUniqueId(), origin);
        send(player, "teleport-start", Map.of("seconds", String.valueOf((warmup + 999) / 1000)));
        pendingTeleports.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin,
                () -> completePending(player, destination, reason), Math.max(1, (warmup + 49) / 50)));
        return true;
    }

    private void completePending(Player player, Location destination, String reason) {
        pendingTeleports.remove(player.getUniqueId());
        Location origin = pendingOrigins.remove(player.getUniqueId());
        if (origin != null) {
            completeTeleport(player, origin, destination, reason);
        }
    }

    private void completeTeleport(Player player, Location origin, Location destination, String reason) {
        state.previousLocations.put(player.getUniqueId(), StoredLocation.from(origin));
        player.teleport(destination);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownFor(player));
        state.logs.add(new TeleportLog(player.getUniqueId(), player.getName(), null, null,
                StoredLocation.from(origin), StoredLocation.from(destination), Instant.now(), reason));
        save();
        send(player, "teleport-success");
    }

    public void cancelPending(Player player, boolean notify) {
        BukkitTask task = pendingTeleports.remove(player.getUniqueId());
        pendingOrigins.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            if (notify) {
                send(player, "teleport-cancelled");
            }
        }
    }

    private long cooldownFor(Player player) {
        return plugin.getConfig().getLong("teleport.cooldown-millis", 0);
    }

    public Location safeRandomLocation(Player player) {
        World world = player.getWorld();
        if (plugin.getConfig().getStringList("rtp.blocked-worlds").contains(world.getName())) {
            return null;
        }
        int min = plugin.getConfig().getInt("rtp.min-radius", 100);
        int max = plugin.getConfig().getInt("rtp.max-radius", 5000);
        int attempts = plugin.getConfig().getInt("rtp.max-attempts", 32);
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double radius = ThreadLocalRandom.current().nextDouble(min, Math.max(min + 1, max));
            int x = (int) Math.round(player.getLocation().getX() + Math.cos(angle) * radius);
            int z = (int) Math.round(player.getLocation().getZ() + Math.sin(angle) * radius);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location candidate = new Location(world, x + .5, y, z + .5);
            if (isSafe(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public boolean isSafe(Location location) {
        if (location.getWorld() == null || location.getBlockY() < location.getWorld().getMinHeight()
                || location.getBlockY() >= location.getWorld().getMaxHeight() - 1) {
            return false;
        }
        Material floor = location.clone().subtract(0, 1, 0).getBlock().getType();
        return floor.isSolid() && !floor.toString().contains("LAVA") && location.getBlock().isPassable()
                && location.clone().add(0, 1, 0).getBlock().isPassable();
    }

    public Location nearestSafe(Location origin) {
        int radius = plugin.getConfig().getInt("back.safe-search-radius", 8);
        for (int distance = 0; distance <= radius; distance++) {
            for (int x = -distance; x <= distance; x++) {
                for (int z = -distance; z <= distance; z++) {
                    Location candidate = origin.clone().add(x, 0, z);
                    if (isSafe(candidate)) return candidate;
                }
            }
        }
        return null;
    }

    public void request(Player requester, Player target, boolean here) {
        if (state.voidSafePlayers.contains(target.getUniqueId())) return;
        requests.put(target.getUniqueId(), new TpaRequest(requester.getUniqueId(), here));
        send(requester, "tpa-sent", Map.of("player", target.getName()));
        send(target, "tpa-received", Map.of("player", requester.getName()));
    }

    public TpaRequest requestFor(Player target) { return requests.remove(target.getUniqueId()); }

    public void lock(Player player, UUID target) { state.voidSafePlayers.add(target); save(); }
    public void unlock(Player player, UUID target) { state.voidSafePlayers.remove(target); save(); }
    public record TpaRequest(UUID requester, boolean here) { }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() != null && (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ())
                && plugin.getConfig().getBoolean("teleport.cancel-on-move", true)) {
            cancelPending(event.getPlayer(), true);
        }
        if (event.getTo() != null && event.getTo().getY() < plugin.getConfig().getDouble("spawn.void-y", -64)
                && state.spawn != null && !state.voidSafePlayers.contains(event.getPlayer().getUniqueId())) {
            Location spawn = state.spawn.toLocation();
            if (spawn != null) event.getPlayer().teleport(spawn);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer(), false);
        requests.remove(event.getPlayer().getUniqueId());
    }

    public StorageState state() { return state; }
    public MoveCorePlugin plugin() { return plugin; }
}