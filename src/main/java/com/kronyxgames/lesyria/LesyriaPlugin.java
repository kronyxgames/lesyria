package com.kronyxgames.lesyria;

import org.bukkit.plugin.java.JavaPlugin;

public class LesyriaPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("Lesyria plugin enabled!");

        // Register commands
        this.getCommand("home").setExecutor(new HomeCommand(this));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Lesyria plugin disabled!");
    }
}