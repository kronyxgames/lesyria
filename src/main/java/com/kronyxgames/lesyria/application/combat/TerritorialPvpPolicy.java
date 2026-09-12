package com.kronyxgames.lesyria.application.combat;

import com.kronyxgames.lesyria.application.nation.NationCache;
import com.kronyxgames.lesyria.application.territory.TerritoryService;
import com.kronyxgames.lesyria.domain.nation.NationMember;
import com.kronyxgames.lesyria.domain.territory.Territory;
import com.kronyxgames.lesyria.domain.territory.TerritoryPermission;
import com.kronyxgames.lesyria.domain.territory.TerritoryStatus;
import com.kronyxgames.lesyria.infrastructure.configuration.ConfigHolder;

import java.util.UUID;

/**
 * Politique PvP de la beta.
 *
 * <p>Ordre d'evaluation :</p>
 * <ol>
 *   <li>protection des membres d'une meme nation ;</li>
 *   <li>zone sauvage : PvP selon {@code pvp.wilderness} ;</li>
 *   <li>territoire conteste par une guerre : le PvP est autorise ;</li>
 *   <li>territoire actif : permission {@code PVP} du territoire.</li>
 * </ol>
 */
public final class TerritorialPvpPolicy implements PvpPolicy {

    private final TerritoryService territories;
    private final NationCache nations;
    private final ConfigHolder config;

    public TerritorialPvpPolicy(TerritoryService territories, NationCache nations,
                                ConfigHolder config) {
        this.territories = territories;
        this.nations = nations;
        this.config = config;
    }

    @Override
    public PvpDecision evaluate(UUID attackerUuid, UUID victimUuid, Territory territory) {
        if (config.pvp().protectNationMembers()) {
            NationMember attacker = nations.membershipOf(attackerUuid);
            NationMember victim = nations.membershipOf(victimUuid);
            if (attacker != null && victim != null && attacker.nationId() == victim.nationId()) {
                return PvpDecision.deny("Les membres de votre nation sont proteges.");
            }
        }

        if (territory == null) {
            return config.pvp().wilderness()
                    ? PvpDecision.allow()
                    : PvpDecision.deny("Le PvP est desactive en zone sauvage.");
        }

        if (territory.status() == TerritoryStatus.CONTESTED) {
            return PvpDecision.allow();
        }

        if (!territories.isAllowed(attackerUuid, territory, TerritoryPermission.PVP)) {
            return PvpDecision.deny("Le PvP est interdit sur le territoire " + territory.name() + ".");
        }
        return PvpDecision.allow();
    }
}
