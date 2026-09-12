package com.kronyxgames.lesyria.domain.territory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Territoire revendique par une nation.
 *
 * <p>Un territoire est un <strong>rectangle de chunks</strong> dans un monde.
 * Ce choix (plutot qu'un ensemble arbitraire de chunks) garde la carte lisible,
 * bornable en memoire et facile a afficher en jeu, tout en restant suffisant
 * pour le gameplay territorial de la beta.</p>
 *
 * <p>Le lien {@code chunk -> territoire -> ville -> nation} est resolu ainsi :
 * l'index territorial donne le territoire d'un chunk, le territoire porte sa
 * nation, et une ville reference le territoire ou elle a ete fondee.</p>
 *
 * @param id          identifiant en base ({@code null} tant que non persiste)
 * @param name        nom unique du territoire
 * @param nationId    nation proprietaire
 * @param ownerUuid   joueur ayant revendique ({@code null} si supprime)
 * @param world       nom du monde
 * @param minChunkX   chunk X minimum inclus
 * @param minChunkZ   chunk Z minimum inclus
 * @param maxChunkX   chunk X maximum inclus
 * @param maxChunkZ   chunk Z maximum inclus
 * @param status      statut courant
 * @param permissions permissions du territoire
 * @param createdAt   date de creation
 */
public record Territory(
        Long id,
        String name,
        long nationId,
        UUID ownerUuid,
        String world,
        int minChunkX,
        int minChunkZ,
        int maxChunkX,
        int maxChunkZ,
        TerritoryStatus status,
        TerritoryPermissions permissions,
        Instant createdAt) {

    public Territory {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(createdAt, "createdAt");
        if (name.isBlank()) {
            throw new IllegalArgumentException("le nom du territoire ne peut pas etre vide");
        }
        if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
            throw new IllegalArgumentException("rectangle de chunks invalide");
        }
    }

    /**
     * Cree un territoire carre centre sur un chunk.
     *
     * @param name        nom du territoire
     * @param nationId    nation proprietaire
     * @param ownerUuid   joueur a l'origine de la revendication
     * @param world       monde
     * @param centerX     chunk X central
     * @param centerZ     chunk Z central
     * @param radius      rayon en chunks
     * @param permissions permissions initiales
     * @param now         horodatage de creation
     * @return le territoire non persiste
     */
    public static Territory claim(String name, long nationId, UUID ownerUuid, String world,
                                  int centerX, int centerZ, int radius,
                                  TerritoryPermissions permissions, Instant now) {
        if (radius < 0) {
            throw new IllegalArgumentException("le rayon de revendication ne peut pas etre negatif");
        }
        return new Territory(null, name, nationId, ownerUuid, world,
                centerX - radius, centerZ - radius,
                centerX + radius, centerZ + radius,
                TerritoryStatus.ACTIVE, permissions, now);
    }

    /** @return le nombre de chunks couverts par le territoire. */
    public int chunkCount() {
        return (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
    }

    /**
     * @param world nom du monde
     * @param x     chunk X
     * @param z     chunk Z
     * @return vrai si le chunk appartient a ce territoire
     */
    public boolean contains(String world, int x, int z) {
        return this.world.equals(world)
                && x >= minChunkX && x <= maxChunkX
                && z >= minChunkZ && z <= maxChunkZ;
    }

    /**
     * @param key cle de chunk
     * @return vrai si le chunk appartient a ce territoire
     */
    public boolean contains(ChunkKey key) {
        return contains(key.world(), key.x(), key.z());
    }

    /**
     * @param other autre territoire
     * @return vrai si les deux rectangles se chevauchent dans le meme monde
     */
    public boolean overlaps(Territory other) {
        return world.equals(other.world)
                && minChunkX <= other.maxChunkX && maxChunkX >= other.minChunkX
                && minChunkZ <= other.maxChunkZ && maxChunkZ >= other.minChunkZ;
    }

    public Territory withId(Long newId) {
        return new Territory(newId, name, nationId, ownerUuid, world, minChunkX, minChunkZ,
                maxChunkX, maxChunkZ, status, permissions, createdAt);
    }

    public Territory withStatus(TerritoryStatus newStatus) {
        return new Territory(id, name, nationId, ownerUuid, world, minChunkX, minChunkZ,
                maxChunkX, maxChunkZ, newStatus, permissions, createdAt);
    }

    public Territory withPermissions(TerritoryPermissions newPermissions) {
        return new Territory(id, name, nationId, ownerUuid, world, minChunkX, minChunkZ,
                maxChunkX, maxChunkZ, status, newPermissions, createdAt);
    }

    /** @return description courte pour l'affichage en jeu. */
    public String describeArea() {
        return "(" + minChunkX + ";" + minChunkZ + ") -> (" + maxChunkX + ";" + maxChunkZ + ")";
    }
}
