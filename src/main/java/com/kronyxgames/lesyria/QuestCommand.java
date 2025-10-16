package com.kronyxgames.lesyria;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class QuestCommand implements CommandExecutor {

    private final QuestManager questManager;

    public QuestCommand(QuestManager questManager) {
        this.questManager = questManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage("Usage: /quest <start|complete> <quest_name>");
            return true;
        }

        String action = args[0].toLowerCase();
        String questName = args[1].toLowerCase();

        switch (action) {
            case "start":
                questManager.startQuest(player, questName);
                break;
            case "complete":
                questManager.completeQuest(player, questName);
                break;
            default:
                player.sendMessage("Unknown action. Use start or complete.");
        }

        return true;
    }
}