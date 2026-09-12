package com.kronyxgames.lesyria.application.nation;

import com.kronyxgames.lesyria.application.city.CityService;
import com.kronyxgames.lesyria.application.economy.EconomyService;
import com.kronyxgames.lesyria.application.player.PlayerService;
import com.kronyxgames.lesyria.application.territory.TerritoryService;
import com.kronyxgames.lesyria.domain.economy.EconomyActor;
import com.kronyxgames.lesyria.domain.economy.TransactionRecord;
import com.kronyxgames.lesyria.domain.economy.TransactionType;
import com.kronyxgames.lesyria.domain.nation.Nation;
import com.kronyxgames.lesyria.domain.nation.NationException;
import com.kronyxgames.lesyria.domain.nation.NationMember;
import com.kronyxgames.lesyria.domain.nation.NationMemberView;
import com.kronyxgames.lesyria.domain.nation.NationRole;
import com.kronyxgames.lesyria.domain.nation.NationSummary;
import com.kronyxgames.lesyria.domain.player.PlayerProfile;
import com.kronyxgames.lesyria.domain.territory.Territory;
import com.kronyxgames.lesyria.infrastructure.configuration.ConfigHolder;
import com.kronyxgames.lesyria.infrastructure.persistence.Database;
import com.kronyxgames.lesyria.infrastructure.persistence.NationRepository;
import com.kronyxgames.lesyria.infrastructure.persistence.PlayerRepository;
import com.kronyxgames.lesyria.infrastructure.persistence.SqlSupport;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Service des nations : fondation, adhesion, invitations, tresorerie, dissolution.
 *
 * <p>Invariants garantis :</p>
 * <ul>
 *   <li>un joueur n'appartient qu'a une seule nation (index unique en base) ;</li>
 *   <li>un nom ou une etiquette ne peut pas etre reutilise ;</li>
 *   <li>le paiement de la fondation et la creation de la nation sont atomiques ;</li>
 *   <li>dissoudre une nation retire ses territoires de l'index territorial.</li>
 * </ul>
 */
public final class NationService {

    private final Database database;
    private final NationRepository repository;
    private final PlayerRepository playerRepository;
    private final NationCache nations;
    private final PlayerService players;
    private final EconomyService economy;
    private final TerritoryService territories;
    private final CityService cities;
    private final ConfigHolder config;
    private final Logger logger;

    public NationService(Database database, NationRepository repository, PlayerRepository playerRepository,
                         NationCache nations, PlayerService players, EconomyService economy,
                         TerritoryService territories, CityService cities, ConfigHolder config,
                         Logger logger) {
        this.database = database;
        this.repository = repository;
        this.playerRepository = playerRepository;
        this.nations = nations;
        this.players = players;
        this.economy = economy;
        this.territories = territories;
        this.cities = cities;
        this.config = config;
        this.logger = logger;
    }

    /**
     * Charge le cache politique depuis la base (au demarrage du plugin).
     */
    public void loadCache() {
        List<Nation> loadedNations = database.supplySync(repository::findAll);
        List<NationMember> loadedMembers = database.supplySync(repository::findAllMembers);
        nations.load(loadedNations, loadedMembers);
        logger.info("Nations: " + loadedNations.size() + ", adhesions: " + loadedMembers.size());
    }

    /**
     * @param nationId identifiant
     * @return la nation, ou {@code null}
     */
    public Nation cached(long nationId) {
        return nations.byId(nationId);
    }

    /**
     * @param name nom ou etiquette
     * @return la nation, ou {@code null}
     */
    public Nation cachedByName(String name) {
        return nations.byName(name);
    }

    /**
     * @param playerUuid joueur
     * @return la nation du joueur, ou {@code null}
     */
    public Nation nationOf(UUID playerUuid) {
        NationMember membership = nations.membershipOf(playerUuid);
        return membership == null ? null : nations.byId(membership.nationId());
    }

    /**
     * @param playerUuid joueur
     * @return son adhesion, ou {@code null}
     */
    public NationMember membershipOf(UUID playerUuid) {
        return nations.membershipOf(playerUuid);
    }

    /** @return toutes les nations connues. */
    public List<Nation> all() {
        return List.copyOf(nations.all());
    }

    /** @return le cache politique. */
    public NationCache cache() {
        return nations;
    }

