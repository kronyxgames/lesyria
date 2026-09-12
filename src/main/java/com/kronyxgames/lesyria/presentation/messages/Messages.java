package com.kronyxgames.lesyria.presentation.messages;

import com.kronyxgames.lesyria.domain.LesyriaException;
import com.kronyxgames.lesyria.infrastructure.configuration.ConfigHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralise la mise en forme des messages joueur.
 *
 * <p>Trois regles pour garder une experience coherente :</p>
 * <ul>
 *   <li>le prefixe, les couleurs et les libelles de monnaie viennent de
 *       {@code config.yml} ;</li>
 *   <li>les messages sont ecrits en francais, sans jargon technique ;</li>
 *   <li>une erreur metier affiche son message, une erreur technique affiche un
 *       message generique et part dans les logs serveur.</li>
 * </ul>
 */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private final ConfigHolder config;
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.FRANCE);
    private final Logger logger;

    public Messages(ConfigHolder config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    /** @return le prefixe configurable. */
    public Component prefix() {
        return LEGACY.deserialize(config.messages().prefix());
    }

    /**
     * Construit un composant a partir d'un modele et de ses placeholders.
     *
     * @param template     modele avec codes couleur {@code &} et placeholders {@code {clef}}
     * @param placeholders paires clef/valeur
     * @return le composant pret a etre envoye
     */
    public Component component(String template, String... placeholders) {
        return LEGACY.deserialize(applyPlaceholders(template, placeholders));
    }

    /**
     * Envoie un message prefixe.
     *
     * @param sender       destinataire
     * @param template     modele
     * @param placeholders paires clef/valeur
     */
    public void send(CommandSender sender, String template, String... placeholders) {
        sender.sendMessage(prefix().append(component(template, placeholders)));
    }

    /**
     * Envoie un message sans prefixe (listes, aide, tableaux).
     *
     * @param sender       destinataire
     * @param template     modele
     * @param placeholders paires clef/valeur
     */
    public void sendRaw(CommandSender sender, String template, String... placeholders) {
        sender.sendMessage(component(template, placeholders));
    }

    /**
     * Envoie plusieurs lignes, avec ou sans prefixe.
     *
     * @param sender       destinataire
     * @param lines        lignes
     * @param withPrefix   vrai pour prefixer chaque ligne
     * @param placeholders paires clef/valeur
     */
    public void sendAll(CommandSender sender, List<String> lines, boolean withPrefix,
                        String... placeholders) {
        for (String line : lines) {
            if (withPrefix) {
                sender.sendMessage(prefix().append(component(line, placeholders)));
            } else {
                sender.sendMessage(component(line, placeholders));
            }
        }
    }

    /**
     * Envoie un message d'erreur (rouge si aucune couleur n'est definie).
     *
     * @param sender       destinataire
     * @param template     modele
     * @param placeholders paires clef/valeur
     */
    public void error(CommandSender sender, String template, String... placeholders) {
        sender.sendMessage(prefix()
                .append(component(template, placeholders).colorIfAbsent(NamedTextColor.RED)));
    }

    /**
     * Envoie l'erreur correspondant a un echec d'operation asynchrone.
     *
     * @param sender destinataire
     * @param error  erreur remontee par le futur
     */
    public void error(CommandSender sender, Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof LesyriaException business) {
            error(sender, business.getMessage());
            return;
        }
        logger.log(Level.SEVERE, "erreur technique pendant une commande", cause);
        error(sender, "Une erreur interne est survenue. L'incident a ete journalise.");
    }

    /**
     * @param error erreur eventuellement enveloppee par un futur
     * @return la cause racine exploitable
     */
    public static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * @param amount montant en Lesyria Coins
     * @return le montant formate, suivi du symbole monetaire
     */
    public String money(long amount) {
        return amount(amount) + " " + currencySymbol();
    }

    /**
     * @param amount montant
     * @return le nombre formate (separateur de milliers francais)
     */
    public String amount(long amount) {
        return numberFormat.format(amount);
    }

    /** @return le nom complet de la monnaie. */
    public String currencyName() {
        return config.economy().currencyName();
    }

    /** @return le symbole de la monnaie. */
    public String currencySymbol() {
        return config.economy().currencySymbol();
    }

    /** @return les lignes de bienvenue de premiere connexion. */
    public List<String> firstJoinLines() {
        return config.messages().firstJoin();
    }

    /** @return les lignes de connexion. */
    public List<String> joinLines() {
        return config.messages().join();
    }

    /** @return les lignes de deconnexion. */
    public List<String> quitLines() {
        return config.messages().quit();
    }

    private static String applyPlaceholders(String template, String... placeholders) {
        if (template == null) {
            return "";
        }
        if (placeholders.length == 0) {
            return template;
        }
        String result = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace('{' + placeholders[i] + '}', placeholders[i + 1]);
        }
        return result;
    }
}
