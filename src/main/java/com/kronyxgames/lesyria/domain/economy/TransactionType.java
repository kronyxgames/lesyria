package com.kronyxgames.lesyria.domain.economy;

/**
 * Nature d'une operation economique.
 *
 * <p>Chaque mouvement de monnaie est journalise avec son type : c'est ce qui
 * rend l'economie auditable (qui a paye quoi, quand, et pourquoi).</p>
 */
public enum TransactionType {

    /** Depot d'argent systeme vers un compte (recompense, vente au marche...). */
    DEPOSIT,
    /** Retrait d'argent depuis un compte. */
    WITHDRAW,
    /** Virement entre deux comptes persistants. */
    TRANSFER,
    /** Paiement d'un joueur a un autre joueur. */
    PLAYER_PAYMENT,
    /** Attribution administrative ({@code /eco give}). */
    ADMIN_GIVE,
    /** Retrait administratif ({@code /eco take}). */
    ADMIN_TAKE,
    /** Fixation administrative du solde ({@code /eco set}). */
    ADMIN_SET,
    /** Revendication d'un territoire. */
    TERRITORY_CLAIM,
    /** Fondation d'une nation. */
    NATION_CREATE,
    /** Fondation d'une ville. */
    CITY_CREATE,
    /** Depot dans la tresorerie d'une nation. */
    NATION_DEPOSIT,
    /** Retrait depuis la tresorerie d'une nation. */
    NATION_WITHDRAW,
    /** Achat au marche. */
    SHOP_BUY,
    /** Vente au marche. */
    SHOP_SELL,
    /** Recompense de quete. */
    QUEST_REWARD,
    /** Recompense de passage de niveau. */
    LEVEL_UP_REWARD,
    /** Prime de guerre versee par une nation a un joueur. */
    WAR_BOUNTY
}
