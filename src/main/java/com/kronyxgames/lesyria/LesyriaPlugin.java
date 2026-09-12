package com.kronyxgames.lesyria;

import com.kronyxgames.lesyria.application.city.CityCache;
import com.kronyxgames.lesyria.application.city.CityService;
import com.kronyxgames.lesyria.application.combat.PvpPolicy;
import com.kronyxgames.lesyria.application.combat.TerritorialPvpPolicy;
import com.kronyxgames.lesyria.application.economy.EconomyService;
import com.kronyxgames.lesyria.application.nation.NationCache;
import com.kronyxgames.lesyria.application.nation.NationRelationProvider;
import com.kronyxgames.lesyria.application.nation.NationService;
import com.kronyxgames.lesyria.application.player.PlayerCache;
import com.kronyxgames.lesyria.application.player.PlayerService;
import com.kronyxgames.lesyria.application.territory.TerritoryIndex;
import com.kronyxgames.lesyria.application.territory.TerritoryService;
import com.kronyxgames.lesyria.infrastructure.configuration.ConfigHolder;
import com.kronyxgames.lesyria.infrastructure.configuration.ConfigurationException;
import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig;
import com.kronyxgames.lesyria.infrastructure.http.StatusHttpServer;
import com.kronyxgames.lesyria.infrastructure.persistence.CityRepository;
import com.kronyxgames.lesyria.infrastructure.persistence.Database;
import com.kronyxgames.lesyria.infrastructure.persistence.EconomyRepository;
import com.kronyxgames.lesyria.infrastructure.persistence.NationRepository;
import com.kronyxgames.lesyria.infrastructure.persistence.PersistenceException;
import com.kronyxgames.lesyria.infrastructure.persistence.PlayerRepository;
import com.kronyxgames.lesyria.infrastructure.persistence.TerritoryRepository;
import com.kronyxgames.lesyria.presentation.commands.CityCommand;
import com.kronyxgames.lesyria.presentation.commands.CommandRouter;
import com.kronyxgames.lesyria.presentation.commands.EconomyCommand;
import com.kronyxgames.lesyria.presentation.commands.HomeCommand;
import com.kronyxgames.lesyria.presentation.commands.LesyriaCommand;
import com.kronyxgames.lesyria.presentation.commands.NationCommand;
import com.kronyxgames.lesyria.presentation.commands.SpawnCommand;
import com.kronyxgames.lesyria.presentation.commands.TerritoryCommand;
import com.kronyxgames.lesyria.presentation.listeners.PlayerActivityListener;
import com.kronyxgames.lesyria.presentation.listeners.PlayerConnectionListener;
import com.kronyxgames.lesyria.presentation.listeners.PvpListener;
import com.kronyxgames.lesyria.presentation.listeners.TerritoryProtectionListener;
import com.kronyxgames.lesyria.presentation.messages.Messages;
import com.kronyxgames.lesyria.presentation.teleport.SpawnResolver;
import com.kronyxgames.lesyria.presentation.teleport.TeleportService;
import org.bukkit.command.PluginCommand;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Point d'entree du plugin Lesyria.
 *
 * <p>Cette classe est la <strong>racine de composition</strong> : elle assemble
 * l'infrastructure (base de donnees, API HTTP), les services applicatifs et la
 * presentation (commandes, listeners), puis gere le cycle de vie. Aucune regle
 * de jeu ne vit ici.</p>
 *
 * <p>Ordre de demarrage : configuration, base de donnees et migrations,
 * caches politiques et territoriaux (avant toute connexion joueur), commandes,
 * listeners, API, taches periodiques.</p>
 */
public final class LesyriaPlugin extends JavaPlugin {

    private ConfigHolder config;
    private Messages messages;
    private Database database;
    private PlayerCache playerCache;
    private PlayerService players;
    private EconomyService economy;
    private TerritoryService territories;
    private CityService cities;
    private NationService nations;
    private PvpPolicy pvp;
    private TeleportService teleports;
    private StatusHttpServer httpServer;
    private final List<BukkitTask> scheduledTasks = new ArrayList<>();
    private long startedAt;

