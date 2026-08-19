package fr.neoscraft.movecore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class MoveCoreCommand implements CommandExecutor, TabCompleter {
    private final MoveCoreService service;

    public MoveCoreCommand(MoveCoreService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        Player player = sender instanceof Player p ? p : null;
        if (name.equals("setspawn")) {
            if (!sender.hasPermission("movecore.admin")) return deny(sender);
            if (player == null) return playerOnly(sender);
            service.state().spawn = StoredLocation.from(player.getLocation());
            service.save();
            sender.sendMessage("Spawn set.");
            return true;
        }
        if (player == null) return playerOnly(sender);
        return switch (name) {
            case "sethome" -> setHome(player, args);
            case "home" -> home(player, args);
            case "delhome" -> deleteHome(sender, args);
            case "homes" -> listHomes(sender, args);
            case "setwarp" -> setWarp(player, args);
            case "warp" -> warp(player, args, false);
            case "delwarp" -> deleteWarp(sender, args);
            case "setpwarp" -> setPlayerWarp(player, args);
            case "pwarp" -> warp(player, args, true);
            case "delpwarp" -> deletePlayerWarp(player, args);
            case "spawn" -> spawn(player);
            case "tpa" -> request(player, args, false);
            case "tpahere" -> request(player, args, true);
            case "tpaccept" -> accept(player);
            case "tpdeny" -> denyRequest(player);
            case "tpalock" -> lock(player, args, true);
            case "tpaunlock" -> lock(player, args, false);
            case "rtp" -> rtp(player);
            case "back" -> back(player);
            default -> false;
        };
    }

    private boolean setHome(Player player, String[] args) {
        if (args.length != 1 || !service.validName(args[0])) return invalid(player);
        String home = key(args[0]);
        Map<String, StoredLocation> homes = service.homes(player.getUniqueId());
        if (!homes.containsKey(home) && homes.size() >= service.homeLimit(player)) {
            service.send(player, "limit-reached", Map.of("limit", String.valueOf(service.homeLimit(player))));
            return true;
        }
        homes.put(home, StoredLocation.from(player.getLocation()));
        service.save();
        service.send(player, "home-created", Map.of("home", home));
        return true;
    }

    private boolean home(Player player, String[] args) {
        if (args.length == 2) {
            if (!player.hasPermission("movecore.admin.homes.teleport")) return deny(player);
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) return notFound(player);
            return teleportStored(player, service.homes(target.getUniqueId()).get(key(args[1])), "admin-home");
        }
        Map<String, StoredLocation> homes = service.homes(player.getUniqueId());
        if (args.length == 0 && homes.size() == 1) {
            return teleportStored(player, homes.values().iterator().next(), "home");
        }
        if (args.length != 1) return invalid(player);
        return teleportStored(player, homes.get(key(args[0])), "home");
    }

    private boolean deleteHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        UUID owner = player.getUniqueId();
        String home;
        if (args.length == 2) {
            if (!player.hasPermission("movecore.admin.homes.delete")) return deny(player);
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) return notFound(player);
            owner = target.getUniqueId();
            home = key(args[1]);
        } else if (args.length == 1) {
            home = key(args[0]);
        } else return invalid(player);
        if (service.homes(owner).remove(home) == null) {
            service.send(player, "home-missing");
        } else {
            service.save();
            service.send(player, "home-deleted", Map.of("home", home));
        }
        return true;
    }

    private boolean listHomes(CommandSender sender, String[] args) {
        UUID owner;
        if (args.length == 1) {
            if (!sender.hasPermission("movecore.admin.homes.view")) return deny(sender);
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) return notFound(sender);
            owner = target.getUniqueId();
        } else if (sender instanceof Player player) {
            owner = player.getUniqueId();
        } else return playerOnly(sender);
        String homes = service.homes(owner).keySet().stream().sorted().collect(Collectors.joining(", "));
        service.send(sender, "home-list", Map.of("homes", homes.isEmpty() ? "-" : homes));
        return true;
    }

    private boolean setWarp(Player player, String[] args) {
        if (!player.hasPermission("movecore.admin.warps")) return deny(player);
        if (args.length != 1 || !service.validName(args[0])) return invalid(player);
        String warp = key(args[0]);
        service.state().warps.put(warp, StoredLocation.from(player.getLocation()));
        service.save();
        service.send(player, "warp-created", Map.of("warp", warp));
        return true;
    }

    private boolean setPlayerWarp(Player player, String[] args) {
        if (args.length != 1 || !service.validName(args[0])) return invalid(player);
        Map<String, StoredLocation> warps = service.playerWarps(player.getUniqueId());
        String warp = key(args[0]);
        if (!warps.containsKey(warp) && warps.size() >= service.playerWarpLimit(player)) {
            service.send(player, "limit-reached", Map.of("limit", String.valueOf(service.playerWarpLimit(player))));
            return true;
        }
        warps.put(warp, StoredLocation.from(player.getLocation()));
        service.save();
        service.send(player, "warp-created", Map.of("warp", warp));
        return true;
    }

    private boolean warp(Player player, String[] args, boolean playerWarp) {
        if (args.length != 1) return invalid(player);
        StoredLocation location = playerWarp ? findPlayerWarp(args[0]) : service.state().warps.get(key(args[0]));
        return teleportStored(player, location, "warp");
    }

    private StoredLocation findPlayerWarp(String raw) {
        String wanted = key(raw);
        for (Map<String, StoredLocation> warps : service.state().playerWarps.values()) {
            if (warps.containsKey(wanted)) return warps.get(wanted);
        }
        return null;
    }

    private boolean deleteWarp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("movecore.admin.warps")) return deny(sender);
        if (args.length != 1) return invalid(sender);
        String warp = key(args[0]);
        if (service.state().warps.remove(warp) == null) service.send(sender, "warp-missing");
        else { service.save(); service.send(sender, "warp-deleted", Map.of("warp", warp)); }
        return true;
    }

    private boolean deletePlayerWarp(Player player, String[] args) {
        if (args.length != 1) return invalid(player);
        String warp = key(args[0]);
        if (service.playerWarps(player.getUniqueId()).remove(warp) == null) service.send(player, "warp-missing");
        else { service.save(); service.send(player, "warp-deleted", Map.of("warp", warp)); }
        return true;
    }

    private boolean spawn(Player player) {
        StoredLocation spawn = service.state().spawn;
        if (spawn == null) { service.send(player, "spawn-missing"); return true; }
        return teleportStored(player, spawn, "spawn");
    }

    private boolean request(Player player, String[] args, boolean here) {
        if (args.length != 1) return invalid(player);
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target == player) return notFound(player);
        service.request(player, target, here);
        return true;
    }

    private boolean accept(Player player) {
        MoveCoreService.TpaRequest request = service.requestFor(player);
        if (request == null) { service.send(player, "tpa-denied"); return true; }
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) { service.send(player, "player-not-found"); return true; }
        Location destination = request.here() ? requester.getLocation() : player.getLocation();
        service.teleport(requester, destination, "tpa");
        service.send(player, "tpa-accepted");
        return true;
    }

    private boolean denyRequest(Player player) {
        MoveCoreService.TpaRequest request = service.requestFor(player);
        if (request != null) {
            Player requester = Bukkit.getPlayer(request.requester());
            if (requester != null) service.send(requester, "tpa-denied");
        }
        service.send(player, "tpa-denied");
        return true;
    }

    private boolean lock(Player player, String[] args, boolean lock) {
        if (args.length != 1) return invalid(player);
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) return notFound(player);
        if (lock) service.lock(player, target.getUniqueId()); else service.unlock(player, target.getUniqueId());
        service.send(player, lock ? "tpa-locked" : "tpa-unlocked", Map.of("player", target.getName()));
        return true;
    }

    private boolean rtp(Player player) {
        Location destination = service.safeRandomLocation(player);
        if (destination == null) service.send(player, "rtp-failed");
        else service.teleport(player, destination, "rtp");
        return true;
    }

    private boolean back(Player player) {
        StoredLocation previous = service.state().previousLocations.get(player.getUniqueId());
        if (previous == null) { service.send(player, "back-missing"); return true; }
        Location location = service.nearestSafe(previous.toLocation());
        if (location == null) { service.send(player, "rtp-failed"); return true; }
        return service.teleport(player, location, "back");
    }

    private boolean teleportStored(Player player, StoredLocation stored, String reason) {
        if (stored == null) { service.send(player, reason.equals("home") ? "home-missing" : "warp-missing"); return true; }
        Location location = service.nearestSafe(stored.toLocation());
        return service.teleport(player, location, reason);
    }

    private String key(String value) { return value.toLowerCase(Locale.ROOT); }
    private boolean deny(CommandSender sender) { service.send(sender, "no-permission"); return true; }
    private boolean playerOnly(CommandSender sender) { service.send(sender, "player-only"); return true; }
    private boolean notFound(CommandSender sender) { service.send(sender, "player-not-found"); return true; }
    private boolean invalid(CommandSender sender) { service.send(sender, "invalid-name"); return true; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("home") && sender instanceof Player player) {
            values.addAll(service.homes(player.getUniqueId()).keySet());
        } else if (command.getName().equalsIgnoreCase("warp")) {
            values.addAll(service.state().warps.keySet());
        } else if (command.getName().equalsIgnoreCase("pwarp")) {
            values.addAll(service.state().playerWarps.values().stream().flatMap(map -> map.keySet().stream()).toList());
        } else if (List.of("tpa", "tpahere", "tpalock", "tpaunlock").contains(command.getName().toLowerCase(Locale.ROOT))) {
            values.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}