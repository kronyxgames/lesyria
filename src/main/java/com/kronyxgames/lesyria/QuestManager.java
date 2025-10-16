package com.kronyxgames.lesyria;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class QuestManager {

    private final LesyriaPlugin plugin;
    private final Map<String, Quest> quests = new HashMap<>();

    public QuestManager(LesyriaPlugin plugin) {
        this.plugin = plugin;
        loadQuests();
    }

    private void loadQuests() {
        quests.put("gather_wood", new Quest("Gather Wood", "Collect 10 logs", org.bukkit.Material.OAK_LOG, 10, 50.0));
        quests.put("mine_coal", new Quest("Mine Coal", "Collect 5 coal", org.bukkit.Material.COAL, 5, 25.0));
    }

    public Quest getQuest(String name) {
        return quests.get(name);
    }

    public void startQuest(Player player, String questName) {
        Quest quest = getQuest(questName);
        if (quest != null) {
            player.sendMessage("Started quest: " + quest.getName() + " - " + quest.getDescription());
        } else {
            player.sendMessage("Quest not found.");
        }
    }

    public void completeQuest(Player player, String questName) {
        Quest quest = getQuest(questName);
        if (quest != null) {
            quest.complete(player, plugin.getEconomyManager());
        } else {
            player.sendMessage("Quest not found.");
        }
    }
}