    /**
     * Fonde une nation.
     *
     * @param founder joueur fondateur (devient chef)
     * @param name    nom de la nation
     * @param tag     etiquette courte
     * @return le futur de la nation creee
     */
    public CompletableFuture<Nation> create(PlayerProfile founder, String name, String tag) {
        return database.supply(connection -> {
            if (nations.membershipOf(founder.uuid()) != null) {
                throw new NationException("Vous appartenez deja a une nation.");
            }
            String nationName = validateName(name);
            String nationTag = validateTag(tag);
            if (repository.nameOrTagTaken(connection, nationName, nationTag)) {
                throw new NationException("Ce nom ou cette etiquette est deja utilise.");
            }

            long cost = config.nation().creationCost();
            if (cost > 0) {
                economy.transferInTransaction(connection, EconomyActor.player(founder.uuid()),
                        EconomyActor.system("nation"), cost, TransactionType.NATION_CREATE,
                        "fondation de la nation " + nationName);
            }

            Nation nation = new Nation(null, nationName, nationTag, founder.uuid(), null, 0L, "",
                    config.nation().openJoinDefault(), Instant.now());
            Nation stored;
            try {
                stored = repository.insert(connection, nation);
            } catch (SQLException ex) {
                if (SqlSupport.isUniqueViolation(ex)) {
                    throw new NationException("Ce nom ou cette etiquette est deja utilise.");
                }
                throw ex;
            }
            repository.insertMember(connection, new NationMember(stored.id(), founder.uuid(),
                    NationRole.LEADER, Instant.now()));
            playerRepository.updateNation(connection, founder.uuid(), stored.id());
            return stored;
        }).thenApply(nation -> {
            nations.putNation(nation);
            nations.putMember(new NationMember(nation.id(), founder.uuid(), NationRole.LEADER,
                    Instant.now()));
            refreshPlayerNation(founder.uuid(), nation.id());
            logger.info("Nation fondee: " + nation.name() + " [" + nation.tag() + "] par "
                    + founder.username());
            return nation;
        });
    }

    /**
     * Invite un joueur a rejoindre une nation.
     *
     * @param actor  membre gestionnaire
     * @param target joueur invite
     * @return le futur de l'operation
     */
    public CompletableFuture<Void> invite(PlayerProfile actor, PlayerProfile target) {
        return database.supply(connection -> {
            NationMember membership = requireMembership(actor.uuid());
            if (!membership.canManage()) {
                throw new NationException("Seuls le chef et les officiers peuvent inviter.");
            }
            if (nations.membershipOf(target.uuid()) != null) {
                throw new NationException(target.username() + " appartient deja a une nation.");
            }
            int memberCount = repository.findMembers(connection, membership.nationId()).size();
            if (memberCount >= config.nation().maxMembers()) {
                throw new NationException("Votre nation a atteint sa limite de membres.");
            }
            repository.insertInvite(connection, membership.nationId(), target.uuid(), actor.uuid(),
                    Instant.now());
            return null;
        }).thenRun(() -> logger.info(actor.username() + " invite " + target.username()
                + " dans " + actor.username()));
    }

    /**
     * Rejoint une nation (sur invitation, ou librement si la nation est ouverte).
     *
     * @param player     joueur
     * @param nationName nom ou etiquette
     * @return le futur de la nation rejointe
     */
    public CompletableFuture<Nation> join(PlayerProfile player, String nationName) {
        return database.supply(connection -> {
            NationMember current = nations.membershipOf(player.uuid());
            if (current != null) {
                Nation existing = nations.byId(current.nationId());
                throw new NationException("Vous appartenez deja a "
                        + (existing == null ? "une nation" : existing.name()) + ".");
            }
            Nation nation = nations.byName(nationName);
            if (nation == null) {
                throw new NationException("Nation introuvable: " + nationName);
            }
            if (repository.findMembers(connection, nation.id()).size() >= config.nation().maxMembers()) {
                throw new NationException("Cette nation est complete.");
            }
            boolean invited = repository.hasInvite(connection, nation.id(), player.uuid());
            if (!invited && !nation.openJoin()) {
                throw new NationException("Vous devez etre invite pour rejoindre cette nation.");
            }
            repository.insertMember(connection, new NationMember(nation.id(), player.uuid(),
                    NationRole.MEMBER, Instant.now()));
            for (Long otherId : repository.findInvitedNations(connection, player.uuid())) {
                repository.deleteInvite(connection, otherId, player.uuid());
            }
            playerRepository.updateNation(connection, player.uuid(), nation.id());
            return nation;
        }).thenApply(nation -> {
            nations.putMember(new NationMember(nation.id(), player.uuid(), NationRole.MEMBER,
                    Instant.now()));
            refreshPlayerNation(player.uuid(), nation.id());
            logger.info(player.username() + " rejoint la nation " + nation.name());
            return nation;
        });
    }

