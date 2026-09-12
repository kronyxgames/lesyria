package com.kronyxgames.lesyria.application.city;

import com.kronyxgames.lesyria.application.economy.EconomyService;
import com.kronyxgames.lesyria.application.nation.NationCache;
import com.kronyxgames.lesyria.application.territory.TerritoryIndex;
import com.kronyxgames.lesyria.domain.city.City;
import com.kronyxgames.lesyria.domain.city.CityException;
import com.kronyxgames.lesyria.domain.economy.EconomyActor;
import com.kronyxgames.lesyria.domain.economy.TransactionType;
import com.kronyxgames.lesyria.domain.nation.Nation;
import com.kronyxgames.lesyria.domain.nation.NationMember;
import com.kronyxgames.lesyria.domain.player.PlayerProfile;
import com.kronyxgames.lesyria.domain.territory.Territory;
import com.kronyxgames.lesyria.infrastructure.configuration.ConfigHolder;
import com.kronyxgames.lesyria.infrastructure.persistence.CityRepository;
import com.kronyxgames.lesyria.infrastructure.persistence.Database;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Service des villes.
 *
 * <p>Une ville est fondee <strong>dans un territoire de sa nation</strong> :
 * c'est ce qui relie la carte territoriale a un point de spawn nomme. Le cout de
 * fondation est preleve sur la tresorerie nationale, comme les revendications.</p>
 */
public final class CityService {

    private final Database database;
    private final CityRepository repository;
    private final CityCache cache;
    private final TerritoryIndex territories;
    private final NationCache nations;
    private final EconomyService economy;
    private final ConfigHolder config;
    private final Logger logger;

    public CityService(Database database, CityRepository repository, CityCache cache,
                       TerritoryIndex territories, NationCache nations, EconomyService economy,
                       ConfigHolder config, Logger logger) {
        this.database = database;
        this.repository = repository;
        this.cache = cache;
        this.territories = territories;
        this.nations = nations;
        this.economy = economy;
        this.config = config;
        this.logger = logger;
    }

    /**
     * Charge le cache des villes (au demarrage du plugin).
     */
    public void loadCache() {
        List<City> cities = database.supplySync(repository::findAll);
        cache.load(cities);
        logger.info("Villes chargees: " + cities.size());
    }

    /**
     * @param name nom de ville
     * @return la ville, ou {@code null}
     */
    public City cachedByName(String name) {
        return cache.byName(name);
    }

    /**
     * @param cityId identifiant
     * @return la ville, ou {@code null}
     */
    public City cachedById(long cityId) {
        return cache.byId(cityId);
    }

    /**
     * @param nationId nation
     * @return ses villes
     */
    public List<City> cachedOfNation(long nationId) {
        return cache.ofNation(nationId);
    }

    /** @return le cache des villes. */
    public CityCache cache() {
        return cache;
    }

    /**
     * Fonde une ville a la position indiquee.
     *
     * @param actor joueur fondateur
     * @param name  nom de la ville
     * @param world monde
     * @param x     abscisse
     * @param y     ordonnee
     * @param z     cote
     * @param yaw   orientation
     * @param pitch inclinaison
     * @return le futur de la ville creee
     */
    public CompletableFuture<City> create(PlayerProfile actor, String name, String world,
                                          double x, double y, double z, float yaw, float pitch) {
        return database.supply(connection -> {
            NationMember membership = nations.membershipOf(actor.uuid());
            if (membership == null) {
                throw new CityException("Vous devez appartenir a une nation pour fonder une ville.");
            }
            if (!membership.canManage()) {
                throw new CityException("Seuls le chef et les officiers peuvent fonder une ville.");
            }
            Nation nation = nations.byId(membership.nationId());
            if (nation == null) {
                throw new CityException("Nation introuvable.");
            }

            String cityName = validateName(name);
            if (repository.findByName(connection, cityName).isPresent()) {
                throw new CityException("Une ville nommee '" + cityName + "' existe deja.");
            }

            int chunkX = Math.floorDiv((int) Math.floor(x), 16);
            int chunkZ = Math.floorDiv((int) Math.floor(z), 16);
            Territory territory = territories.at(world, chunkX, chunkZ);
            if (config.city().requireNationTerritory()) {
                if (territory == null || territory.nationId() != nation.id()) {
                    throw new CityException("Une ville doit etre fondee dans un territoire de votre nation.");
                }
            }
            requireDistanceFromOtherCities(world, chunkX, chunkZ);

            long cost = config.city().creationCost();
            if (cost > 0) {
                economy.transferInTransaction(connection, EconomyActor.nation(nation.id()),
                        EconomyActor.system("city"), cost, TransactionType.CITY_CREATE,
                        "fondation de la ville " + cityName);
            }

            City city = new City(null, nation.id(), territory == null ? null : territory.id(),
                    cityName, world, x, y, z, yaw, pitch, Instant.now());
            return repository.insert(connection, city);
        }).thenApply(city -> {
            cache.put(city);
            Nation nation = nations.byId(city.nationId());
            logger.info("Ville fondee: " + city.name() + " par " + actor.username()
                    + " [" + (nation == null ? "?" : nation.tag()) + "]");
            return city;
        });
    }

