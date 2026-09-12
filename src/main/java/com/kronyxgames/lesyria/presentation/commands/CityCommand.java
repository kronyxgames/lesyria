package com.kronyxgames.lesyria.presentation.commands;

import com.kronyxgames.lesyria.LesyriaPlugin;
import com.kronyxgames.lesyria.domain.city.City;
import com.kronyxgames.lesyria.domain.nation.Nation;
import com.kronyxgames.lesyria.domain.nation.NationMember;
import com.kronyxgames.lesyria.domain.player.PlayerProfile;
import com.kronyxgames.lesyria.domain.territory.Territory;
import com.kronyxgames.lesyria.presentation.teleport.SpawnResolver;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /city create|info|list|spawn|setspawn}.
 *
 * <p>Une ville est fondee dans un territoire de sa nation et sert ensuite de
 * point de spawn nomme. Le cout de fondation est preleve sur la tresorerie
 * nationale.</p>
 */
public final class CityCommand {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private CityCommand() {
    }

    /**
     * @param plugin plugin
     * @return le routeur de la commande
     */
    public static CommandRouter create(LesyriaPlugin plugin) {
        CommandRouter router = new CommandRouter(plugin, plugin.messages(), "city",
                "lesyria.command.city");

        router.sub("create", "<nom>", "Fonder une ville", true,
                        (sender, args) -> create(plugin, sender, args))
                .completer((sender, args) -> List.of("<nom>"));

        router.sub("info", "[ville]", "Informations sur une ville", true,
                        (sender, args) -> info(plugin, sender, args))
                .completer((sender, args) -> cityNames(plugin, args));

        router.sub("list", "", "Lister les villes", true, (sender, args) -> list(plugin, sender));

        router.sub("spawn", "<ville>", "Se teleporter a une ville", true,
                        (sender, args) -> spawn(plugin, sender, args))
                .completer((sender, args) -> cityNames(plugin, args));

        router.sub("setspawn", "<ville>", "Definir le spawn de la ville", true,
                        (sender, args) -> setSpawn(plugin, sender, args))
                .completer((sender, args) -> cityNames(plugin, args));

        return router;
    }

    private static void create(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        CommandSupport.requireArgs(args, 1, "/city create <nom>");
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile actor = CommandSupport.requireProfile(plugin.players(), player);
        Location location = player.getLocation();
        var messages = plugin.messages();

        AsyncFeedback.when(plugin, messages, sender,
                plugin.cities().create(actor, args[0], location.getWorld().getName(),
                        location.getX(), location.getY(), location.getZ(),
                        location.getYaw(), location.getPitch()),
                city -> messages.send(sender,
                        "&aVille &e{name} &afondee. Utilisez &e/city spawn {name} &apour vous y rendre.",
                        "name", city.name()));
    }

    private static void info(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        Player player = CommandSupport.requirePlayer(sender);
        var messages = plugin.messages();

        City city;
        if (args.length == 0) {
            Location location = player.getLocation();
            Territory territory = plugin.territories().territoryAt(location.getWorld().getName(),
                    location.getBlockX() >> 4, location.getBlockZ() >> 4);
            if (territory == null) {
                messages.error(sender, "Aucune ville ici. Precisez un nom : &e/city info <ville>");
                return;
            }
            city = plugin.cities().cachedOfNation(territory.nationId()).stream()
                    .filter(candidate -> candidate.territoryId() != null
                            && candidate.territoryId().equals(territory.id()))
                    .findFirst()
                    .orElse(null);
            if (city == null) {
                messages.error(sender, "Aucune ville ici. Precisez un nom : &e/city info <ville>");
                return;
            }
        } else {
            city = plugin.cities().cachedByName(args[0]);
            if (city == null) {
                messages.error(sender, "Ville inconnue : &e{city}", "city", args[0]);
                return;
            }
        }

        Nation nation = plugin.nations().cached(city.nationId());
        Territory territory = city.territoryId() == null ? null
                : plugin.territories().index().byId(city.territoryId());
        messages.sendRaw(sender, "");
        messages.sendRaw(sender, "&6&l{name}", "name", city.name());
        messages.sendRaw(sender, "&7Nation: &f{nation}", "nation",
                nation == null ? "?" : nation.name() + " " + nation.displayTag());
        messages.sendRaw(sender, "&7Centre: &f{world} {x} {y} {z}",
                "world", city.world(),
                "x", Integer.toString((int) city.x()),
                "y", Integer.toString((int) city.y()),
                "z", Integer.toString((int) city.z()));
        messages.sendRaw(sender, "&7Territoire: &f{territory}",
                "territory", territory == null ? "aucun" : territory.name());
        messages.sendRaw(sender, "&7Citoyens de la nation: &f{citizens} &7| Fondee le &f{date}",
                "citizens", Integer.toString(
                        plugin.nations().cache().memberCount(city.nationId())),
                "date", DATE.format(city.createdAt()));
        messages.sendRaw(sender, "");
    }

    private static void list(LesyriaPlugin plugin, CommandSender sender) {
        Player player = CommandSupport.requirePlayer(sender);
        NationMember membership = plugin.nations().membershipOf(player.getUniqueId());
        List<City> cities = membership == null
                ? List.copyOf(plugin.cities().cache().all())
                : plugin.cities().cachedOfNation(membership.nationId());
        var messages = plugin.messages();
        if (cities.isEmpty()) {
            messages.send(sender, "&7Aucune ville a afficher.");
            return;
        }
        messages.send(sender, "&7Villes ({count}) :", "count", Integer.toString(cities.size()));
        for (City city : cities) {
            Nation nation = plugin.nations().cached(city.nationId());
            messages.sendRaw(sender, " &8- &e{name} &7- {nation} &7({world} {x} {z})",
                    "name", city.name(),
                    "nation", nation == null ? "?" : nation.displayTag(),
                    "world", city.world(),
                    "x", Integer.toString((int) city.x()),
                    "z", Integer.toString((int) city.z()));
        }
    }

    private static void spawn(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        CommandSupport.requireArgs(args, 1, "/city spawn <ville>");
        Player player = CommandSupport.requirePlayer(sender);
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

    private static void setSpawn(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        CommandSupport.requireArgs(args, 1, "/city setspawn <ville>");
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile actor = CommandSupport.requireProfile(plugin.players(), player);
        Location location = player.getLocation();
        var messages = plugin.messages();
        AsyncFeedback.when(plugin, messages, sender,
                plugin.cities().setSpawn(actor, args[0], location.getWorld().getName(),
                        location.getX(), location.getY(), location.getZ(),
                        location.getYaw(), location.getPitch()),
                city -> messages.send(sender, "&aSpawn de &e{name} &amis a jour.", "name", city.name()));
    }

    private static List<String> cityNames(LesyriaPlugin plugin, String[] args) {
        String prefix = CommandSupport.lastArgument(args).toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (City city : plugin.cities().cache().all()) {
            if (city.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(city.name());
            }
        }
        return names;
    }
}
