package com.kronyxgames.lesyria.domain.territory;

import com.kronyxgames.lesyria.domain.nation.NationRelation;

/**
 * Niveau d'acces requis pour une {@link TerritoryPermission}.
 *
 * <p>L'ordre de restriction est volontairement lineaire :
 * {@code NONE} &lt; {@code NATION} &lt; {@code ALLY} &lt; {@code PUBLIC}.
 * Une permission accordee a un niveau donne est donc implicitement accordee
 * aux niveaux moins restrictifs.</p>
 */
public enum AccessLevel {

    /** Personne, sauf le chef de la nation proprietaire. */
    NONE(0),
    /** Membres de la nation proprietaire. */
    NATION(1),
    /** Membres de la nation proprietaire et de ses allies. */
    ALLY(2),
    /** Tout le monde. */
    PUBLIC(3);

    private final int weight;

    AccessLevel(int weight) {
        this.weight = weight;
    }

    /**
     * Indique si une relation donnee beneficie de ce niveau d'acces.
     *
     * @param relation relation entre le joueur et la nation proprietaire
     * @return vrai si l'action est autorisee
     */
    public boolean grants(NationRelation relation) {
        return switch (this) {
            case NONE -> relation == NationRelation.SELF;
            case NATION -> relation == NationRelation.SELF;
            case ALLY -> relation == NationRelation.SELF || relation == NationRelation.ALLY;
            case PUBLIC -> true;
        };
    }

    /**
     * Analyse une valeur de configuration.
     *
     * @param raw valeur brute (insensible a la casse)
     * @return le niveau correspondant
     * @throws IllegalArgumentException si la valeur est inconnue
     */
    public static AccessLevel parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("niveau d'acces manquant");
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "niveau d'acces inconnu: '" + raw + "' (attendu: NATION, ALLY, PUBLIC, NONE)");
        }
    }

    public int weight() {
        return weight;
    }
}
