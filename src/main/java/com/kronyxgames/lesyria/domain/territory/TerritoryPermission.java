package com.kronyxgames.lesyria.domain.territory;

/**
 * Permissions configurables sur un territoire.
 *
 * <p>Ces permissions decrivent <em>qui</em> a le droit de faire quoi a
 * l'interieur d'un territoire donne. Elles sont resolues via un
 * {@link AccessLevel} et sont evaluees synchronement (jamais de requete SQL
 * sur le thread principal du serveur).</p>
 */
public enum TerritoryPermission {

    /** Poser un bloc. */
    BUILD,
    /** Casser un bloc. */
    BREAK,
    /** Ouvrir un coffre, un four, un tonneau... */
    CONTAINER,
    /** Interagir avec un bloc (portes, boutons, leviers, ateliers). */
    INTERACT,
    /** Blesser un autre joueur a l'interieur du territoire. */
    PVP,
    /** Entrer sur le territoire (annonce + restriction de deplacement). */
    ENTER
}
