package com.kronyxgames.lesyria;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.generator.ChunkGenerator;

public class LesyriaPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("Lesyria MMORPG plugin enabled!");

        // Register commands
        this.getCommand("home").setExecutor(new HomeCommand(this));
        this.getCommand("spawn").setExecutor(new SpawnCommand(this));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Lesyria plugin disabled!");
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        // Return Earth generator for "earth" world
        if ("earth".equals(worldName)) {
            return new EarthWorldGenerator();
        }
        return null;
    }
}