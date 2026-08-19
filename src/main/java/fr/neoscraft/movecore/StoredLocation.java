package fr.neoscraft.movecore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {
    public static StoredLocation from(Location location) {
        return new StoredLocation(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    public Location toLocation() {
        World loadedWorld = Bukkit.getWorld(world);
        return loadedWorld == null ? null : new Location(loadedWorld, x, y, z, yaw, pitch);
    }
}