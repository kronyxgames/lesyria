package com.kronyxgames.lesyria.application.territory;

import com.kronyxgames.lesyria.application.economy.EconomyService;
import com.kronyxgames.lesyria.application.nation.NationCache;
import com.kronyxgames.lesyria.application.nation.NationRelationProvider;
import com.kronyxgames.lesyria.application.player.PlayerService;
import com.kronyxgames.lesyria.domain.economy.EconomyActor;
import com.kronyxgames.lesyria.domain.economy.TransactionType;
import com.kronyxgames.lesyria.domain.nation.Nation;
import com.kronyxgames.lesyria.domain.nation.NationMember;
import com.kronyxgames.lesyria.domain.nation.NationRelation;
import com.kronyxgames.lesyria.domain.player.PlayerProfile;
import com.kronyxgames.lesyria.domain.territory.AccessLevel;
import com.kronyxgames.lesyria.domain.territory.Territory;
import com.kronyxgames.lesyria.domain.territory.TerritoryException;
import com.kronyxgames.lesyria.domain.territory.TerritoryPermission;
import com.kronyxgames.lesyria.domain.territory.TerritoryStatus;
import com.kronyxgames.lesyria.infrastructure.configuration.ConfigHolder;
import com.kronyxgames.lesyria.infrastructure.persistence.Database;
import com.kronyxgames.lesyria.infrastructure.persistence.TerritoryRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Service territorial : revendication, permissions, resolution d'acces.
 *
 * <p>Toutes les operations d'ecriture sont asynchrones et transactionnelles.
 * La resolution d'acces ({@link #isAllowed}) est synchrone : elle doit pouvoir
 * etre appelee depuis les evenements Bukkit.</p>
 *
 * <p>Une revendication est confirmee dans une transaction unique : validation
 * (nation, role, chevauchement, limites), paiement preleve sur la tresorerie de
 * la nation, insertion, puis mise a jour de l'index. Un verrou applicatif
 * serialise les revendications concurrentes afin que l'index ne puisse jamais
 * accepter deux revendications qui se chevauchent.</p>
 */
public final class TerritoryService {

    private final Database database;
    private final TerritoryRepository repository;
    private final TerritoryIndex index;
    private final NationCache nations;
    private final NationRelationProvider relations;
    private final EconomyService economy;
    private final PlayerService players;
    private final ConfigHolder config;
    private final Logger logger;
    private final ReentrantLock claimLock = new ReentrantLock();

    public TerritoryService(Database database, TerritoryRepository repository, TerritoryIndex index,
                            NationCache nations, NationRelationProvider relations,
                            EconomyService economy, PlayerService players, ConfigHolder config,
                            Logger logger) {
        this.database = database;
        this.repository = repository;
        this.index = index;
        this.nations = nations;
        this.relations = relations;
        this.economy = economy;
        this.players = players;
        this.config = config;
        this.logger = logger;
    }

    /**
     * Charge l'index territorial depuis la base.
     *
     * <p>Appelee pendant l'activation du plugin : le serveur n'accepte pas encore
     * de joueurs, un chargement synchrone garantit un index complet avant la
     * premiere connexion.</p>
     */
    public void loadIndex() {
        List<Territory> territories = database.supplySync(repository::findAll);
        index.clear();
        territories.forEach(index::index);
        logger.info("Index territorial: " + territories.size() + " territoire(s), "
                + index.chunkCount() + " chunk(s)");
    }

    /**
     * @param world nom du monde
     * @param x     chunk X
     * @param z     chunk Z
     * @return le territoire du chunk, ou {@code null}
     */
    public Territory territoryAt(String world, int x, int z) {
        return index.at(world, x, z);
    }

    /** @return l'index territorial (lecture seule). */
    public TerritoryIndex index() {
        return index;
    }

    /**
     * Resout une permission territoriale pour un joueur.
     *
     * @param playerUuid joueur
     * @param territory  territoire concerne ({@code null} pour une zone sauvage)
     * @param permission permission demandee
     * @return vrai si l'action est autorisee
     */
    public boolean isAllowed(UUID playerUuid, Territory territory, TerritoryPermission permission) {
        if (territory == null) {
            return true;
        }
        AccessLevel required = territory.permissions().level(permission);
        NationRelation relation = relations.relationOf(playerUuid, territory.nationId());
        return required.grants(relation);
    }