    /**
     * Quitte sa nation.
     *
     * @param player joueur
     * @return le futur de l'operation
     */
    public CompletableFuture<Void> leave(PlayerProfile player) {
        return database.supply(connection -> {
            NationMember membership = requireMembership(player.uuid());
            if (membership.isLeader()) {
                throw new NationException(
                        "En tant que chef, utilisez /nation disband pour dissoudre la nation.");
            }
            repository.deleteMember(connection, membership.nationId(), player.uuid());
            playerRepository.updateNation(connection, player.uuid(), null);
            return membership.nationId();
        }).thenAccept(nationId -> {
            nations.removeMember(player.uuid());
            refreshPlayerNation(player.uuid(), null);
            Nation nation = nations.byId(nationId);
            logger.info(player.username() + " quitte la nation "
                    + (nation == null ? nationId : nation.name()));
        });
    }

    /**
     * Exclut un membre de sa nation.
     *
     * @param actor  membre gestionnaire
     * @param target joueur exclu
     * @return le futur de l'operation
     */
    public CompletableFuture<Void> kick(PlayerProfile actor, PlayerProfile target) {
        return database.supply(connection -> {
            NationMember actorMembership = requireMembership(actor.uuid());
            if (actor.uuid().equals(target.uuid())) {
                throw new NationException("Utilisez /nation leave pour quitter votre nation.");
            }
            NationMember targetMembership = nations.membershipOf(target.uuid());
            if (targetMembership == null || targetMembership.nationId() != actorMembership.nationId()) {
                throw new NationException(target.username() + " n'est pas membre de votre nation.");
            }
            if (targetMembership.isLeader()) {
                throw new NationException("Vous ne pouvez pas exclure le chef de la nation.");
            }
            if (actorMembership.role() == NationRole.MEMBER) {
                throw new NationException("Seuls le chef et les officiers peuvent exclure un membre.");
            }
            if (targetMembership.role() == NationRole.OFFICER && !actorMembership.isLeader()) {
                throw new NationException("Seul le chef peut exclure un officier.");
            }
            repository.deleteMember(connection, actorMembership.nationId(), target.uuid());
            playerRepository.updateNation(connection, target.uuid(), null);
            return null;
        }).thenRun(() -> {
            nations.removeMember(target.uuid());
            refreshPlayerNation(target.uuid(), null);
            logger.info(actor.username() + " exclut " + target.username());
        });
    }

    /**
     * Definie la capitale de la nation.
     *
     * @param actor    chef de la nation
     * @param cityName nom de la ville
     * @return le futur de la nation mise a jour
     */
    public CompletableFuture<Nation> setCapital(PlayerProfile actor, String cityName) {
        return database.supply(connection -> {
            NationMember membership = requireMembership(actor.uuid());
            if (!membership.isLeader()) {
                throw new NationException("Seul le chef peut definir la capitale.");
            }
            var city = cities.cachedByName(cityName);
            if (city == null || city.nationId() != membership.nationId()) {
                throw new NationException("Votre nation ne possede pas de ville nommee " + cityName + ".");
            }
            Nation nation = nations.byId(membership.nationId());
            if (nation == null) {
                throw new NationException("Nation introuvable.");
            }
            Nation updated = nation.withCapitalCity(city.id());
            repository.update(connection, updated);
            return updated;
        }).thenApply(nation -> {
            nations.putNation(nation);
            var capital = cities.cachedById(nation.capitalCityId() == null ? -1L : nation.capitalCityId());
            logger.info("Capitale de " + nation.name() + " : "
                    + (capital == null ? "?" : capital.name()));
            return nation;
        });
    }

    /**
     * Dissout une nation : territoires et villes supprimes, membres liberes.
     *
     * @param actor chef de la nation
     * @return le futur de l'operation
     */
    public CompletableFuture<Void> disband(PlayerProfile actor) {
        NationMember membership = nations.membershipOf(actor.uuid());
        if (membership == null) {
            return CompletableFuture.failedFuture(
                    new NationException("Vous n'appartenez a aucune nation."));
        }
        if (!membership.isLeader()) {
            return CompletableFuture.failedFuture(
                    new NationException("Seul le chef peut dissoudre la nation."));
        }
        long nationId = membership.nationId();
        Nation nation = nations.byId(nationId);
        List<Territory> nationTerritories = territories.territoriesOf(nationId);
        Set<UUID> members = Set.copyOf(nations.membersOf(nationId));

        return database.supply(connection -> {
            // Les contraintes de cle etrangere suppriment les membres, territoires
            // et villes, et remettent a NULL la nation des profils joueurs.
            repository.delete(connection, nationId);
            return null;
        }).thenRun(() -> {
            nations.removeNation(nationId);
            nationTerritories.forEach(this::territoryServiceUnindex);
            cities.removeNationCities(nationId);
            members.forEach(uuid -> refreshPlayerNation(uuid, null));
            logger.info("Nation dissoute: " + (nation == null ? nationId : nation.name())
                    + " (" + nationTerritories.size() + " territoires liberes)");
        });
    }

