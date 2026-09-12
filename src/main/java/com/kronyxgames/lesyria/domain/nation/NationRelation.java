package com.kronyxgames.lesyria.domain.nation;

/**
 * Relation d'un joueur avec une nation donnee.
 *
 * <p>Utilisee par la resolution des permissions territoriales : elle ne depend
 * pas du type de relation diplomatique exact, mais de sa "famille" (soi-meme,
 * allie, autre). Cela permet d'ajouter plus tard
 * {@code TRADE}, {@code NON_AGGRESSION}, {@code VASSAL}... sans toucher aux
 * permissions.</p>
 */
public enum NationRelation {

    /** Le joueur est membre de la nation. */
    SELF,
    /** Le joueur appartient a une nation alliee. */
    ALLY,
    /** Tout autre cas (neutre, en guerre, sans nation). */
    OTHER
}
