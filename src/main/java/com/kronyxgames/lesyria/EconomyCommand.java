package com.kronyxgames.lesyria;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Material;

public class EconomyCommand implements CommandExecutor {

    private final EconomyManager economy;

    public EconomyCommand(EconomyManager economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("Usage: /eco balance | /eco buy <item> <qty> | /eco sell <item> <qty>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "balance":
                double balance = economy.getBalance(player);
                player.sendMessage("Your balance: " + balance + " Lesyria Coins");
                break;
            case "buy":
                if (args.length < 3) {
                    player.sendMessage("Usage: /eco buy <item> <qty>");
                    return true;
                }
                try {
                    Material material = Material.valueOf(args[1].toUpperCase());
                    int qty = Integer.parseInt(args[2]);
                    double price = getBuyPrice(material) * qty;
                    economy.buyItem(player, material, qty, price);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("Invalid item or quantity.");
                }
                break;
            case "sell":
                if (args.length < 3) {
                    player.sendMessage("Usage: /eco sell <item> <qty>");
                    return true;
                }
                try {
                    Material material = Material.valueOf(args[1].toUpperCase());
                    int qty = Integer.parseInt(args[2]);
                    double price = getSellPrice(material) * qty;
                    economy.sellItem(player, material, qty, price);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("Invalid item or quantity.");
                }
                break;
            default:
                player.sendMessage("Unknown subcommand.");
        }

        return true;
    }

    private double getBuyPrice(Material material) {
        // Simple pricing
        switch (material) {
            case DIAMOND: return 100.0;
            case IRON_INGOT: return 20.0;
            case COAL: return 5.0;
            default: return 10.0;
        }
    }

    private double getSellPrice(Material material) {
        return getBuyPrice(material) * 0.8; // Sell for 80% of buy price
    }
}