package com.kronyxgames.lesyria.presentation.commands;

import com.kronyxgames.lesyria.presentation.messages.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Passerelle entre les operations asynchrones et l'API Bukkit.
 *
 * <p>Aucune requete SQL ne tourne sur le thread principal, et aucune API Bukkit
 * ne doit etre appelee depuis un thread de base de donnees. Cette classe fait le
 * lien : elle revient sur le thread principal avant d'ecrire le resultat au
 * joueur.</p>
 */
public final class AsyncFeedback {

    private AsyncFeedback() {
    }

    /**
     * Execute {@code onSuccess} sur le thread principal apres la reussite du futur.
     *
     * @param plugin    plugin (planificateur)
     * @param messages  messages
     * @param sender    destinataire du retour
     * @param future    operation asynchrone
     * @param onSuccess traitement du resultat (thread principal)
     * @param <T>       type du resultat
     */
    public static <T> void when(Plugin plugin, Messages messages, CommandSender sender,
                                CompletableFuture<T> future, Consumer<T> onSuccess) {
        future.whenComplete((value, error) -> dispatch(plugin, messages, sender, error, value, onSuccess));
    }

    /**
     * Execute {@code onSuccess} apres succes, sans traiter le resultat.
     *
     * @param plugin    plugin
     * @param messages  messages
     * @param sender    destinataire
     * @param future    operation asynchrone
     * @param onSuccess traitement (thread principal)
     */
    public static void whenComplete(Plugin plugin, Messages messages, CommandSender sender,
                                    CompletableFuture<Void> future, Runnable onSuccess) {
        future.whenComplete((value, error) -> dispatch(plugin, messages, sender, error, value,
                ignored -> onSuccess.run()));
    }

    private static <T> void dispatch(Plugin plugin, Messages messages, CommandSender sender,
                                     Throwable error, T value, Consumer<T> onSuccess) {
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    messages.error(sender, error);
                    return;
                }
                onSuccess.accept(value);
            });
        } catch (RuntimeException ex) {
            // Le serveur s'arrete : plus aucun retour joueur n'est possible.
            plugin.getLogger().log(Level.WARNING, "retour de commande impossible (arret du serveur ?)", ex);
        }
    }
}
