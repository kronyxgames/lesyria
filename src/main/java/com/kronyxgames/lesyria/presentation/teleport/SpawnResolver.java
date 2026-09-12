package com.kronyxgames.lesyria.presentation.teleport;

import com.kronyxgames.lesyria.domain.city.City;
import com.kronyxgames.lesyria.infrastructure.configuration.ConfigHolder;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

/**
 * Construit les points de spawn a partir de la configuration et des villes.
 *
 * <p>Aucune coordonnee de spawn n'est ecrite en dur dans le code : elles
 * viennent de {@code config.yml} (spawn principal) ou de la base de donnees
 * (spawns de villes).</p>
 *
 * <p>Le recalage au sol ({@code spawn.snap-to-ground}) evite qu'un joueur
 * apparaisse en l'air apres un changement de generation ou de hauteur de
 * terrain.</p>
 */
public final class SpawnResolver {

    private SpawnResolver() {
    }

    /**
     * @param server serveur
     * @param config configuration courante
     * @return le spawn principal, ou {@code null} si le monde est introuvable
     */
    public static Location resolveMainSpawn(Server server, ConfigHolder config) {
        var settings = config.spawn();
        World world = server.getWorld(settings.world());
        if (world == null) {
            return null;
        }
        double y = settings.y();
        if (settings.snapToGround()) {
            y = groundLevel(world, settings.x(), settings.z());
        }
        return new Location(world, settings.x(), y, settings.z(), settings.yaw(), settings.pitch());
    }

    /**
     * @param server serveur
     * @param city   ville
     * @return le spawn de la ville, ou {@code null} si le monde est introuvable
     */
    public static Location resolveCitySpawn(Server server, City city) {
        World world = server.getWorld(city.world());
        if (world == null) {
            return null;
        }
        return new Location(world, city.x(), city.y(), city.z(), city.yaw(), city.pitch());
    }

    /**
     * @param world monde
     * @param x     abscisse
     * @param z     cote
     * @return la premiere hauteur praticable a ces coordonnees
     */
    public static double groundLevel(World world, double x, double z) {
        int blockY = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));
        return blockY + 1.0d;
    }
}
