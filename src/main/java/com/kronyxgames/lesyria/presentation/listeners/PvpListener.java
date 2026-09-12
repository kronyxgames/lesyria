package com.kronyxgames.lesyria.presentation.listeners;

import com.kronyxgames.lesyria.LesyriaPlugin;
import com.kronyxgames.lesyria.application.combat.PvpDecision;
import com.kronyxgames.lesyria.domain.territory.Territory;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applique la politique PvP et enregistre les kills.
 *
 * <p>La decision vient de {@link com.kronyxgames.lesyria.application.combat.PvpPolicy} :
 * le listener ne contient aucune regle, il se contente de la brancher sur les
 * evenements Bukkit (corps a corps et projectiles).</p>
 */
public final class PvpListener implements Listener {

    private static final long MESSAGE_INTERVAL_MILLIS = 2500L;

    private final LesyriaPlugin plugin;
    private final Map<UUID, Long> lastDeniedMessage = new ConcurrentHashMap<>();

    public PvpListener(LesyriaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        Location location = victim.getLocation();
        Territory territory = plugin.territories().territoryAt(location.getWorld().getName(),
                location.getBlockX() >> 4, location.getBlockZ() >> 4);

        PvpDecision decision = plugin.pvp().evaluate(attacker.getUniqueId(), victim.getUniqueId(),
                territory);
        if (decision.allowed()) {
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long previous = lastDeniedMessage.get(attacker.getUniqueId());
        if (previous == null || now - previous >= MESSAGE_INTERVAL_MILLIS) {
            lastDeniedMessage.put(attacker.getUniqueId(), now);
            plugin.messages().error(attacker, decision.reason());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        plugin.players().recordPlayerKill(killer.getUniqueId(), victim.getUniqueId());
        plugin.getLogger().info("PvP: " + killer.getName() + " a tue " + victim.getName());
    }

    private Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