    /**
     * Revendique une zone carree de chunks autour d'un chunk central.
     *
     * @param actor           joueur a l'origine de la revendication
     * @param world           monde
     * @param centerChunkX    chunk central X
     * @param centerChunkZ    chunk central Z
     * @param requestedName   nom souhaite ({@code null} ou vide pour un nom automatique)
     * @return le futur du territoire cree
     */
    public CompletableFuture<Territory> claim(PlayerProfile actor, String world, int centerChunkX,
                                             int centerChunkZ, String requestedName) {
        AtomicReference<Territory> indexed = new AtomicReference<>();
        return database.supply(connection -> {
            claimLock.lock();
            try {
                NationMember membership = nations.membershipOf(actor.uuid());
                if (membership == null) {
                    throw new TerritoryException(
                            "Vous devez appartenir a une nation pour revendiquer un territoire.");
                }
                if (!membership.canManage()) {
                    throw new TerritoryException(
                            "Seuls le chef et les officiers peuvent revendiquer un territoire.");
                }
                Nation nation = nations.byId(membership.nationId());
                if (nation == null) {
                    throw new TerritoryException("Nation introuvable.");
                }

                int territoryCount = repository.countByNation(connection, nation.id());
                if (territoryCount >= config.territory().maxTerritoriesPerNation()) {
                    throw new TerritoryException("Votre nation a atteint la limite de "
                            + config.territory().maxTerritoriesPerNation() + " territoires.");
                }

                String name = resolveName(connection, nation, territoryCount, requestedName);
                Territory candidate = Territory.claim(name, nation.id(), actor.uuid(), world,
                        centerChunkX, centerChunkZ, config.territory().claimRadiusChunks(),
                        config.territory().defaultPermissions(), Instant.now());

                if (candidate.chunkCount() > config.territory().maxChunksPerTerritory()) {
                    throw new TerritoryException("La zone demandee depasse la taille maximale de "
                            + config.territory().maxChunksPerTerritory() + " chunks.");
                }
                if (!index.isFree(candidate)) {
                    throw new TerritoryException(
                            "Cette zone appartient deja a un territoire existant.");
                }
                if (repository.findByName(connection, name).isPresent()) {
                    throw new TerritoryException("Le nom de territoire '" + name + "' est deja utilise.");
                }

                long cost = config.territory().claimCost(candidate.chunkCount());
                if (cost > 0) {
                    economy.transferInTransaction(connection, EconomyActor.nation(nation.id()),
                            EconomyActor.system("territory"), cost, TransactionType.TERRITORY_CLAIM,
                            "revendication de " + name);
                }

                Territory stored = repository.insert(connection, candidate);
                // L'index est mis a jour dans la section verrouillee : deux
                // revendications concurrentes ne peuvent pas se chevaucher.
                index.index(stored);
                indexed.set(stored);
                return stored;
            } finally {
                claimLock.unlock();
            }
        }).whenComplete((territory, error) -> {
            if (error != null) {
                Territory stale = indexed.get();
                if (stale != null) {
                    index.unindex(stale.id());
                }
            }
        }).thenCompose(territory -> {
            Nation nation = nations.byId(territory.nationId());
            logger.info("Territoire revendique: " + territory.name() + " par " + actor.username()
                    + " [" + (nation == null ? "?" : nation.tag()) + "] "
                    + territory.describeArea() + " (" + territory.chunkCount() + " chunks)");
            return players.addExperience(actor.uuid(), config.progression().xpPerTerritoryClaim(),
                    "revendication de " + territory.name()).thenApply(ignored -> territory);
        });
    }

