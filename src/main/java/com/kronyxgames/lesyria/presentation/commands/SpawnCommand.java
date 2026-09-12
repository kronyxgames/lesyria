package com.kronyxgames.lesyria.presentation.commands;

import com.kronyxgames.lesyria.LesyriaPlugin;
import com.kronyxgames.lesyria.domain.city.City;
import com.kronyxgames.lesyria.presentation.teleport.SpawnResolver;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /spawn} et {@code /spawn &lt;ville&gt;}.
 */
public final class SpawnCommand {

    private SpawnCommand() {
    }

    /**
     * @param plugin plugin
     * @return le routeur de la commande
     */
    public static CommandRouter create(LesyriaPlugin plugin) {
        CommandRouter router = new CommandRouter(plugin, plugin.messages(), "spawn",
                "lesyria.command.spawn");

        router.sub("", "[ville]", "Se teleporter au spawn ou a une ville", true,
                        (sender, args) -> teleport(plugin, sender, args))
                .completer((sender, args) -> args.length <= 1
                        ? cityNames(plugin, CommandSupport.lastArgument(args))
                        : List.of());

        return router;
    }

    private static void teleport(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        Player player = CommandSupport.requirePlayer(sender);
        if (args.length == 0) {
            Location spawn = SpawnResolver.resolveMainSpawn(plugin.getServer(), plugin.config());
            if (spawn == null) {
                plugin.messages().error(sender, "Le monde du spawn est introuvable : &e{world}",
                        "world", plugin.config().spawn().world());
                return;
            }
            plugin.teleports().teleport(player, spawn, "spawn");
            return;
        }

        City city = plugin.cities().cachedByName(args[0]);
        if (city == null) {
            plugin.messages().error(sender, "Ville inconnue : &e{city}", "city", args[0]);
            return;
        }
        Location destination = SpawnResolver.resolveCitySpawn(plugin.getServer(), city);
        if (destination == null) {
            plugin.messages().error(sender, "Le monde de cette ville est introuvable.");
            return;
        }
        plugin.teleports().teleport(player, destination, city.name());
    }

    private static List<String> cityNames(LesyriaPlugin plugin, String prefix) {
        List<String> names = new ArrayList<>();
        for (City city : plugin.cities().cache().all()) {
            if (city.name().toLowerCase(java.util.Locale.ROOT)
                    .startsWith(prefix.toLowerCase(java.util.Locale.ROOT))) {
                names.add(city.name());
            }
        }
        return names;
    }
}
