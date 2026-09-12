package com.kronyxgames.lesyria.presentation.commands;

import com.kronyxgames.lesyria.LesyriaPlugin;
import com.kronyxgames.lesyria.domain.player.PlayerHome;
import com.kronyxgames.lesyria.domain.player.PlayerProfile;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /sethome} et {@code /home}.
 *
 * <p>Une seule instance gere les deux commandes : le comportement est identique,
 * seul le sens change ({@code set = true} pour l'enregistrement).</p>
 */
public final class HomeCommand {

    private final LesyriaPlugin plugin;
    private final boolean setMode;

    private HomeCommand(LesyriaPlugin plugin, boolean setMode) {
        this.plugin = plugin;
        this.setMode = setMode;
    }

    /**
     * @param plugin plugin
     * @return le routeur de {@code /sethome}
     */
    public static CommandRouter createSetHome(LesyriaPlugin plugin) {
        return create(plugin, true);
    }

    /**
     * @param plugin plugin
     * @return le routeur de {@code /home}
     */
    public static CommandRouter createHome(LesyriaPlugin plugin) {
        return create(plugin, false);
    }

    private static CommandRouter create(LesyriaPlugin plugin, boolean setMode) {
        String root = setMode ? "sethome" : "home";
        CommandRouter router = new CommandRouter(plugin, plugin.messages(), root,
                "lesyria.command.home");
        HomeCommand command = new HomeCommand(plugin, setMode);
        router.sub("", "", setMode ? "Definir votre domicile" : "Se teleporter a votre domicile",
                true, command::execute);
        return router;
    }

    private void execute(CommandSender sender, String[] args) {
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile profile = CommandSupport.requireProfile(plugin.players(), player);
        if (setMode) {
            setHome(player);
        } else {
            goHome(player, profile);
        }
    }

    private void setHome(Player player) {
        Location location = player.getLocation();
        PlayerHome home = new PlayerHome(location.getWorld().getName(), location.getX(),
                location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        AsyncFeedback.whenComplete(plugin, plugin.messages(), player,
                plugin.players().saveHome(player.getUniqueId(), home),
                () -> plugin.messages().send(player,
                        "&aDomicile defini en &e{x} {y} {z} &adans &e{world}",
                        "x", Integer.toString(location.getBlockX()),
                        "y", Integer.toString(location.getBlockY()),
                        "z", Integer.toString(location.getBlockZ()),
                        "world", location.getWorld().getName()));
    }

    private void goHome(Player player, PlayerProfile profile) {
        if (!profile.hasHome()) {
            plugin.messages().error(player, "Vous n'avez pas de domicile. Utilisez &e/sethome&c.");
            return;
        }
        PlayerHome home = profile.home();
        World world = plugin.getServer().getWorld(home.world());
        if (world == null) {
            plugin.messages().error(player, "Le monde de votre domicile est introuvable.");
            return;
        }
        Location destination = new Location(world, home.x(), home.y(), home.z(), home.yaw(),
                home.pitch());
        plugin.teleports().teleport(player, destination, "votre domicile");
    }
}
