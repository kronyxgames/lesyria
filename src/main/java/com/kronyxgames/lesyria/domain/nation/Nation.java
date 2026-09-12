package com.kronyxgames.lesyria.domain.nation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Nation : regroupement politique de joueurs, proprietaire de territoires.
 *
 * <p>La tresorerie est une "identite economique" a part entiere : les
 * operations qui la touchent passent par le meme service transactionnel que
 * les comptes joueurs (voir {@code application.economy.EconomyService}).</p>
 *
 * @param id            identifiant en base
 * @param name          nom unique (insensible a la casse)
 * @param tag           etiquette courte unique, affichee avant les pseudos
 * @param leaderUuid    chef de la nation
 * @param capitalCityId ville capitale ({@code null} tant qu'aucune ville)
 * @param treasury      solde de la tresorerie
 * @param description   description libre
 * @param openJoin      vrai si {@code /nation join} est ouvert sans invitation
 * @param createdAt     date de fondation
 */
public record Nation(
        Long id,
        String name,
        String tag,
        UUID leaderUuid,
        Long capitalCityId,
        long treasury,
        String description,
        boolean openJoin,
        Instant createdAt) {

    public Nation {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(leaderUuid, "leaderUuid");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(createdAt, "createdAt");
        if (treasury < 0) {
            throw new IllegalArgumentException("la tresorerie ne peut pas etre negative");
        }
    }

    public Nation withId(Long newId) {
        return new Nation(newId, name, tag, leaderUuid, capitalCityId, treasury, description,
                openJoin, createdAt);
    }

    public Nation withTreasury(long newTreasury) {
        return new Nation(id, name, tag, leaderUuid, capitalCityId, newTreasury, description,
                openJoin, createdAt);
    }

    public Nation withCapitalCity(Long cityId) {
        return new Nation(id, name, tag, leaderUuid, cityId, treasury, description, openJoin,
                createdAt);
    }

    public Nation withLeader(UUID newLeader) {
        return new Nation(id, name, tag, newLeader, capitalCityId, treasury, description,
                openJoin, createdAt);
    }

    public Nation withDescription(String newDescription) {
        return new Nation(id, name, tag, leaderUuid, capitalCityId, treasury, newDescription,
                openJoin, createdAt);
    }

    /** @return le tag pret a etre affiche, par exemple {@code [LSY]}. */
    public String displayTag() {
        return "[" + tag + "]";
    }
}
