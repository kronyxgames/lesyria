package com.kronyxgames.lesyria.domain.city;

import java.time.Instant;
import java.util.Objects;

/**
 * Ville fondee par une nation dans un de ses territoires.
 *
 * <p>Une ville fournit un point de spawn nomme ({@code /spawn <ville>},
 * {@code /city spawn}) et sert de capitale via
 * {@link com.kronyxgames.lesyria.domain.nation.Nation#capitalCityId()}.</p>
 *
 * @param id          identifiant en base
 * @param nationId    nation proprietaire
 * @param territoryId territoire ou la ville est fondee ({@code null} si le
 *                    territoire a ete supprime)
 * @param name        nom unique de la ville
 * @param world       monde
 * @param x           abscisse du centre
 * @param y           ordonnee du centre
 * @param z           cote du centre
 * @param yaw         orientation du spawn
 * @param pitch       inclinaison du spawn
 * @param createdAt   date de fondation
 */
public record City(
        Long id,
        long nationId,
        Long territoryId,
        String name,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        Instant createdAt) {

    public City {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(createdAt, "createdAt");
        if (name.isBlank()) {
            throw new IllegalArgumentException("le nom de la ville ne peut pas etre vide");
        }
    }

    public City withId(Long newId) {
        return new City(newId, nationId, territoryId, name, world, x, y, z, yaw, pitch, createdAt);
    }
}
