package com.kronyxgames.lesyria.presentation.listeners;

import com.kronyxgames.lesyria.LesyriaPlugin;
import com.kronyxgames.lesyria.domain.territory.Territory;
import com.kronyxgames.lesyria.domain.territory.TerritoryPermission;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applique les permissions territoriales aux evenements de jeu.
 *
 * <p>Chemin critique : chaque bloc casse, pose ou interaction passe par ici.
 * Les decisions se prennent donc uniquement sur l'index territorial en memoire
 * (aucune requete SQL) et les messages de refus sont limites dans le temps pour
 * ne pas inonder le joueur.</p>
 *
 * <p>La nation du joueur est lue dans le cache politique, toujours disponible :
 * la protection reste donc active meme dans la seconde qui suit sa connexion.</p>
 */
public final class TerritoryProtectionListener implements Listener {

    /** Blocs consideres comme des conteneurs (permission CONTAINER). */
    private static final Set<Material> CONTAINERS = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.FURNACE,
            Material.BLAST_FURNACE, Material.SMOKER, Material.HOPPER, Material.DISPENSER,
            Material.DROPPER, Material.BREWING_STAND, Material.DECORATED_POT,
            Material.CHISELED_BOOKSHELF, Material.LECTERN, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.SHULKER_BOX, Material.WHITE_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX, Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX);

    private static final long MESSAGE_INTERVAL_MILLIS = 2500L;

    private final LesyriaPlugin plugin;
    private final Map<UUID, Long> lastDeniedMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastVisitedTerritory = new ConcurrentHashMap<>();

    public TerritoryProtectionListener(LesyriaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getLocation(), TerritoryPermission.BREAK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getLocation(), TerritoryPermission.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        TerritoryPermission permission = CONTAINERS.contains(event.getClickedBlock().getType())
                ? TerritoryPermission.CONTAINER
                : TerritoryPermission.INTERACT;
        if (!allowed(event.getPlayer(), event.getClickedBlock().getLocation(), permission)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getLocation(), TerritoryPermission.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getLocation(), TerritoryPermission.BREAK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        // Une explosion ne detruit pas un territoire protege : on retire les
        // blocs concernes de la liste plutot que d'annuler toute l'explosion
        // (les degats aux entites restent, les degats aux constructions non).
        event.blockList().removeIf(block -> {
            Territory territory = territoryAt(block.getLocation());
            return territory != null && !plugin.territories()
                    .isAllowed(null, territory, TerritoryPermission.BREAK);
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if ((from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return;
        }
        Player player = event.getPlayer();
        Territory territory = territoryAt(to);
        if (territory == null) {
            lastVisitedTerritory.remove(player.getUniqueId());
            return;
        }
        if (!plugin.territories().isAllowed(player.getUniqueId(), territory,
                TerritoryPermission.ENTER)) {
            event.setTo(from);
            deny(player, "Vous ne pouvez pas entrer sur le territoire &e" + territory.name() + "&c.");
            return;
        }
        announce(player, territory);
    }

    private boolean allowed(Player player, Location location, TerritoryPermission permission) {
        Territory territory = territoryAt(location);
        if (territory == null) {
            return true;
        }
        if (plugin.territories().isAllowed(player.getUniqueId(), territory, permission)) {
            return true;
        }
        deny(player, "Vous n'avez pas la permission &e" + permission.name()
                + "&c sur le territoire &e" + territory.name() + "&c.");
        return false;
    }

    private Territory territoryAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return plugin.territories().territoryAt(location.getWorld().getName(),
                location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private void deny(Player player, String message) {
        long now = System.currentTimeMillis();
        Long previous = lastDeniedMessage.get(player.getUniqueId());
        if (previous != null && now - previous < MESSAGE_INTERVAL_MILLIS) {
            return;
        }
        lastDeniedMessage.put(player.getUniqueId(), now);
        plugin.messages().error(player, message);
    }

    private void announce(Player player, Territory territory) {
        Long previous = lastVisitedTerritory.get(player.getUniqueId());
        if (previous != null && previous == territory.id()) {
            return;
        }
        lastVisitedTerritory.put(player.getUniqueId(), territory.id());
        var nation = plugin.nations().cached(territory.nationId());
        plugin.messages().send(player, "&7Vous entrez sur le territoire &e{name} {nation}",
                "name", territory.name(),
                "nation", nation == null ? "" : nation.displayTag());
    }
}
