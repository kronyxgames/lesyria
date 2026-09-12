package com.kronyxgames.lesyria.domain.nation;

import java.time.Instant;
import java.util.UUID;

/**
 * Membre d'une nation tel qu'affiche dans {@code /nation info}.
 *
 * @param uuid     identifiant du joueur
 * @param username pseudo connu
 * @param role     role dans la nation
 * @param joinedAt date d'adhesion
 */
public record NationMemberView(UUID uuid, String username, NationRole role, Instant joinedAt) {
}
