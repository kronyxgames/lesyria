package com.kronyxgames.lesyria.application.combat;

import com.kronyxgames.lesyria.domain.territory.Territory;

import java.util.UUID;

/**
 * Regles de combat joueur contre joueur.
 *
 * <p>Le PvP de Lesyria n'est jamais "active partout" : il depend du lieu
 * (zone sauvage ou territoire) et de l'etat du monde (territoire conteste par
 * une guerre). Cette interface isole cette decision afin de pouvoir ajouter plus
 * tard paix, tension, siege et conquete sans modifier les listeners Bukkit.</p>
 */
@FunctionalInterface
public interface PvpPolicy {

    /**
     * @param attackerUuid attaquant
     * @param victimUuid   victime
     * @param territory    territoire ou se produit l'attaque ({@code null} en zone sauvage)
     * @return la decision appliquee
     */
    PvpDecision evaluate(UUID attackerUuid, UUID victimUuid, Territory territory);
}
