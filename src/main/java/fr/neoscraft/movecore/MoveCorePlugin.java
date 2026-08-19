package fr.neoscraft.movecore;

import org.bukkit.plugin.java.JavaPlugin;

public final class MoveCorePlugin extends JavaPlugin {
    private MoveCoreService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        service = new MoveCoreService(this);
        service.enable();
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.disable();
        }
    }

    public MoveCoreService service() {
        return service;
    }
}