    @Override
    public void onEnable() {
        startedAt = System.currentTimeMillis();
        saveDefaultConfig();

        try {
            config = new ConfigHolder(LesyriaConfig.load(getConfig()));
        } catch (ConfigurationException ex) {
            disableWithError("Configuration invalide : " + ex.getMessage());
            return;
        }
        messages = new Messages(config, getLogger());

        try {
            database = Database.open(config.database(), getLogger());
        } catch (SQLException ex) {
            disableWithError("Connexion PostgreSQL impossible (" + config.database().jdbcUrl()
                    + ") : " + ex.getMessage()
                    + " - verifiez database.* dans config.yml ou la variable DATABASE_URL.");
            return;
        }

        if (!buildServices()) {
            return;
        }

        registerCommands();
        registerListeners();
        applyWorldSpawn();
        startApi();
        schedulePeriodicTasks();

        getLogger().info("Lesyria " + getPluginMeta().getVersion() + " pret - "
                + config.server().versionLabel() + " - "
                + nations.cache().nationCount() + " nation(s), "
                + territories.index().size() + " territoire(s), "
                + cities.cache().size() + " ville(s)");
    }

    @Override
    public void onDisable() {
        scheduledTasks.forEach(BukkitTask::cancel);
        scheduledTasks.clear();
        if (teleports != null) {
            teleports.cancelAll();
        }
        if (httpServer != null) {
            httpServer.stop();
        }
        if (players != null) {
            try {
                // Derniere chance d'ecrire les statistiques accumulees.
                players.flushStatistics().join();
            } catch (RuntimeException ex) {
                getLogger().log(Level.WARNING, "statistiques non ecrites a l'arret", ex);
            }
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("Lesyria arrete.");
    }

    private boolean buildServices() {
        PlayerRepository playerRepository = new PlayerRepository();
        EconomyRepository economyRepository = new EconomyRepository();
        TerritoryRepository territoryRepository = new TerritoryRepository();
        NationRepository nationRepository = new NationRepository();
        CityRepository cityRepository = new CityRepository();

        playerCache = new PlayerCache();
        economy = new EconomyService(database, economyRepository, playerCache, config, getLogger());
        players = new PlayerService(database, playerRepository, playerCache, economy, config,
                getLogger());

        TerritoryIndex index = new TerritoryIndex();
        NationCache nationCache = new NationCache();
        NationRelationProvider relations = NationRelationProvider.local(nationCache);

        territories = new TerritoryService(database, territoryRepository, index, nationCache,
                relations, economy, players, config, getLogger());
        cities = new CityService(database, cityRepository, new CityCache(), index, nationCache,
                economy, config, getLogger());
        nations = new NationService(database, nationRepository, playerRepository, nationCache,
                players, economy, territories, cities, config, getLogger());
        pvp = new TerritorialPvpPolicy(territories, nationCache, config);
        teleports = new TeleportService(this, config, messages);

        try {
            // Chargement synchrone : le serveur n'accepte encore aucun joueur,
            // et les caches doivent etre coherents des la premiere connexion.
            nations.loadCache();
            territories.loadIndex();
            cities.loadCache();
        } catch (PersistenceException ex) {
            getLogger().log(Level.SEVERE, "chargement des donnees impossible", ex);
            if (database != null) {
                database.close();
            }
            disableWithError("Donnees illisibles : " + ex.getMessage());
            return false;
        }
        return true;
    }

    private void registerCommands() {
        register("lesyria", LesyriaCommand.create(this));
        register("spawn", SpawnCommand.create(this));
        register("home", HomeCommand.createHome(this));
        register("sethome", HomeCommand.createSetHome(this));
        register("eco", EconomyCommand.create(this));
        register("nation", NationCommand.create(this));
        register("city", CityCommand.create(this));
        register("territory", TerritoryCommand.create(this));
    }

    private void register(String name, CommandRouter router) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Commande declaree introuvable dans plugin.yml : " + name);
            return;
        }
        command.setExecutor(router);
        command.setTabCompleter(router);
    }

    private void registerListeners() {
        var manager = getServer().getPluginManager();
        manager.registerEvents(new PlayerConnectionListener(this), this);
        manager.registerEvents(new TerritoryProtectionListener(this), this);
        manager.registerEvents(new PvpListener(this), this);
        manager.registerEvents(new PlayerActivityListener(this), this);
        manager.registerEvents(teleports, this);
    }

    private void applyWorldSpawn() {
        Location spawn = SpawnResolver.resolveMainSpawn(getServer(), config);
        if (spawn == null) {
            getLogger().warning("Monde de spawn introuvable : " + config.spawn().world()
                    + " (le spawn par defaut du serveur est conserve)");
            return;
        }
        spawn.getWorld().setSpawnLocation(spawn);
        getLogger().info("Spawn du monde " + spawn.getWorld().getName() + " : "
                + spawn.getBlockX() + " " + spawn.getBlockY() + " " + spawn.getBlockZ());
    }

    private void startApi() {
        httpServer = new StatusHttpServer(config, getLogger(), this::statusSnapshot);
        httpServer.start();
    }

    private StatusHttpServer.StatusSnapshot statusSnapshot() {
        return new StatusHttpServer.StatusSnapshot(
                true,
                getServer().getOnlinePlayers().size(),
                getServer().getMaxPlayers(),
                config.server().versionLabel(),
                (System.currentTimeMillis() - startedAt) / 1000L,
                nations.cache().nationCount(),
                territories.index().size(),
                cities.cache().size(),
                getServer().getTPS()[0]);
    }

    private void schedulePeriodicTasks() {
        long statisticsPeriod = config.player().statisticsPersistenceSeconds() * 20L;
        scheduledTasks.add(getServer().getScheduler().runTaskTimer(this,
                () -> players.flushStatistics(), statisticsPeriod, statisticsPeriod));

        long invitePeriod = 20L * 60L * 5L;
        scheduledTasks.add(getServer().getScheduler().runTaskTimer(this,
                () -> nations.purgeExpiredInvites(), invitePeriod, invitePeriod));
    }

    /**
     * Recharge {@code config.yml} a chaud.
     *
     * <p>Les services lisent la configuration courante a chaque appel : couts,
     * limites, messages et politique PvP sont donc appliques immediatement.
     * L'acces base de donnees et l'adresse d'ecoute de l'API HTTP necessitent un
     * redemarrage.</p>
     *
     * @return vrai si la nouvelle configuration est valide
     */
    public boolean reloadLesyriaConfiguration() {
        try {
            reloadConfig();
            config.set(LesyriaConfig.load(getConfig()));
            applyWorldSpawn();
            getLogger().info("Configuration rechargee");
            return true;
        } catch (ConfigurationException ex) {
            getLogger().severe("Configuration invalide, ancienne configuration conservee : "
                    + ex.getMessage());
            return false;
        }
    }

    private void disableWithError(String message) {
        getLogger().severe(message);
        getServer().getPluginManager().disablePlugin(this);
    }

    /** @return la configuration rechargeable. */
    public ConfigHolder config() {
        return config;
    }

    /** @return le service de messages. */
    public Messages messages() {
        return messages;
    }

    /** @return la base de donnees. */
    public Database database() {
        return database;
    }

    /** @return le service joueurs. */
    public PlayerService players() {
        return players;
    }

    /** @return le service economique. */
    public EconomyService economy() {
        return economy;
    }

    /** @return le service territorial. */
    public TerritoryService territories() {
        return territories;
    }

    /** @return le service des villes. */
    public CityService cities() {
        return cities;
    }

    /** @return le service des nations. */
    public NationService nations() {
        return nations;
    }

    /** @return la politique PvP courante. */
    public PvpPolicy pvp() {
        return pvp;
    }

    /** @return le service de teleportation. */
    public TeleportService teleports() {
        return teleports;
    }
}
