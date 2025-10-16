package com.kronyxgames.lesyria;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.World;

public class NPCManager {

    private final LesyriaPlugin plugin;

    public NPCManager(LesyriaPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnQuestNPC(Location location, String name) {
        World world = location.getWorld();
        Villager npc = (Villager) world.spawnEntity(location, EntityType.VILLAGER);
        npc.setCustomName(name);
        npc.setCustomNameVisible(true);
        npc.setAI(false); // Prevent movement
        npc.setInvulnerable(true);
        // Add quest interaction logic here (e.g., on right-click)
    }

    public void spawnShopNPC(Location location, String name) {
        World world = location.getWorld();
        Villager npc = (Villager) world.spawnEntity(location, EntityType.VILLAGER);
        npc.setCustomName(name + " (Shop)");
        npc.setCustomNameVisible(true);
        npc.setAI(false);
        npc.setInvulnerable(true);
        npc.setProfession(Villager.Profession.LIBRARIAN); // Example profession
    }
}