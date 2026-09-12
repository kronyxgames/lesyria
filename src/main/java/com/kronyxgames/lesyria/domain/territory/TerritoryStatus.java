package com.kronyxgames.lesyria.domain.territory;

/**
 * Statut d'un territoire.
 *
 * <p>La beta n'utilise que {@link #ACTIVE} et {@link #CONTESTED}. Le statut
 * {@link #CONTESTED} est applique lorsqu'une guerre vise le territoire : les
 * regles de PvP y deviennent alors permissives pendant le conflit.</p>
 */
public enum TerritoryStatus {

    /** Territoire normalement protege par ses permissions. */
    ACTIVE,
    /** Territoire conteste par une guerre en cours. */
    CONTESTED
}
