package com.kronyxgames.lesyria.domain.nation;

/**
 * Vue synthetique d'une nation pour {@code /nation list} et l'API HTTP :
 * evite de charger toutes les adhesions pour un simple affichage.
 *
 * @param nation         la nation
 * @param memberCount    nombre de membres
 * @param territoryCount nombre de territoires
 */
public record NationSummary(Nation nation, int memberCount, int territoryCount) {
}
