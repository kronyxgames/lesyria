package com.kronyxgames.lesyria.application.nation;

import com.kronyxgames.lesyria.domain.nation.NationMember;
import com.kronyxgames.lesyria.domain.nation.NationRelation;

import java.util.UUID;

/**
 * Determine la relation entre un joueur et une nation.
 *
 * <p>Point d'extension volontaire : la beta ne connait que "membre" et
 * "etranger". Le systeme diplomatique (alliances, guerres) branchera une
 * implementation qui consulte les relations entre nations, sans toucher ni aux
 * territoires ni aux permissions.</p>
 */
@FunctionalInterface
public interface NationRelationProvider {

    /**
     * @param playerUuid joueur
     * @param nationId   nation de reference
     * @return la relation du joueur avec cette nation
     */
    NationRelation relationOf(UUID playerUuid, long nationId);

    /**
     * @param cache cache politique
     * @return une implementation locale (appartenance uniquement)
     */
    static NationRelationProvider local(NationCache cache) {
        return (playerUuid, nationId) -> {
            NationMember membership = cache.membershipOf(playerUuid);
            if (membership != null && membership.nationId() == nationId) {
                return NationRelation.SELF;
            }
            return NationRelation.OTHER;
        };
    }
}