    /**
     * Abandonne un territoire de sa nation.
     *
     * @param actor       joueur
     * @param territoryId territoire a abandonner
     * @return le futur de l'operation
     */
    public CompletableFuture<Void> unclaim(PlayerProfile actor, long territoryId) {
        return database.supply(connection -> {
            Territory territory = repository.findById(connection, territoryId)
                    .orElseThrow(() -> new TerritoryException("Territoire introuvable."));
            requireManager(actor, territory.nationId(), "abandonner un territoire");
            repository.delete(connection, territoryId);
            return territory;
        }).thenAccept(territory -> {
            index.unindex(territory.id());
            logger.info("Territoire abandonne: " + territory.name() + " (" + territory.id() + ")");
        });
    }

    /**
     * Modifie une permission territoriale.
     *
     * @param actor      joueur
     * @param territoryId territoire
     * @param permission  permission concernee
     * @param level       nouveau niveau d'acces
     * @return le futur du territoire mis a jour
     */
    public CompletableFuture<Territory> setPermission(PlayerProfile actor, long territoryId,
                                                     TerritoryPermission permission, AccessLevel level) {
        return database.supply(connection -> {
            Territory territory = repository.findById(connection, territoryId)
                    .orElseThrow(() -> new TerritoryException("Territoire introuvable."));
            requireManager(actor, territory.nationId(), "modifier les permissions");
            Territory updated = territory.withPermissions(territory.permissions().with(permission, level));
            repository.update(connection, updated);
            return updated;
        }).thenApply(territory -> {
            index.index(territory);
            logger.info("Permission " + permission + " du territoire " + territory.name()
                    + " fixee a " + level + " par " + actor.username());
            return territory;
        });
    }

    /**
     * Change le statut d'un territoire (utilise par les guerres).
     *
     * @param territoryId territoire
     * @param status      nouveau statut
     * @param reason      motif journalise
     * @return le futur du territoire mis a jour
     */
    public CompletableFuture<Territory> setStatus(long territoryId, TerritoryStatus status,
                                                 String reason) {
        return database.supply(connection -> {
            Territory territory = repository.findById(connection, territoryId)
                    .orElseThrow(() -> new TerritoryException("Territoire introuvable."));
            Territory updated = territory.withStatus(status);
            repository.update(connection, updated);
            return updated;
        }).thenApply(territory -> {
            index.index(territory);
            logger.info("Territoire " + territory.name() + " : statut " + status + " (" + reason + ")");
            return territory;
        });
    }

    /**
     * Retire des territoires de l'index (dissolution d'une nation).
     *
     * @param territories territoires supprimes en base
     */
    public void unindexAll(List<Territory> territories) {
        territories.forEach(territory -> index.unindex(territory.id()));
    }

    /**
     * @param nationId nation
     * @return ses territoires (depuis l'index)
     */
    public List<Territory> territoriesOf(Long nationId) {
        return nationId == null ? List.of() : index.ofNation(nationId);
    }

    /**
     * @return tous les territoires persistes
     */
    public CompletableFuture<List<Territory>> findAll() {
        return database.supply(repository::findAll);
    }

    /**
     * @param playerUuid joueur
     * @param nationId   nation
     * @param action     action demandee
     * @throws TerritoryException si le joueur n'est pas gestionnaire de la nation
     */
    private void requireManager(PlayerProfile actor, long nationId, String action) {
        NationMember membership = nations.membershipOf(actor.uuid());
        if (membership == null || membership.nationId() != nationId) {
            throw new TerritoryException("Ce territoire n'appartient pas a votre nation.");
        }
        if (!membership.canManage()) {
            throw new TerritoryException("Seuls le chef et les officiers peuvent " + action + ".");
        }
    }

    private String resolveName(java.sql.Connection connection, Nation nation, int territoryCount,
                               String requestedName) throws SQLException {
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isEmpty()) {
            name = nation.tag() + "-" + (territoryCount + 1);
        }
        if (name.length() < 3 || name.length() > 32) {
            throw new TerritoryException("Le nom du territoire doit contenir entre 3 et 32 caracteres.");
        }
        if (!name.matches("[A-Za-z0-9 _-]+")) {
            throw new TerritoryException(
                    "Le nom du territoire ne peut contenir que des lettres, chiffres, espaces, '-' et '_'.");
        }
        return name;
    }
}
