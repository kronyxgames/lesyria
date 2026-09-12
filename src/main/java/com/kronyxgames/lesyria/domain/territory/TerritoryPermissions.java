package com.kronyxgames.lesyria.domain.territory;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Ensemble immuable des permissions d'un territoire.
 *
 * <p>Une permission absente est traitee comme {@link AccessLevel#NONE} : le
 * comportement par defaut est toujours restrictif.</p>
 */
public final class TerritoryPermissions {

    private final Map<TerritoryPermission, AccessLevel> levels;

    private TerritoryPermissions(Map<TerritoryPermission, AccessLevel> levels) {
        this.levels = Collections.unmodifiableMap(levels);
    }

    /**
     * Construit un ensemble de permissions.
     *
     * @param levels niveaux par permission (les permissions absentes valent NONE)
     * @return les permissions
     */
    public static TerritoryPermissions of(Map<TerritoryPermission, AccessLevel> levels) {
        Objects.requireNonNull(levels, "levels");
        return new TerritoryPermissions(copyOf(levels));
    }

    /** @return un ensemble ou tout est interdit. */
    public static TerritoryPermissions none() {
        return new TerritoryPermissions(new EnumMap<>(TerritoryPermission.class));
    }

    /**
     * @param permission permission demandee
     * @return le niveau requis, jamais {@code null}
     */
    public AccessLevel level(TerritoryPermission permission) {
        Objects.requireNonNull(permission, "permission");
        return levels.getOrDefault(permission, AccessLevel.NONE);
    }

    /**
     * @param permission permission a modifier
     * @param level      nouveau niveau
     * @return un nouvel ensemble (immuabilite)
     */
    public TerritoryPermissions with(TerritoryPermission permission, AccessLevel level) {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(level, "level");
        Map<TerritoryPermission, AccessLevel> copy = copyOf(levels);
        copy.put(permission, level);
        return new TerritoryPermissions(copy);
    }

    /** @return une vue non modifiable des niveaux configures. */
    public Map<TerritoryPermission, AccessLevel> asMap() {
        return levels;
    }

    /**
     * Copie une table de niveaux.
     *
     * <p>Le constructeur {@code EnumMap(Map)} echoue si la source est vide car
     * il ne peut pas deduire le type d'enum : on passe donc explicitement la
     * classe de l'enum.</p>
     *
     * @param levels niveaux a copier
     * @return une copie modifiable
     */
    private static Map<TerritoryPermission, AccessLevel> copyOf(
            Map<TerritoryPermission, AccessLevel> levels) {
        Map<TerritoryPermission, AccessLevel> copy = new EnumMap<>(TerritoryPermission.class);
        copy.putAll(levels);
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TerritoryPermissions permissions && levels.equals(permissions.levels);
    }

    @Override
    public int hashCode() {
        return levels.hashCode();
    }

    @Override
    public String toString() {
        return "TerritoryPermissions" + levels;
    }
}
