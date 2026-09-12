package com.kronyxgames.lesyria.domain.player;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Profil persistant d'un joueur.
 *
 * <p>L'UUID Minecraft est le seul identifiant stable : le pseudo peut changer a
 * tout moment, il n'est conserve que pour l'affichage et les recherches
 * administratives.</p>
 *
 * @param uuid       UUID du joueur (identifiant principal)
 * @param username   dernier pseudo connu
 * @param firstJoin  premiere connexion
 * @param lastJoin   derniere connexion
 * @param balance    solde en Lesyria Coins (jamais negatif)
 * @param home       domicile ({@code null} si non defini)
 * @param nationId   nation d'appartenance ({@code null} si aucune)
 * @param level      niveau courant
 * @param experience experience cumulee
 * @param statistics statistiques de jeu
 */
public record PlayerProfile(
        UUID uuid,
        String username,
        Instant firstJoin,
        Instant lastJoin,
        long balance,
        PlayerHome home,
        Long nationId,
        int level,
        long experience,
        PlayerStatistics statistics) {

    public PlayerProfile {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(firstJoin, "firstJoin");
        Objects.requireNonNull(lastJoin, "lastJoin");
        Objects.requireNonNull(statistics, "statistics");
        if (balance < 0) {
            throw new IllegalArgumentException("le solde ne peut pas etre negatif");
        }
        if (experience < 0) {
            throw new IllegalArgumentException("l'experience ne peut pas etre negative");
        }
        if (level < 1) {
            throw new IllegalArgumentException("le niveau minimum est 1");
        }
    }

    /**
     * Cree le profil initial d'un joueur qui rejoint le serveur pour la premiere fois.
     *
     * @param uuid           UUID du joueur
     * @param username       pseudo
     * @param now            instant de connexion
     * @param startingBalance solde initial
     * @return le profil initial
     */
    public static PlayerProfile create(UUID uuid, String username, Instant now, long startingBalance) {
        if (startingBalance < 0) {
            throw new IllegalArgumentException("le solde initial ne peut pas etre negatif");
        }
        return new PlayerProfile(uuid, username, now, now, startingBalance, null, null, 1, 0L,
                PlayerStatistics.empty());
    }

    /** @return vrai si le joueur a defini un domicile. */
    public boolean hasHome() {
        return home != null;
    }

    /** @return vrai si le joueur appartient a une nation. */
    public boolean hasNation() {
        return nationId != null;
    }

    public PlayerProfile withUsername(String newUsername) {
        return new PlayerProfile(uuid, newUsername, firstJoin, lastJoin, balance, home, nationId,
                level, experience, statistics);
    }

    public PlayerProfile withLastJoin(Instant instant) {
        return new PlayerProfile(uuid, username, firstJoin, instant, balance, home, nationId,
                level, experience, statistics);
    }

    public PlayerProfile withBalance(long newBalance) {
        return new PlayerProfile(uuid, username, firstJoin, lastJoin, newBalance, home, nationId,
                level, experience, statistics);
    }

    public PlayerProfile withHome(PlayerHome newHome) {
        return new PlayerProfile(uuid, username, firstJoin, lastJoin, balance, newHome, nationId,
                level, experience, statistics);
    }

    public PlayerProfile withNation(Long newNationId) {
        return new PlayerProfile(uuid, username, firstJoin, lastJoin, balance, home, newNationId,
                level, experience, statistics);
    }

    /**
     * Applique un gain d'experience et recalcule le niveau.
     *
     * @param gained points gagnes (peut etre nul, jamais negatif)
     * @return le profil mis a jour
     */
    public PlayerProfile withExperienceGained(long gained) {
        if (gained < 0) {
            throw new IllegalArgumentException("l'experience gagnee ne peut pas etre negative");
        }
        long total = experience + gained;
        return new PlayerProfile(uuid, username, firstJoin, lastJoin, balance, home, nationId,
                Progression.levelFor(total), total, statistics);
    }

    public PlayerProfile withStatistics(PlayerStatistics newStatistics) {
        return new PlayerProfile(uuid, username, firstJoin, lastJoin, balance, home, nationId,
                level, experience, newStatistics);
    }
}
