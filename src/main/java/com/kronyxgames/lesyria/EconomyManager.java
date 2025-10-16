package com.kronyxgames.lesyria;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {

    private final LesyriaPlugin plugin;
    private final Map<UUID, Double> playerBalances = new HashMap<>();

    public EconomyManager(LesyriaPlugin plugin) {
        this.plugin = plugin;
    }

    public double getBalance(Player player) {
        return playerBalances.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void addMoney(Player player, double amount) {
        UUID uuid = player.getUniqueId();
        double current = getBalance(player);
        playerBalances.put(uuid, current + amount);
        player.sendMessage("Added " + amount + " Lesyria Coins. Balance: " + (current + amount));
    }

    public boolean deductMoney(Player player, double amount) {
        UUID uuid = player.getUniqueId();
        double current = getBalance(player);
        if (current >= amount) {
            playerBalances.put(uuid, current - amount);
            player.sendMessage("Deducted " + amount + " Lesyria Coins. Balance: " + (current - amount));
            return true;
        } else {
            player.sendMessage("Insufficient funds!");
            return false;
        }
    }

    public void giveStartingMoney(Player player) {
        addMoney(player, 100.0); // Starting balance
    }

    // Simple shop system
    public boolean buyItem(Player player, Material material, int quantity, double price) {
        if (deductMoney(player, price)) {
            ItemStack item = new ItemStack(material, quantity);
            player.getInventory().addItem(item);
            player.sendMessage("Bought " + quantity + " " + material.name());
            return true;
        }
        return false;
    }

    public void sellItem(Player player, Material material, int quantity, double price) {
        ItemStack item = new ItemStack(material, quantity);
        if (player.getInventory().containsAtLeast(item, quantity)) {
            player.getInventory().removeItem(item);
            addMoney(player, price);
            player.sendMessage("Sold " + quantity + " " + material.name());
        } else {
            player.sendMessage("You don't have enough " + material.name());
        }
    }
}