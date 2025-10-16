package com.kronyxgames.lesyria;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class LesyriaPlugin extends JavaPlugin implements Listener {

    private EconomyManager economyManager;
    private QuestManager questManager;
    private NPCManager npcManager;
    private APIManager apiManager;

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("Lesyria MMORPG plugin enabled!");

        economyManager = new EconomyManager(this);
        questManager = new QuestManager(this);
        npcManager = new NPCManager(this);
        apiManager = new APIManager(this);

        // Register commands
        this.getCommand("home").setExecutor(new HomeCommand(this));
        this.getCommand("spawn").setExecutor(new SpawnCommand(this));
        this.getCommand("eco").setExecutor(new EconomyCommand(economyManager));
        this.getCommand("quest").setExecutor(new QuestCommand(questManager));

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Start API on port 8080
        apiManager.startAPI(8080);

        // Spawn initial NPCs
        spawnInitialNPCs();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        apiManager.stopAPI();
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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Give starting money
        economyManager.giveStartingMoney(event.getPlayer());
    }

    private void spawnInitialNPCs() {
        // Spawn NPCs in cities
        npcManager.spawnQuestNPC(new org.bukkit.Location(getServer().getWorld("earth"), 200000, 65, 500000), "Quest Master");
        npcManager.spawnShopNPC(new org.bukkit.Location(getServer().getWorld("earth"), 200010, 65, 500010), "Shopkeeper");
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }
}