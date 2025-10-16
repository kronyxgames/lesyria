package com.kronyxgames.lesyria;

import org.bukkit.entity.Player;
import org.bukkit.Material;

public class Quest {

    private final String name;
    private final String description;
    private final Material requiredItem;
    private final int requiredAmount;
    private final double rewardMoney;

    public Quest(String name, String description, Material requiredItem, int requiredAmount, double rewardMoney) {
        this.name = name;
        this.description = description;
        this.requiredItem = requiredItem;
        this.requiredAmount = requiredAmount;
        this.rewardMoney = rewardMoney;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean canComplete(Player player) {
        return player.getInventory().containsAtLeast(new org.bukkit.inventory.ItemStack(requiredItem), requiredAmount);
    }

    public void complete(Player player, EconomyManager economy) {
        if (canComplete(player)) {
            player.getInventory().removeItem(new org.bukkit.inventory.ItemStack(requiredItem, requiredAmount));
            economy.addMoney(player, rewardMoney);
            player.sendMessage("Quest '" + name + "' completed! Reward: " + rewardMoney + " coins.");
        } else {
            player.sendMessage("You don't have the required items for quest '" + name + "'.");
        }
    }
}