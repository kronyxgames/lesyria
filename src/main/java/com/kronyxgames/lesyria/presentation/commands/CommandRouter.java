package com.kronyxgames.lesyria.presentation.commands;

import com.kronyxgames.lesyria.domain.LesyriaException;
import com.kronyxgames.lesyria.presentation.messages.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Routeur de sous-commandes.
 *
 * <p>Chaque commande de Lesyria ({@code /nation}, {@code /territory}...) est un
 * {@code CommandRouter} alimente par des sous-commandes. Le routeur garantit a
 * chaque sous-commande :</p>
 * <ul>
 *   <li>la verification de permission ;</li>
 *   <li>la verification "commande reservee aux joueurs" ;</li>
 *   <li>la gestion uniforme des erreurs metier et techniques ;</li>
 *   <li>le tab-completion ;</li>
 *   <li>une aide generee automatiquement.</li>
 * </ul>
 */
public final class CommandRouter implements CommandExecutor, TabCompleter {

    /** Execution d'une sous-commande. */
    @FunctionalInterface
    public interface Handler {
        void execute(CommandSender sender, String[] args);
    }

    /** Completion d'une sous-commande. */
    @FunctionalInterface
    public interface Completer {
        List<String> complete(CommandSender sender, String[] args);
    }

    /**
     * Description d'une sous-commande.
     */
    public static final class Sub {

        private final String name;
        private final String usage;
        private final String description;
        private final String permission;
        private final boolean playerOnly;
        private final Handler handler;
        private Completer completer = (sender, args) -> List.of();

        private Sub(String name, String usage, String description, String permission,
                    boolean playerOnly, Handler handler) {
            this.name = name;
            this.usage = usage;
            this.description = description;
            this.permission = permission;
            this.playerOnly = playerOnly;
            this.handler = handler;
        }

        /**
         * @param completer fournisseur de suggestions
         * @return cette sous-commande (chaining)
         */
        public Sub completer(Completer completer) {
            this.completer = completer;
            return this;
        }
    }

    private final Plugin plugin;
    private final Messages messages;
    private final String root;
    private final String permissionRoot;
    private final Map<String, Sub> subs = new LinkedHashMap<>();

    /**
     * @param root           nom racine tel qu'ecrit par le joueur (sans le {@code /})
     * @param permissionRoot prefixe de permission par defaut ({@code lesyria.command.<root>})
     */
    public CommandRouter(Plugin plugin, Messages messages, String root, String permissionRoot) {
        this.plugin = plugin;
        this.messages = messages;
        this.root = root;
        this.permissionRoot = permissionRoot;
    }

    /**
     * Declare une sous-commande avec la permission par defaut.
     *
     * @param name        nom
     * @param usage       arguments, par exemple {@code <nom> <tag>}
     * @param description description affichee dans l'aide
     * @param playerOnly  vrai si la commande est reservee aux joueurs
     * @param handler     traitement
     * @return la sous-commande (pour chainer le tab-completion)
     */
    public Sub sub(String name, String usage, String description, boolean playerOnly,
                   Handler handler) {
        return sub(name, usage, description, permissionRoot, playerOnly, handler);
    }

    /**
     * Declare une sous-commande avec une permission explicite.
     *
     * @param name        nom
     * @param usage       arguments
     * @param description description
     * @param permission  permission Bukkit requise
     * @param playerOnly  vrai si reservee aux joueurs
     * @param handler     traitement
     * @return la sous-commande
     */
    public Sub sub(String name, String usage, String description, String permission,
                   boolean playerOnly, Handler handler) {
        Sub sub = new Sub(name, usage, description, permission, playerOnly, handler);
        subs.put(name.toLowerCase(Locale.ROOT), sub);
        return sub;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equals("?")) {
            sendHelp(sender);
            return true;
        }
        Sub sub = subs.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            messages.error(sender, "Sous-commande inconnue : &e{name}", "name", args[0]);
            sendHelp(sender);
            return true;
        }
        if (!sender.hasPermission(sub.permission)) {
            messages.error(sender, "Vous n'avez pas la permission d'utiliser &e/{root} {name}",
                    "root", root, "name", sub.name);
            return true;
        }
        if (sub.playerOnly && !(sender instanceof Player)) {
            messages.error(sender, "Cette commande doit etre executee par un joueur.");
            return true;
        }

        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        try {
            sub.handler.execute(sender, rest);
        } catch (LesyriaException business) {
            messages.error(sender, business.getMessage());
        } catch (RuntimeException technical) {
            plugin.getLogger().log(Level.SEVERE,
                    "erreur pendant /" + root + " " + sub.name, technical);
            messages.error(sender, "Une erreur interne est survenue. L'incident a ete journalise.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias,
                                      String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            for (Sub sub : subs.values()) {
                if (sender.hasPermission(sub.permission) && sub.name.startsWith(prefix)) {
                    suggestions.add(sub.name);
                }
            }
            return suggestions;
        }
        Sub sub = subs.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null || !sender.hasPermission(sub.permission)) {
            return suggestions;
        }
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        for (String suggestion : sub.completer.complete(sender, rest)) {
            if (suggestion.toLowerCase(Locale.ROOT).startsWith(rest[rest.length - 1].toLowerCase(Locale.ROOT))) {
                suggestions.add(suggestion);
            }
        }
        return suggestions;
    }

    private void sendHelp(CommandSender sender) {
        messages.sendRaw(sender, "");
        messages.sendRaw(sender, "&6&lLesyria &7- &f/{root}", "root", root);
        for (Sub sub : subs.values()) {
            if (!sender.hasPermission(sub.permission)) {
                continue;
            }
            String usage = sub.usage.isEmpty() ? "" : " " + sub.usage;
            messages.sendRaw(sender, "&e/{root} {name}{usage} &7- {description}",
                    "root", root, "name", sub.name, "usage", usage, "description", sub.description);
        }
        messages.sendRaw(sender, "");
    }

    /** @return le nom racine de la commande. */
    public String root() {
        return root;
    }
}
