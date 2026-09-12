package com.kronyxgames.lesyria.presentation.commands;

import com.kronyxgames.lesyria.LesyriaPlugin;
import com.kronyxgames.lesyria.domain.nation.Nation;
import com.kronyxgames.lesyria.domain.nation.NationMember;
import com.kronyxgames.lesyria.domain.player.PlayerProfile;
import com.kronyxgames.lesyria.domain.territory.AccessLevel;
import com.kronyxgames.lesyria.domain.territory.Territory;
import com.kronyxgames.lesyria.domain.territory.TerritoryException;
import com.kronyxgames.lesyria.domain.territory.TerritoryPermission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /territory claim|info|list|unclaim|perm|map}.
 *
 * <p>Les lectures ({@code info}, {@code list}, {@code map}) sont servies par
 * l'index territorial en memoire : aucun acces SQL sur le thread principal.
 * Les ecritures passent par {@link com.kronyxgames.lesyria.application.territory.TerritoryService}.</p>
 */
public final class TerritoryCommand {

    private TerritoryCommand() {
    }

    /**
     * @param plugin plugin
     * @return le routeur de la commande
     */
    public static CommandRouter create(LesyriaPlugin plugin) {
        CommandRouter router = new CommandRouter(plugin, plugin.messages(), "territory",
                "lesyria.command.territory");

        router.sub("claim", "[nom]", "Revendiquer la zone autour de vous", true,
                        (sender, args) -> claim(plugin, sender, args))
                .completer((sender, args) -> List.of("<nom-du-territoire>"));

        router.sub("info", "[territoire]", "Informations sur un territoire", true,
                        (sender, args) -> info(plugin, sender, args))
                .completer((sender, args) -> territoryNames(plugin, sender, args));

        router.sub("list", "", "Vos territoires", true, (sender, args) -> list(plugin, sender));

        router.sub("unclaim", "<territoire>", "Abandonner un territoire", true,
                        (sender, args) -> unclaim(plugin, sender, args))
                .completer((sender, args) -> territoryNames(plugin, sender, args));

        router.sub("perm", "<territoire> <permission> <niveau>", "Modifier une permission", true,
                        (sender, args) -> permission(plugin, sender, args))
                .completer((sender, args) -> {
                    if (args.length <= 1) {
                        return territoryNames(plugin, sender, args);
                    }
                    if (args.length == 2) {
                        List<String> permissions = new ArrayList<>();
                        for (TerritoryPermission permission : TerritoryPermission.values()) {
                            permissions.add(permission.name().toLowerCase(Locale.ROOT));
                        }
                        return permissions;
                    }
                    return List.of("NATION", "ALLY", "PUBLIC", "NONE");
                });

        router.sub("map", "", "Carte des territoires alentour", true,
                (sender, args) -> map(plugin, sender));

        return router;
    }

    private static void claim(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile profile = CommandSupport.requireProfile(plugin.players(), player);
        String name = args.length == 0 ? null : args[0];
        var location = player.getLocation();

        AsyncFeedback.when(plugin, plugin.messages(), sender,
                plugin.territories().claim(profile, location.getWorld().getName(),
                        location.getBlockX() >> 4, location.getBlockZ() >> 4, name),
                territory -> plugin.messages().send(sender,
                        "&aTerritoire &e{name} &arevendique &7({chunks} chunks, zone {area})",
                        "name", territory.name(),
                        "chunks", Integer.toString(territory.chunkCount()),
                        "area", territory.describeArea()));
    }

    private static void info(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        Player player = CommandSupport.requirePlayer(sender);
        Territory territory;
        if (args.length == 0) {
            var location = player.getLocation();
            territory = plugin.territories().territoryAt(location.getWorld().getName(),
                    location.getBlockX() >> 4, location.getBlockZ() >> 4);
            if (territory == null) {
                plugin.messages().send(sender,
                        "&7Vous etes en zone sauvage : aucun territoire ici.");
                return;
            }
        } else {
            territory = findNationTerritory(plugin, sender, args[0]);
            if (territory == null) {
                plugin.messages().error(sender, "Territoire introuvable : &e{name}", "name", args[0]);
                return;
            }
        }
        display(sender, plugin, territory);
    }

    private static void display(CommandSender sender, LesyriaPlugin plugin, Territory territory) {
        var messages = plugin.messages();
        Nation nation = plugin.nations().cached(territory.nationId());
        messages.sendRaw(sender, "");
        messages.sendRaw(sender, "&6&l{name} &7- territoire {status}",
                "name", territory.name(), "status", territory.status().name());
        messages.sendRaw(sender, "&7Nation: &f{nation}", "nation",
                nation == null ? "?" : nation.name() + " " + nation.displayTag());
        messages.sendRaw(sender, "&7Monde: &f{world} &7| Zone: &f{area} &7| Chunks: &f{chunks}",
                "world", territory.world(),
                "area", territory.describeArea(),
                "chunks", Integer.toString(territory.chunkCount()));
        StringBuilder permissions = new StringBuilder();
        for (TerritoryPermission permission : TerritoryPermission.values()) {
            permissions.append("&7").append(permission.name()).append(":&f ")
                    .append(territory.permissions().level(permission).name()).append("  ");
        }
        messages.sendRaw(sender, permissions.toString().trim());
        messages.sendRaw(sender, "");
    }