    /**
     * Depose de l'argent dans la tresorerie de sa nation.
     *
     * @param player joueur
     * @param amount montant
     * @return le futur de l'ecriture de journal
     */
    public CompletableFuture<TransactionRecord> deposit(PlayerProfile player, long amount) {
        NationMember membership = nations.membershipOf(player.uuid());
        if (membership == null) {
            return CompletableFuture.failedFuture(
                    new NationException("Vous n'appartenez a aucune nation."));
        }
        long nationId = membership.nationId();
        return economy.transfer(EconomyActor.player(player.uuid()), EconomyActor.nation(nationId),
                        amount, TransactionType.NATION_DEPOSIT, "depot en tresorerie")
                .thenApply(record -> {
                    if (record.targetBalanceAfter() != null) {
                        nations.applyTreasury(nationId, record.targetBalanceAfter());
                    }
                    return record;
                });
    }

    /**
     * Retire de l'argent de la tresorerie (chef uniquement).
     *
     * @param actor  chef
     * @param amount montant
     * @return le futur de l'ecriture de journal
     */
    public CompletableFuture<TransactionRecord> withdraw(PlayerProfile actor, long amount) {
        NationMember membership = nations.membershipOf(actor.uuid());
        if (membership == null) {
            return CompletableFuture.failedFuture(
                    new NationException("Vous n'appartenez a aucune nation."));
        }
        if (!membership.isLeader()) {
            return CompletableFuture.failedFuture(
                    new NationException("Seul le chef peut retirer de la tresorerie."));
        }
        long nationId = membership.nationId();
        return economy.transfer(EconomyActor.nation(nationId), EconomyActor.player(actor.uuid()),
                        amount, TransactionType.NATION_WITHDRAW, "retrait de tresorerie")
                .thenApply(record -> {
                    if (record.sourceBalanceAfter() != null) {
                        nations.applyTreasury(nationId, record.sourceBalanceAfter());
                    }
                    return record;
                });
    }

    /**
     * @return les nations avec leurs compteurs (membres, territoires)
     */
    public CompletableFuture<List<NationSummary>> summaries() {
        return database.supply(repository::findAllSummaries);
    }

    /**
     * @param nationId nation
     * @return ses membres avec leur pseudo
     */
    public CompletableFuture<List<NationMemberView>> membersOf(long nationId) {
        return database.supply(connection -> repository.findMemberViews(connection, nationId));
    }

    /**
     * @param playerUuid joueur
     * @return les nations qui ont invite ce joueur
     */
    public CompletableFuture<List<Nation>> invitedNations(UUID playerUuid) {
        return database.supply(connection -> repository.findInvitedNations(connection, playerUuid)
                .stream()
                .map(nations::byId)
                .filter(java.util.Objects::nonNull)
                .toList());
    }

    /**
     * Supprime les invitations expirees.
     *
     * @return le futur du nombre d'invitations supprimees
     */
    public CompletableFuture<Integer> purgeExpiredInvites() {
        Instant limit = Instant.now().minusSeconds(config.nation().inviteExpireMinutes() * 60L);
        return database.supply(connection -> repository.deleteInvitesOlderThan(connection, limit));
    }

    private NationMember requireMembership(UUID uuid) {
        NationMember membership = nations.membershipOf(uuid);
        if (membership == null) {
            throw new NationException("Vous n'appartenez a aucune nation.");
        }
        return membership;
    }

    private void refreshPlayerNation(UUID uuid, Long nationId) {
        PlayerProfile cached = players.cached(uuid);
        if (cached != null) {
            players.cache().apply(cached.withNation(nationId));
        }
    }

    private void territoryServiceUnindex(Territory territory) {
        if (territory.id() != null) {
            territories.index().unindex(territory.id());
        }
    }

    private String validateName(String raw) {
        String name = raw == null ? "" : raw.trim();
        var settings = config.nation();
        if (name.length() < settings.nameMinLength() || name.length() > settings.nameMaxLength()) {
            throw new NationException("Le nom de la nation doit contenir entre "
                    + settings.nameMinLength() + " et " + settings.nameMaxLength() + " caracteres.");
        }
        if (!name.matches("[A-Za-z0-9 _-]+")) {
            throw new NationException(
                    "Le nom de la nation ne peut contenir que des lettres, chiffres, espaces, '-' et '_'.");
        }
        return name;
    }

    private String validateTag(String raw) {
        String tag = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
        var settings = config.nation();
        if (tag.length() < settings.tagMinLength() || tag.length() > settings.tagMaxLength()) {
            throw new NationException("L'etiquette doit contenir entre " + settings.tagMinLength()
                    + " et " + settings.tagMaxLength() + " caracteres.");
        }
        if (!tag.matches("[A-Z0-9]+")) {
            throw new NationException(
                    "L'etiquette ne peut contenir que des lettres majuscules et des chiffres.");
        }
        return tag;
    }
}
