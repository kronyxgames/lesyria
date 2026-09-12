package com.kronyxgames.lesyria.presentation.listeners;

import com.kronyxgames.lesyria.LesyriaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Comptabilise l'activite des joueurs (statistiques).
 *
 * <p>Les compteurs sont incrementes en memoire puis ecrits periodiquement par
 * {@link com.kronyxgames.lesyria.application.player.PlayerService#flushStatistics()} :
 * une requete SQL par bloc casse serait intenable.</p>
 */
public final class PlayerActivityListener implements Listener {

    private final LesyriaPlugin plugin;

    public PlayerActivityListener(LesyriaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        plugin.players().recordBlockPlaced(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.players().recordBlockBroken(event.getPlayer().getUniqueId());
    }
}
