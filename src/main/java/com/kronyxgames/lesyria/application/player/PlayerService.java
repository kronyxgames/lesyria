package com.kronyxgames.lesyria.application.player;

import com.kronyxgames.lesyria.application.economy.EconomyService;
import com.kronyxgames.lesyria.domain.economy.EconomyActor;
import com.kronyxgames.lesyria.domain.economy.TransactionType;
import com.kronyxgames.lesyria.domain.player.PlayerHome;
import com.kronyxgames.lesyria.domain.player.PlayerProfile;
import com.kronyxgames.lesyria.infrastructure.configuration.ConfigHolder;
import com.kronyxgames.lesyria.infrastructure.persistence.Database;
import com.kronyxgames.lesyria.infrastructure.persistence.PersistenceException;
import com.kronyxgames.lesyria.infrastructure.persistence.PlayerRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Cycle de vie des joueurs : creation du profil, progression, statistiques.
 *
 * <p>Les statistiques de blocs (pose / casse) sont volontairement accumulees en
 * memoire puis ecrites periodiquement : sans cela, chaque bloc casse
 * declencherait une requete SQL, ce qui est inacceptable sur un serveur
 * Minecraft. Les evenements rares (kill, niveau, quete) sont ecrits
 * immediatement.</p>
 */
public final class PlayerService {

    private final Database database;
    private final PlayerRepository repository;
    private final PlayerCache cache;
    private final EconomyService economy;
    private final ConfigHolder config;
    private final Logger logger;
    private final Set<UUID> dirtyStatistics = ConcurrentHashMap.newKeySet();

    public PlayerService(Database database, PlayerRepository repository, PlayerCache cache,
                         EconomyService economy, ConfigHolder config, Logger logger) {
        this.database = database;
        this.repository = repository;
        this.cache = cache;
        this.economy = economy;
        this.config = config;
        this.logger = logger;
    }

    /**
     * @param uuid joueur
     * @return le profil en cache, ou {@code null}
     */
    public PlayerProfile cached(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * @param username pseudo
     * @return le profil en cache, ou {@code null}
     */
    public PlayerProfile cachedByName(String username) {
        return cache.byName(username);
    }

    /** @return le cache joueurs (lecture seule par les autres composants). */
    public PlayerCache cache() {
        return cache;
    }

    /**
     * Charge le profil du joueur, ou le cree s'il s'agit de sa premiere connexion.
     *
     * <p>Creation : insertion du profil a zero, puis attribution du solde initial
     * via une transaction economique journalisee. Les deux operations partagent
     * la meme transaction.</p>
     *
     * @param uuid     identifiant du joueur
     * @param username pseudo courant
     * @return le futur du profil charge
     */
    public CompletableFuture<PlayerLoadResult> loadOrCreate(UUID uuid, String username) {
        return database.supply(connection -> {
            Instant now = Instant.now();
            var existing = repository.findByUuid(connection, uuid);
            if (existing.isPresent()) {
                PlayerProfile profile = existing.get().withUsername(username).withLastJoin(now);
                repository.update(connection, profile);
                return new PlayerLoadResult(profile, false);
            }

            PlayerProfile created = PlayerProfile.create(uuid, username, now, 0L);
            repository.insert(connection, created);

            long startingBalance = config.economy().startingBalance();
            if (startingBalance > 0) {
                economy.transferInTransaction(connection, EconomyActor.system("initial-balance"),
                        EconomyActor.player(uuid), startingBalance, TransactionType.DEPOSIT,
                        "solde initial");
                created = created.withBalance(startingBalance);
            }
            logger.info("Nouveau profil: " + username + " (" + uuid + ") - solde initial "
                    + startingBalance + " " + economy.currencySymbol());
            return new PlayerLoadResult(created, true);
        }).thenApply(result -> {
            cache.put(result.profile());
            return result;
        });
    }

    /**
     * Resultat du chargement d'un profil.
     *
     * @param profile profil charge
     * @param created vrai s'il vient d'etre cree (premiere connexion)
     */
    public record PlayerLoadResult(PlayerProfile profile, boolean created) {
    }

    /**
     * Enregistre le domicile d'un joueur.
     *
     * @param uuid joueur
     * @param home domicile ({@code null} pour effacer)
     * @return le futur de l'ecriture
     */
    public CompletableFuture<Void> saveHome(UUID uuid, PlayerHome home) {
        return database.supply(connection -> {
            repository.updateHome(connection, uuid, home);
            return null;
        }).thenRun(() -> {
            PlayerProfile profile = cache.get(uuid);
            if (profile != null) {
                cache.apply(profile.withHome(home));
            }
        });
    }

    /**
     * Enregistre la nation d'un joueur.
     *
     * @param uuid     joueur
     * @param nationId nation ({@code null} pour quitter)
     * @return le futur de l'ecriture
     */
    public CompletableFuture<Void> saveNation(UUID uuid, Long nationId) {
        return database.supply(connection -> {
            repository.updateNation(connection, uuid, nationId);
            return null;
        }).thenRun(() -> {
            PlayerProfile profile = cache.get(uuid);
            if (profile != null) {
                cache.apply(profile.withNation(nationId));
            }
        });
    }

    /**
     * Ajoute de l'experience a un joueur (et gere les passages de niveau).
     *
     * @param uuid   joueur
     * @param amount points d'experience (0 = aucun effet)
     * @param reason motif journalise
     * @return le futur de l'ecriture
     */
    public CompletableFuture<Void> addExperience(UUID uuid, long amount, String reason) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return database.supply(connection -> {
            PlayerProfile profile = requireProfile(connection, uuid);
            PlayerProfile updated = grantExperience(connection, profile, amount, reason);
            repository.updateProgress(connection, updated);
            return updated;
        }).thenAccept(cache::apply);
    }

