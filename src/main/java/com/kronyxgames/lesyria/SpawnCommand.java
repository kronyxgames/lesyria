package com.kronyxgames.lesyria;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final LesyriaPlugin plugin;

    public SpawnCommand(LesyriaPlugin plugin) {
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
            // Teleport to spawn
            Location spawn = getSpawnLocation();
            player.teleport(spawn);
            player.sendMessage("Teleported to spawn!");
        } else if (args.length == 1) {
            // Teleport to city
            String city = args[0].toLowerCase();
            Location cityLoc = getCityLocation(city);
            if (cityLoc != null) {
                player.teleport(cityLoc);
                player.sendMessage("Teleported to " + city + "!");
            } else {
                player.sendMessage("City not found. Available cities: paris, london, tokyo");
            }
        } else {
            player.sendMessage("Usage: /spawn [city]");
        }

        return true;
    }

    private Location getSpawnLocation() {
        // Default spawn at 0,0
        return new Location(plugin.getServer().getWorld("earth"), 0, 65, 0);
    }

    private Location getCityLocation(String city) {
        // Placeholder coordinates for real cities (scale 1:1, approximate)
        switch (city) {
            case "paris":
                return new Location(plugin.getServer().getWorld("earth"), 200000, 65, 500000); // Approx Paris coords
            case "london":
                return new Location(plugin.getServer().getWorld("earth"), 0, 65, 510000); // Approx London
            case "tokyo":
                return new Location(plugin.getServer().getWorld("earth"), 9000000, 65, 4000000); // Approx Tokyo
            default:
                return null;
        }
    }
}