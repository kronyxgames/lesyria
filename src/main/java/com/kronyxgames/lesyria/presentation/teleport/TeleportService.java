package com.kronyxgames.lesyria.presentation.teleport;

import com.kronyxgames.lesyria.infrastructure.configuration.ConfigHolder;
import com.kronyxgames.lesyria.presentation.messages.Messages;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Teleportation avec delai et temps de recharge.
 *
 * <p>Le delai evite de fuir un combat instantanement : si le joueur bouge ou
 * subit des degats, la teleportation est annulee. Le temps de recharge limite
 * les abus de commande.</p>
 */
public final class TeleportService implements Listener {

    private final Plugin plugin;
    private final ConfigHolder config;
    private final Messages messages;
    private final Map<UUID, BukkitTask> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Location> origins = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public TeleportService(Plugin plugin, ConfigHolder config, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
    }

    /**
     * Teleporte un joueur vers une destination, avec le delai configure.
     *
     * @param player      joueur
     * @param destination destination (monde resolu)
     * @param label       libelle affiche ("spawn", "Paris", "votre domicile"...)
     */
    public void teleport(Player player, Location destination, String label) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownUntil = cooldowns.getOrDefault(uuid, 0L);
        if (now < cooldownUntil) {
            long remaining = (cooldownUntil - now + 999) / 1000;
            messages.error(player, "Patientez encore &e{seconds}s &cavant de vous teleporter.",
                    "seconds", Long.toString(remaining));
            return;
        }

        cancelPending(player.getUniqueId());
        int delay = config.teleport().delaySeconds();
        if (delay <= 0) {
            complete(player, destination, label);
            return;
        }

        origins.put(uuid, player.getLocation().clone());
        messages.send(player, "&7Teleportation vers &e{label} &7dans &e{seconds}s&7. Ne bougez pas.",
                "label", label, "seconds", Integer.toString(delay));
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> complete(player, destination, label), delay * 20L);
        pending.put(uuid, task);
    }

    /**
     * @param uuid joueur
     * @return vrai si une teleportation est en attente
     */
    public boolean isPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    /** Annule toutes les teleportations en attente (arret du serveur). */
    public void cancelAll() {
        List<BukkitTask> tasks = new ArrayList<>(pending.values());
        pending.clear();
        origins.clear();
        tasks.forEach(BukkitTask::cancel);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Location origin = origins.get(uuid);
        if (origin == null) {
            return;
        }
        Location to = event.getTo();
        if (origin.getBlockX() != to.getBlockX()
                || origin.getBlockY() != to.getBlockY()
                || origin.getBlockZ() != to.getBlockZ()) {
            cancelPending(uuid);
            messages.error(event.getPlayer(), "Teleportation annulee : vous avez bouge.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isPending(player.getUniqueId())) {
            cancelPending(player.getUniqueId());
            messages.error(player, "Teleportation annulee : vous avez subi des degats.");
        }
    }

    private void cancelPending(UUID uuid) {
        BukkitTask task = pending.remove(uuid);
        origins.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void complete(Player player, Location destination, String label) {
        UUID uuid = player.getUniqueId();
        pending.remove(uuid);
        origins.remove(uuid);
        if (!player.isOnline()) {
            return;
        }
        player.teleportAsync(destination).thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) {
                cooldowns.put(uuid, System.currentTimeMillis()
                        + config.teleport().cooldownSeconds() * 1000L);
                messages.send(player, "&aVous etes arrive a &e{label}&a.", "label", label);
            } else {
                messages.error(player, "Teleportation impossible vers {label}.", "label", label);
            }
        });
    }
}
