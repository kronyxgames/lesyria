package com.kronyxgames.lesyria.domain.player;

import java.util.Objects;

/**
 * Domicile d'un joueur ({@code /sethome}, {@code /home}).
 *
 * @param world monde
 * @param x     abscisse
 * @param y     ordonnee
 * @param z     cote
 * @param yaw   orientation
 * @param pitch inclinaison
 */
public record PlayerHome(String world, double x, double y, double z, float yaw, float pitch) {

    public PlayerHome {
        Objects.requireNonNull(world, "world");
    }
}