    private static void list(LesyriaPlugin plugin, CommandSender sender) {
        Player player = CommandSupport.requirePlayer(sender);
        NationMember membership = plugin.nations().membershipOf(player.getUniqueId());
        if (membership == null) {
            plugin.messages().error(sender, "Vous n'appartenez a aucune nation.");
            return;
        }
        List<Territory> territories = plugin.territories().territoriesOf(membership.nationId());
        if (territories.isEmpty()) {
            plugin.messages().send(sender, "&7Votre nation ne possede aucun territoire.");
            return;
        }
        plugin.messages().send(sender, "&7Territoires de votre nation ({count}) :",
                "count", Integer.toString(territories.size()));
        for (Territory territory : territories) {
            plugin.messages().sendRaw(sender, " &8- &e{name} &7{area} &8[{status}]",
                    "name", territory.name(), "area", territory.describeArea(),
                    "status", territory.status().name());
        }
    }

    private static void unclaim(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        CommandSupport.requireArgs(args, 1, "/territory unclaim <territoire>");
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile profile = CommandSupport.requireProfile(plugin.players(), player);
        Territory territory = findNationTerritory(plugin, sender, args[0]);
        if (territory == null) {
            plugin.messages().error(sender, "Territoire introuvable : &e{name}", "name", args[0]);
            return;
        }
        AsyncFeedback.whenComplete(plugin, plugin.messages(), sender,
                plugin.territories().unclaim(profile, territory.id()),
                () -> plugin.messages().send(sender, "&aTerritoire &e{name} &aabandonne.",
                        "name", territory.name()));
    }

    private static void permission(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        CommandSupport.requireArgs(args, 3,
                "/territory perm <territoire> <permission> <NATION|ALLY|PUBLIC|NONE>");
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile profile = CommandSupport.requireProfile(plugin.players(), player);
        Territory territory = findNationTerritory(plugin, sender, args[0]);
        if (territory == null) {
            plugin.messages().error(sender, "Territoire introuvable : &e{name}", "name", args[0]);
            return;
        }
        TerritoryPermission permission;
        try {
            permission = TerritoryPermission.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.messages().error(sender,
                    "Permission inconnue : &e{permission}&c. Valeurs : BUILD, BREAK, CONTAINER, "
                            + "INTERACT, PVP, ENTER", "permission", args[1]);
            return;
        }
        AccessLevel level;
        try {
            level = AccessLevel.parse(args[2]);
        } catch (IllegalArgumentException ex) {
            plugin.messages().error(sender, ex.getMessage());
            return;
        }
        AsyncFeedback.when(plugin, plugin.messages(), sender,
                plugin.territories().setPermission(profile, territory.id(), permission, level),
                updated -> plugin.messages().send(sender,
                        "&aPermission &e{permission} &ade &f{name} &afixee a &e{level}",
                        "permission", permission.name(), "name", updated.name(),
                        "level", level.name()));
    }

    private static void map(LesyriaPlugin plugin, CommandSender sender) {
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile profile = CommandSupport.requireProfile(plugin.players(), player);
        var location = player.getLocation();
        String world = location.getWorld().getName();
        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;
        int radius = 6;

        plugin.messages().sendRaw(sender, "&7Carte des territoires (&e✚ &7= vous, &a█ &7= votre nation, "
                + "(x) &7= autre nation, &f. &7= sauvage)");
        StringBuilder legend = new StringBuilder("   ");
        for (int x = -radius; x <= radius; x++) {
            legend.append(x == 0 ? "&f|" : "&7|");
        }
        plugin.messages().sendRaw(sender, legend.toString());

        for (int z = -radius; z <= radius; z++) {
            StringBuilder row = new StringBuilder();
            for (int x = -radius; x <= radius; x++) {
                Territory territory = plugin.territories().territoryAt(world, centerX + x, centerZ + z);
                if (x == 0 && z == 0) {
                    row.append("&e✚");
                } else if (territory == null) {
                    row.append("&f.");
                } else if (profile.hasNation() && territory.nationId() == profile.nationId()) {
                    row.append("&a█");
                } else {
                    row.append("&c(x)");
                }
            }
            plugin.messages().sendRaw(sender, row.toString());
        }
    }

    private static Territory findNationTerritory(LesyriaPlugin plugin, CommandSender sender,
                                                 String name) {
        Player player = sender instanceof Player p ? p : null;
        if (player == null) {
            return null;
        }
        NationMember membership = plugin.nations().membershipOf(player.getUniqueId());
        if (membership == null) {
            return null;
        }
        for (Territory territory : plugin.territories().territoriesOf(membership.nationId())) {
            if (territory.name().equalsIgnoreCase(name)) {
                return territory;
            }
        }
        return null;
    }

    private static List<String> territoryNames(LesyriaPlugin plugin, CommandSender sender,
                                               String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        NationMember membership = plugin.nations().membershipOf(player.getUniqueId());
        if (membership == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        String prefix = CommandSupport.lastArgument(args).toLowerCase(Locale.ROOT);
        for (Territory territory : plugin.territories().territoriesOf(membership.nationId())) {
            if (territory.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(territory.name());
            }
        }
        return names;
    }
}
