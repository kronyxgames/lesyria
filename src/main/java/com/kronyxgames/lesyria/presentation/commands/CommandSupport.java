package com.kronyxgames.lesyria.presentation.commands;

import com.kronyxgames.lesyria.application.player.PlayerService;
import com.kronyxgames.lesyria.domain.LesyriaException;
import com.kronyxgames.lesyria.domain.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Aides partagees par les commandes : profils, parsing, suggestions.
 */
public final class CommandSupport {

    private CommandSupport() {
    }

    /**
     * @param sender expediteur
     * @return le joueur correspondant
     * @throws LesyriaException si l'expediteur n'est pas un joueur
     */
    public static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        throw new LesyriaException("Cette commande doit etre executee par un joueur.");
    }

    /**
     * @param players service joueurs
     * @param player  joueur
     * @return le profil charge en memoire
     * @throws LesyriaException si le profil n'est pas encore pret
     */
    public static PlayerProfile requireProfile(PlayerService players, Player player) {
        PlayerProfile profile = players.cached(player.getUniqueId());
        if (profile == null) {
            throw new LesyriaException(
                    "Votre profil est en cours de chargement. Reessayez dans un instant.");
        }
        return profile;
    }

    /**
     * Resout un profil joueur par pseudo (memoire d'abord, base ensuite).
     *
     * @param players  service joueurs
     * @param username pseudo
     * @return le futur du profil
     */
    public static java.util.concurrent.CompletableFuture<PlayerProfile> resolveProfile(
            PlayerService players, String username) {
        PlayerProfile cached = players.cachedByName(username);
        if (cached != null) {
            return java.util.concurrent.CompletableFuture.completedFuture(cached);
        }
        return players.findByUsername(username).thenApply(optional -> optional.orElseThrow(
                () -> new LesyriaException("Joueur inconnu : &e" + username)));
    }

    /**
     * Analyse un montant saisi par un joueur.
     *
     * @param raw      saisie ("150", "2k", "1M")
     * @param maximum  plafond autorise
     * @param messages messages
     * @return le montant
     * @throws LesyriaException si la saisie est invalide
     */
    public static long parseAmount(String raw, long maximum) {
        if (raw == null || raw.isBlank()) {
            throw new LesyriaException("Indiquez un montant, par exemple &e100");
        }
        String value = raw.trim().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        long multiplier = 1L;
        if (value.endsWith("k")) {
            multiplier = 1_000L;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("m")) {
            multiplier = 1_000_000L;
            value = value.substring(0, value.length() - 1);
        }
        long amount;
        try {
            amount = Math.multiplyExact(Long.parseLong(value), multiplier);
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new LesyriaException("Montant invalide : &e" + raw + "&c (exemples : 100, 5k, 2m)");
        }
        if (amount <= 0) {
            throw new LesyriaException("Le montant doit etre strictement positif.");
        }
        if (amount > maximum) {
            throw new LesyriaException("Le montant ne peut pas depasser &e" + maximum + "&c.");
        }
        return amount;
    }

    /**
     * @param raw valeur brute
     * @param size attendu
     * @param usage usage attendu
     * @param args  arguments recus
     * @throws LesyriaException si le nombre d'arguments est incorrect
     */
    public static void requireArgs(String[] args, int size, String usage) {
        if (args.length < size) {
            throw new LesyriaException("Usage : &e" + usage);
        }
    }

    /**
     * @param raw saisie
     * @return l'entier
     * @throws LesyriaException si la saisie n'est pas un entier
     */
    public static int parseInteger(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new LesyriaException("Nombre invalide : &e" + raw);
        }
    }

    /**
     * @return les pseudos des joueurs connectes
     */
    public static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
        return names;
    }

    /**
     * @param args arguments recus
     * @return le dernier argument saisi (celui en cours de completion)
     */
    public static String lastArgument(String[] args) {
        return args.length == 0 ? "" : args[args.length - 1];
    }

    /**
     * @param raw nom
     * @return vrai si le nom peut etre utilise pour une nation, une ville ou un territoire
     */
    public static boolean isValidName(String raw) {
        return raw != null && raw.matches("[A-Za-z0-9 _-]+");
    }
}