    /**
     * Enregistre un kill joueur : statistiques des deux joueurs et experience du tueur.
     *
     * @param killer tueur
     * @param victim victime
     * @return le futur de l'ecriture
     */
    public CompletableFuture<Void> recordPlayerKill(UUID killer, UUID victim) {
        return database.supply(connection -> {
            List<PlayerProfile> updated = new ArrayList<>(2);

            PlayerProfile killerProfile = requireProfile(connection, killer);
            PlayerProfile newKiller = grantExperience(connection, killerProfile,
                    config.progression().xpPerPlayerKill(), "kill de " + victim);
            newKiller = newKiller.withStatistics(newKiller.statistics().withKill());
            repository.updateProgress(connection, newKiller);
            updated.add(newKiller);

            if (victim != null && !victim.equals(killer)) {
                repository.findByUuid(connection, victim).ifPresent(victimProfile -> {
                    try {
                        PlayerProfile newVictim = victimProfile
                                .withStatistics(victimProfile.statistics().withDeath());
                        repository.updateProgress(connection, newVictim);
                        updated.add(newVictim);
                    } catch (SQLException ex) {
                        throw new PersistenceException("mise a jour des statistiques impossible", ex);
                    }
                });
            }
            return updated;
        }).thenAccept(profiles -> profiles.forEach(cache::apply));
    }

    /**
     * Comptabilise un bloc pose (en memoire, ecrit au prochain flush).
     *
     * @param uuid joueur
     */
    public void recordBlockPlaced(UUID uuid) {
        PlayerProfile profile = cache.get(uuid);
        if (profile != null) {
            cache.apply(profile.withStatistics(profile.statistics().withBlockPlaced()));
            dirtyStatistics.add(uuid);
        }
    }

    /**
     * Comptabilise un bloc casse (en memoire, ecrit au prochain flush).
     *
     * @param uuid joueur
     */
    public void recordBlockBroken(UUID uuid) {
        PlayerProfile profile = cache.get(uuid);
        if (profile != null) {
            cache.apply(profile.withStatistics(profile.statistics().withBlockBroken()));
            dirtyStatistics.add(uuid);
        }
    }

    /**
     * Comptabilise une quete terminee.
     *
     * @param uuid   joueur
     * @param reason motif
     * @return le futur de l'ecriture
     */
    public CompletableFuture<Void> recordQuestCompleted(UUID uuid, String reason) {
        return database.supply(connection -> {
            PlayerProfile profile = requireProfile(connection, uuid);
            PlayerProfile updated = grantExperience(connection, profile,
                    config.progression().xpPerQuestComplete(), reason);
            updated = updated.withStatistics(updated.statistics().withQuestCompleted());
            repository.updateProgress(connection, updated);
            return updated;
        }).thenAccept(cache::apply);
    }

    /**
     * Ecrit les statistiques accumulees en memoire.
     *
     * @return le futur de l'ecriture
     */
    public CompletableFuture<Void> flushStatistics() {
        Set<UUID> pending = new HashSet<>(dirtyStatistics);
        if (pending.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        dirtyStatistics.removeAll(pending);
        CompletableFuture<Void> write = database.supply(connection -> {
            for (UUID uuid : pending) {
                PlayerProfile profile = cache.get(uuid);
                if (profile != null) {
                    repository.updateProgress(connection, profile);
                }
            }
            return null;
        });
        return write.exceptionally(throwable -> {
            // Les statistiques ne sont pas perdues : elles restent en cache et
            // seront retentees au prochain flush.
            dirtyStatistics.addAll(pending);
            logger.warning("ecriture des statistiques impossible: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * Recherche un profil par pseudo (joueur hors ligne compris).
     *
     * @param username pseudo
     * @return le profil, vide si inconnu
     */
    public CompletableFuture<Optional<PlayerProfile>> findByUsername(String username) {
        return database.supply(connection -> repository.findByUsername(connection, username));
    }

    /**
     * @param uuid joueur
     * @return tous les profils persistes
     */
    public CompletableFuture<List<PlayerProfile>> findAll() {
        return database.supply(repository::findAll);
    }

    /**
     * @param uuid joueur
     * @return le futur du nombre de profils persistes
     */
    public CompletableFuture<Long> count() {
        return database.supply(repository::count);
    }

    private PlayerProfile requireProfile(Connection connection, UUID uuid) throws SQLException {
        return repository.findByUuid(connection, uuid)
                .orElseThrow(() -> new PersistenceException("profil introuvable: " + uuid));
    }

    private PlayerProfile grantExperience(Connection connection, PlayerProfile profile, long amount,
                                          String reason) throws SQLException {
        if (amount <= 0) {
            return profile;
        }
        int previousLevel = profile.level();
        PlayerProfile updated = profile.withExperienceGained(amount);
        int levelsGained = updated.level() - previousLevel;
        if (levelsGained > 0) {
            long reward = config.progression().levelUpReward() * levelsGained;
            if (reward > 0) {
                economy.transferInTransaction(connection, EconomyActor.system("progression"),
                        EconomyActor.player(profile.uuid()), reward, TransactionType.LEVEL_UP_REWARD,
                        "niveau " + updated.level() + " (" + reason + ")");
                updated = updated.withBalance(updated.balance() + reward);
            }
            logger.info(profile.username() + " passe niveau " + updated.level()
                    + " (+" + reward + " " + economy.currencySymbol() + ")");
        }
        return updated;
    }
}