    /**
     * Deplace le point de spawn d'une ville de sa nation.
     *
     * @param actor    joueur
     * @param cityName nom de la ville
     * @param world    monde
     * @param x        abscisse
     * @param y        ordonnee
     * @param z        cote
     * @param yaw      orientation
     * @param pitch    inclinaison
     * @return le futur de la ville mise a jour
     */
    public CompletableFuture<City> setSpawn(PlayerProfile actor, String cityName, String world,
                                            double x, double y, double z, float yaw, float pitch) {
        return database.supply(connection -> {
            City city = repository.findByName(connection, cityName)
                    .orElseThrow(() -> new CityException("Ville introuvable: " + cityName));
            NationMember membership = nations.membershipOf(actor.uuid());
            if (membership == null || membership.nationId() != city.nationId() || !membership.canManage()) {
                throw new CityException("Vous ne pouvez pas modifier le spawn de cette ville.");
            }
            City updated = new City(city.id(), city.nationId(), city.territoryId(), city.name(), world,
                    x, y, z, yaw, pitch, city.createdAt());
            repository.update(connection, updated);
            return updated;
        }).thenApply(city -> {
            cache.put(city);
            logger.info("Spawn de la ville " + city.name() + " mis a jour par " + actor.username());
            return city;
        });
    }

    /**
     * @return toutes les villes persistees
     */
    public CompletableFuture<List<City>> findAll() {
        return database.supply(repository::findAll);
    }

    /**
     * Retire du cache les villes d'une nation dissoute.
     *
     * @param nationId nation
     */
    public void removeNationCities(long nationId) {
        cache.removeNation(nationId);
    }

    private void requireDistanceFromOtherCities(String world, int chunkX, int chunkZ) {
        int minimum = config.city().minDistanceChunks();
        if (minimum <= 0) {
            return;
        }
        for (City existing : cache.all()) {
            if (!existing.world().equals(world)) {
                continue;
            }
            int otherX = Math.floorDiv((int) Math.floor(existing.x()), 16);
            int otherZ = Math.floorDiv((int) Math.floor(existing.z()), 16);
            if (Math.max(Math.abs(otherX - chunkX), Math.abs(otherZ - chunkZ)) < minimum) {
                throw new CityException("Une ville existe deja a moins de " + minimum
                        + " chunks d'ici (" + existing.name() + ").");
            }
        }
    }

    private String validateName(String raw) throws SQLException {
        String name = raw == null ? "" : raw.trim();
        int min = config.nation().nameMinLength();
        int max = config.nation().nameMaxLength();
        if (name.length() < min || name.length() > max) {
            throw new CityException("Le nom de la ville doit contenir entre " + min + " et " + max
                    + " caracteres.");
        }
        if (!name.matches("[A-Za-z0-9 _-]+")) {
            throw new CityException(
                    "Le nom de la ville ne peut contenir que des lettres, chiffres, espaces, '-' et '_'.");
        }
        return name;
    }
}
