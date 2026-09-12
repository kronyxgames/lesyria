package com.kronyxgames.lesyria.domain.nation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Appartenance d'un joueur a une nation.
 *
 * <p>L'unicite "un joueur = une nation" est garantie par la base
 * (index unique {@code nation_members_single_nation_key}).</p>
 *
 * @param nationId identifiant de la nation
 * @param uuid     identifiant du joueur
 * @param role     role du joueur
 * @param joinedAt date d'adhesion
 */
public record NationMember(long nationId, UUID uuid, NationRole role, Instant joinedAt) {

    public NationMember {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }

    /** @return vrai si le joueur dirige la nation. */
    public boolean isLeader() {
        return role == NationRole.LEADER;
    }

    /** @return vrai si le joueur peut gerer la nation. */
    public boolean canManage() {
        return role.canManage();
    }

    public NationMember withRole(NationRole newRole) {
        return new NationMember(nationId, uuid, newRole, joinedAt);
    }
}
