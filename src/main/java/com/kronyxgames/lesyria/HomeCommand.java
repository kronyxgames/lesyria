package com.kronyxgames.lesyria;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HomeCommand implements CommandExecutor {

    private final LesyriaPlugin plugin;

    public HomeCommand(LesyriaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Teleport to home
            Location home = getHome(player);
            if (home != null) {
                player.teleport(home);
                player.sendMessage("Teleported to your home!");
            } else {
                player.sendMessage("You don't have a home set. Use /home set to set one.");
            }
        } else if (args.length == 1 && args[0].equalsIgnoreCase("set")) {
            // Set home
            setHome(player, player.getLocation());
            player.sendMessage("Home set!");
        } else {
            player.sendMessage("Usage: /home or /home set");
        }

        return true;
    }

    private Location getHome(Player player) {
        // Simple storage using config (for demo, in production use database)
        if (plugin.getConfig().contains(player.getUniqueId().toString())) {
            return (Location) plugin.getConfig().get(player.getUniqueId().toString());
        }
        return null;
    }

    private void setHome(Player player, Location location) {
        plugin.getConfig().set(player.getUniqueId().toString(), location);
        plugin.saveConfig();
    }
}