package com.kronyxgames.lesyria.domain.territory;

import java.util.Objects;

/**
 * Cle d'un chunk dans un monde donne.
 *
 * <p>Cette cle est l'unite de base de la carte territoriale : l'index
 * territorial associe un {@code ChunkKey} a un territoire, ce qui permet des
 * recherches en O(1) lors des evenements Bukkit.</p>
 *
 * @param world nom du monde
 * @param x     coordonnee X du chunk
 * @param z     coordonnee Z du chunk
 */
public record ChunkKey(String world, int x, int z) {

    public ChunkKey {
        Objects.requireNonNull(world, "world");
    }

    /** @return l'unite territoriale sous forme lisible ({@code world:x:z}). */
    public String asString() {
        return world + ':' + x + ':' + z;
    }
}
