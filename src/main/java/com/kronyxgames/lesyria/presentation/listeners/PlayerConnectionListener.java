package com.kronyxgames.lesyria.presentation.listeners;

import com.kronyxgames.lesyria.LesyriaPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

/**
 * Connexion et deconnexion des joueurs.
 *
 * <p>A la connexion : chargement (ou creation) du profil, attribution du solde
 * initial, messages d'accueil. A la deconnexion : ecriture des statistiques
 * accumulees puis liberation du cache.</p>
 *
 * <p>Le chargement est asynchrone ; la connexion du joueur n'est jamais bloquee
 * par une requete SQL.</p>
 */
public final class PlayerConnectionListener implements Listener {

    private final LesyriaPlugin plugin;

    public PlayerConnectionListener(LesyriaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Les messages de connexion sont geres par la configuration : on coupe
        // celui de Bukkit pour eviter les doublons.
        event.joinMessage(null);
        broadcast(plugin.messages().joinLines(), player);
        plugin.getLogger().info("Connexion: " + player.getName() + " (" + player.getUniqueId() + ")");

        plugin.players().loadOrCreate(player.getUniqueId(), player.getName())
                .whenComplete((result, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.SEVERE,
                                "chargement du profil impossible pour " + player.getName(), error);
                        plugin.messages().error(player,
                                "Votre profil n'a pas pu etre charge. Reconnectez-vous ; "
                                        + "si le probleme persiste, contactez un administrateur.");
                        return;
                    }
                    if (!player.isOnline()) {
                        return;
                    }
                    if (result.created()) {
                        plugin.messages().sendAll(player, plugin.messages().firstJoinLines(), true,
                                "player", player.getName(),
                                "balance", Long.toString(result.profile().balance()),
                                "currency", plugin.messages().currencySymbol());
                        plugin.getLogger().info("Premiere connexion: " + player.getName());
                    } else {
                        plugin.messages().send(player, "&7Bon retour, &f{player}&7. Solde : &e{balance}",
                                "player", player.getName(),
                                "balance", plugin.messages().money(result.profile().balance()));
                    }
                }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(null);
        broadcast(plugin.messages().quitLines(), player);
        plugin.getLogger().info("Deconnexion: " + player.getName());

        // Ecriture des statistiques avant de liberer le cache : le flush lit les
        // profils en memoire, l'ordre est donc important.
        plugin.players().flushStatistics().whenComplete((ignored, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING,
                        "statistiques de " + player.getName() + " non ecrites", error);
            }
            plugin.players().cache().remove(player.getUniqueId());
        });
    }

    private void broadcast(java.util.List<String> lines, Player player) {
        for (String line : lines) {
            Component message = plugin.messages().component(line, "player", player.getName());
            plugin.getServer().sendMessage(message);
        }
    }
}
