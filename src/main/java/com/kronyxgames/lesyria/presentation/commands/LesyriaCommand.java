package com.kronyxgames.lesyria.presentation.commands;

import com.kronyxgames.lesyria.LesyriaPlugin;
import org.bukkit.command.CommandSender;

/**
 * Commande principale : {@code /lesyria version}, {@code /lesyria reload}.
 */
public final class LesyriaCommand {

    private LesyriaCommand() {
    }

    /**
     * @param plugin plugin (racine de composition)
     * @return le routeur de la commande
     */
    public static CommandRouter create(LesyriaPlugin plugin) {
        CommandRouter router = new CommandRouter(plugin, plugin.messages(), "lesyria",
                "lesyria.command.help");

        router.sub("version", "", "Afficher la version et l'etat du serveur", "lesyria.command.help",
                false, (sender, args) -> version(plugin, sender));

        router.sub("reload", "", "Recharger la configuration", "lesyria.command.reload", false,
                (sender, args) -> reload(plugin, sender));

        return router;
    }

    private static void version(LesyriaPlugin plugin, CommandSender sender) {
        var messages = plugin.messages();
        messages.sendRaw(sender, "");
        messages.sendRaw(sender, "&6&lLesyria &7- version &e{version}",
                "version", plugin.getPluginMeta().getVersion());
        messages.sendRaw(sender, "&7Realm: &f{label}",
                "label", plugin.config().server().versionLabel());
        messages.sendRaw(sender, "&7Serveur: &f{server}", "server", plugin.getServer().getVersion());
        messages.sendRaw(sender, "&7Base de donnees: {state}", "state",
                plugin.database().isHealthy() ? "&aconnectee" : "&cindisponible");
        messages.sendRaw(sender, "&7Joueurs charges: &f{count}",
                "count", Integer.toString(plugin.players().cache().size()));
        messages.sendRaw(sender, "&7Nations: &f{nations} &7| Territoires: &f{territories}"
                        + " &7| Chunks revendiques: &f{chunks} &7| Villes: &f{cities}",
                "nations", Integer.toString(plugin.nations().cache().nationCount()),
                "territories", Integer.toString(plugin.territories().index().size()),
                "chunks", Integer.toString(plugin.territories().index().chunkCount()),
                "cities", Integer.toString(plugin.cities().cache().size()));
        messages.sendRaw(sender, "&7Monnaie: &f{currency} &7| Spawn: &f{spawn}",
                "currency", messages.currencyName(),
                "spawn", plugin.config().spawn().world() + " "
                        + plugin.config().spawn().x() + " " + plugin.config().spawn().z());
        messages.sendRaw(sender, "");
    }

    private static void reload(LesyriaPlugin plugin, CommandSender sender) {
        var messages = plugin.messages();
        if (plugin.reloadLesyriaConfiguration()) {
            messages.send(sender, "&aConfiguration rechargee.");
            messages.sendRaw(sender, "&7Note : les reglages de base de donnees et d'API HTTP "
                    + "necessitent un redemarrage du serveur.");
        } else {
            messages.error(sender, "Configuration invalide. Consultez les logs du serveur.");
        }
    }
}
