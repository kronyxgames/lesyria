package com.kronyxgames.lesyria.infrastructure.configuration;

import com.kronyxgames.lesyria.domain.territory.AccessLevel;
import com.kronyxgames.lesyria.domain.territory.TerritoryPermission;
import com.kronyxgames.lesyria.domain.territory.TerritoryPermissions;
import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Configuration typée du plugin.
 *
 * <p>Le YAML reste la source de verite pour le gameplay (couts, limites,
 * messages, spawn...). Les variables d'environnement n'ont la priorite que pour
 * ce qui doit varier entre les environnements : acces a la base de donnees et
 * bind de l'API HTTP. Cela permet d'utiliser la meme image Docker en
 * developpement et en production.</p>
 *
 * <p>Toute valeur invalide provoque un {@link ConfigurationException} au
 * demarrage : mieux vaut refuser de s'activer que de tourner avec une
 * configuration incoherente.</p>
 */
public final class LesyriaConfig {

    private final ServerSettings server;
    private final SpawnSettings spawn;
    private final TeleportSettings teleport;
    private final EconomySettings economy;
    private final TerritorySettings territory;
    private final NationSettings nation;
    private final CitySettings city;
    private final PvpSettings pvp;
    private final ProgressionSettings progression;
    private final PlayerSettings player;
    private final DatabaseSettings database;
    private final ApiSettings api;
    private final MessageSettings messages;

    private LesyriaConfig(ServerSettings server, SpawnSettings spawn, TeleportSettings teleport,
                          EconomySettings economy, TerritorySettings territory,
                          NationSettings nation, CitySettings city, PvpSettings pvp,
                          ProgressionSettings progression, PlayerSettings player,
                          DatabaseSettings database, ApiSettings api, MessageSettings messages) {
        this.server = server;
        this.spawn = spawn;
        this.teleport = teleport;
        this.economy = economy;
        this.territory = territory;
        this.nation = nation;
        this.city = city;
        this.pvp = pvp;
        this.progression = progression;
        this.player = player;
        this.database = database;
        this.api = api;
        this.messages = messages;
    }

    /**
     * Charge la configuration depuis le YAML du plugin et l'environnement du processus.
     *
     * @param root racine de configuration
     * @return la configuration typée
     */
    public static LesyriaConfig load(ConfigurationSection root) {
        return load(root, System::getenv);
    }

    /**
     * Charge la configuration (variante testable : la resolution des variables
     * d'environnement est injectable).
     *
     * @param root racine de configuration
     * @param env  resolution des variables d'environnement
     * @return la configuration typée
     */
    public static LesyriaConfig load(ConfigurationSection root, Function<String, String> env) {
        if (root == null) {
            throw new ConfigurationException("config.yml introuvable");
        }
        ServerSettings server = new ServerSettings(
                nonBlank(root.getString("server.version-label", "beta"), "server.version-label"));

        SpawnSettings spawn = new SpawnSettings(
                nonBlank(root.getString("spawn.world"), "spawn.world"),
                number(root, "spawn.x"),
                number(root, "spawn.y"),
                number(root, "spawn.z"),
                (float) root.getDouble("spawn.yaw", 0.0d),
                (float) root.getDouble("spawn.pitch", 0.0d),
                root.getBoolean("spawn.snap-to-ground", true));

        TeleportSettings teleport = new TeleportSettings(
                atLeast(root.getInt("teleport.delay-seconds", 3), 0, "teleport.delay-seconds"),
                atLeast(root.getInt("teleport.cooldown-seconds", 10), 0, "teleport.cooldown-seconds"));

        long startingBalance = atLeast(root.getLong("economy.starting-balance", 100L), 0L,
                "economy.starting-balance");
        EconomySettings economy = new EconomySettings(
                nonBlank(root.getString("economy.currency-name", "Lesyria Coins"),
                        "economy.currency-name"),
                root.getString("economy.currency-symbol", "LC"),
                startingBalance,
                atLeast(root.getLong("economy.max-transaction-amount", 1_000_000_000L), 1L,
                        "economy.max-transaction-amount"),
                clampPercent(root.getDouble("economy.player-transfer-tax-percent", 0.0d),
                        "economy.player-transfer-tax-percent"));

        TerritorySettings territory = new TerritorySettings(
                atLeast(root.getInt("territory.claim.radius-chunks", 1), 0,
                        "territory.claim.radius-chunks"),
                atLeast(root.getLong("territory.claim.cost-per-chunk", 100L), 0L,
                        "territory.claim.cost-per-chunk"),
                atLeast(root.getInt("territory.max-chunks-per-territory", 64), 1,
                        "territory.max-chunks-per-territory"),
                atLeast(root.getInt("territory.max-territories-per-nation", 20), 1,
                        "territory.max-territories-per-nation"),
                readDefaultPermissions(root.getConfigurationSection("territory.default-permissions")));

        int nameMin = atLeast(root.getInt("nation.name-min-length", 3), 1, "nation.name-min-length");
        int nameMax = atLeast(root.getInt("nation.name-max-length", 24), nameMin,
                "nation.name-max-length");
        int tagMin = atLeast(root.getInt("nation.tag-min-length", 2), 1, "nation.tag-min-length");
        int tagMax = atLeast(root.getInt("nation.tag-max-length", 5), tagMin, "nation.tag-max-length");
        NationSettings nation = new NationSettings(
                atLeast(root.getLong("nation.creation-cost", 1000L), 0L, "nation.creation-cost"),
                atLeast(root.getInt("nation.max-members", 50), 1, "nation.max-members"),
                nameMin, nameMax, tagMin, tagMax,
                root.getBoolean("nation.open-join-default", false),
                atLeast(root.getInt("nation.invite-expire-minutes", 15), 1,
                        "nation.invite-expire-minutes"));

        CitySettings city = new CitySettings(
                atLeast(root.getLong("city.creation-cost", 500L), 0L, "city.creation-cost"),
                atLeast(root.getInt("city.min-distance-chunks", 4), 0, "city.min-distance-chunks"),
                root.getBoolean("city.require-nation-territory", true));

        PvpSettings pvp = new PvpSettings(
                root.getBoolean("pvp.wilderness", true),
                root.getBoolean("pvp.protect-nation-members", true));

        ProgressionSettings progression = new ProgressionSettings(
                atLeast(root.getLong("progression.level-up-reward", 25L), 0L,
                        "progression.level-up-reward"),
                atLeast(root.getLong("progression.xp.player-kill", 50L), 0L,
                        "progression.xp.player-kill"),
                atLeast(root.getLong("progression.xp.territory-claim", 100L), 0L,
                        "progression.xp.territory-claim"),
                atLeast(root.getLong("progression.xp.quest-complete", 75L), 0L,
                        "progression.xp.quest-complete"),
                atLeast(root.getLong("progression.xp.shop-trade", 5L), 0L,
                        "progression.xp.shop-trade"));

        PlayerSettings player = new PlayerSettings(
                atLeast(root.getInt("player.statistics-persistence-seconds", 300), 10,
                        "player.statistics-persistence-seconds"));

        DatabaseSettings database = readDatabase(root.getConfigurationSection("database"), env);

        boolean apiEnabled = Boolean.parseBoolean(
                envOr(env, "LESYRIA_API_ENABLED", String.valueOf(root.getBoolean("api.enabled", true))));
        String apiBind = envOr(env, "LESYRIA_API_BIND", root.getString("api.bind", "127.0.0.1"));
        int apiPort = parsePort(
                envOr(env, "LESYRIA_API_PORT", String.valueOf(root.getInt("api.port", 8080))),
                "API_PORT");
        ApiSettings api = new ApiSettings(apiEnabled, nonBlank(apiBind, "api.bind"), apiPort,
                root.getString("api.cors-origin", ""), readNews(root));

        MessageSettings messages = new MessageSettings(
                root.getString("messages.prefix", "&8[&6Lesyria&8] &r"),
                root.getStringList("messages.first-join"),
                root.getStringList("messages.join"),
                root.getStringList("messages.quit"));

        return new LesyriaConfig(server, spawn, teleport, economy, territory, nation, city, pvp,
                progression, player, database, api, messages);
    }

    private static DatabaseSettings readDatabase(ConfigurationSection section,
                                                Function<String, String> env) {
        if (section == null) {
            throw new ConfigurationException("section 'database' manquante dans config.yml");
        }
        String host = section.getString("host", "localhost");
        int port = section.getInt("port", 5432);
        String name = section.getString("name", "lesyria");
        String user = section.getString("user", "lesyria");
        String password = section.getString("password", "");
        int poolSize = section.getInt("pool-size", 6);
        int timeout = section.getInt("connection-timeout-ms", 5000);

        // 1. Variables dediees
        host = envOr(env, "LESYRIA_DB_HOST", host);
        port = parsePort(envOr(env, "LESYRIA_DB_PORT", String.valueOf(port)), "LESYRIA_DB_PORT");
        name = envOr(env, "LESYRIA_DB_NAME", name);
        user = envOr(env, "LESYRIA_DB_USER", user);
        password = envOr(env, "LESYRIA_DB_PASSWORD", password);
        poolSize = atLeast(parsePort(envOr(env, "LESYRIA_DB_POOL_SIZE", String.valueOf(poolSize)),
                "LESYRIA_DB_POOL_SIZE"), 1, "database.pool-size");
        timeout = atLeast(parsePort(envOr(env, "LESYRIA_DB_TIMEOUT_MS", String.valueOf(timeout)),
                "LESYRIA_DB_TIMEOUT_MS"), 100, "database.connection-timeout-ms");

        // 2. DATABASE_URL a la priorite (convention des plateformes d'hebergement)
        String url = env.apply("DATABASE_URL");
        if (url != null && !url.isBlank()) {
            DatabaseSettings parsed = parseDatabaseUrl(url);
            host = parsed.host();
            port = parsed.port();
            name = parsed.name();
            user = parsed.user();
            password = parsed.password();
        }

        return new DatabaseSettings(nonBlank(host, "database.host"), port, nonBlank(name, "database.name"),
                nonBlank(user, "database.user"), password == null ? "" : password, poolSize, timeout);
    }

    private static DatabaseSettings parseDatabaseUrl(String url) {
        try {
            URI uri = new URI(url);
            String user = "lesyria";
            String password = "";
            if (uri.getUserInfo() != null) {
                String[] parts = uri.getUserInfo().split(":", 2);
                user = parts[0];
                if (parts.length > 1) {
                    password = parts[1];
                }
            }
            String path = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            return new DatabaseSettings(
                    uri.getHost() == null ? "localhost" : uri.getHost(),
                    uri.getPort() < 0 ? 5432 : uri.getPort(),
                    path.isBlank() ? "lesyria" : path,
                    user, password, 6, 5000);
        } catch (URISyntaxException ex) {
            throw new ConfigurationException("DATABASE_URL invalide: " + url, ex);
        }
    }

    private static List<NewsEntry> readNews(ConfigurationSection root) {
        List<NewsEntry> news = new ArrayList<>();
        for (Map<?, ?> entry : root.getMapList("api.news")) {
            Object title = entry.get("title");
            Object body = entry.get("body");
            Object date = entry.get("date");
            if (title == null || body == null) {
                throw new ConfigurationException("api.news: chaque actualite requiert 'title' et 'body'");
            }
            news.add(new NewsEntry(String.valueOf(title), String.valueOf(body),
                    date == null ? "" : String.valueOf(date)));
        }
        return List.copyOf(news);
    }

    private static TerritoryPermissions readDefaultPermissions(ConfigurationSection section) {
        TerritoryPermissions permissions = TerritoryPermissions.none();
        if (section == null) {
            return permissions;
        }
        for (String key : section.getKeys(false)) {
            TerritoryPermission permission;
            try {
                permission = TerritoryPermission.valueOf(key.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ConfigurationException("permission territoriale inconnue: '" + key
                        + "' (attendu: BUILD, BREAK, CONTAINER, INTERACT, PVP, ENTER)");
            }
            AccessLevel level = readAccessLevel(section.getString(key), "territory.default-permissions." + key);
            permissions = permissions.with(permission, level);
        }
        return permissions;
    }

    /**
     * @param raw  valeur brute
     * @param path chemin de configuration (pour le message d'erreur)
     * @return le niveau d'acces
     */
    public static AccessLevel readAccessLevel(String raw, String path) {
        try {
            return AccessLevel.parse(raw);
        } catch (IllegalArgumentException ex) {
            throw new ConfigurationException("valeur invalide pour " + path + ": " + ex.getMessage(), ex);
        }
    }

    private static String envOr(Function<String, String> env, String key, String fallback) {
        String value = env.apply(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String nonBlank(String value, String path) {
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("valeur obligatoire manquante: " + path);
        }
        return value.trim();
    }

    private static double number(ConfigurationSection root, String path) {
        if (!root.isSet(path)) {
            throw new ConfigurationException("coordonnee manquante: " + path);
        }
        return root.getDouble(path);
    }

    private static long atLeast(long value, long minimum, String path) {
        if (value < minimum) {
            throw new ConfigurationException(path + " doit etre >= " + minimum + " (valeur: " + value + ")");
        }
        return value;
    }

    private static int atLeast(int value, int minimum, String path) {
        if (value < minimum) {
            throw new ConfigurationException(path + " doit etre >= " + minimum + " (valeur: " + value + ")");
        }
        return value;
    }

    private static double clampPercent(double value, String path) {
        if (value < 0.0d || value > 100.0d) {
            throw new ConfigurationException(path + " doit etre un pourcentage entre 0 et 100");
        }
        return value;
    }

    private static int parsePort(String raw, String path) {
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) {
                throw new ConfigurationException(path + ": port hors bornes (" + port + ")");
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new ConfigurationException(path + ": nombre attendu, recu '" + raw + "'", ex);
        }
    }

    public ServerSettings server() {
        return server;
    }

    public SpawnSettings spawn() {
        return spawn;
    }

    public TeleportSettings teleport() {
        return teleport;
    }

    public EconomySettings economy() {
        return economy;
    }

    public TerritorySettings territory() {
        return territory;
    }

    public NationSettings nation() {
        return nation;
    }

    public CitySettings city() {
        return city;
    }

    public PvpSettings pvp() {
        return pvp;
    }

    public ProgressionSettings progression() {
        return progression;
    }

    public PlayerSettings player() {
        return player;
    }

    public DatabaseSettings database() {
        return database;
    }

    public ApiSettings api() {
        return api;
    }

    public MessageSettings messages() {
        return messages;
    }

    // ─── Sections typées ──────────────────────────────────────────────────────

    /** Metadonnees du serveur. */
    public record ServerSettings(String versionLabel) {
    }

    /** Point de spawn principal. */
    public record SpawnSettings(String world, double x, double y, double z, float yaw, float pitch,
                                boolean snapToGround) {
    }

    /** Reglages de teleportation. */
    public record TeleportSettings(int delaySeconds, int cooldownSeconds) {
    }

    /** Reglages economiques. */
    public record EconomySettings(String currencyName, String currencySymbol, long startingBalance,
                                  long maxTransactionAmount, double playerTransferTaxPercent) {
    }

    /** Reglages territoriaux. */
    public record TerritorySettings(int claimRadiusChunks, long costPerChunk,
                                    int maxChunksPerTerritory, int maxTerritoriesPerNation,
                                    TerritoryPermissions defaultPermissions) {

        /**
         * @return le cout d'une revendication de {@code chunkCount} chunks
         */
        public long claimCost(int chunkCount) {
            return costPerChunk * chunkCount;
        }
    }

    /** Reglages des nations. */
    public record NationSettings(long creationCost, int maxMembers, int nameMinLength,
                                 int nameMaxLength, int tagMinLength, int tagMaxLength,
                                 boolean openJoinDefault, int inviteExpireMinutes) {
    }

    /** Reglages des villes. */
    public record CitySettings(long creationCost, int minDistanceChunks, boolean requireNationTerritory) {
    }

    /** Reglages PvP. */
    public record PvpSettings(boolean wilderness, boolean protectNationMembers) {
    }

    /** Reglages de progression. */
    public record ProgressionSettings(long levelUpReward, long xpPerPlayerKill, long xpPerTerritoryClaim,
                                      long xpPerQuestComplete, long xpPerShopTrade) {
    }

    /** Reglages joueurs. */
    public record PlayerSettings(int statisticsPersistenceSeconds) {
    }

    /** Acces a la base de donnees. */
    public record DatabaseSettings(String host, int port, String name, String user, String password,
                                   int poolSize, int connectionTimeoutMs) {

        /** @return l'URL JDBC correspondante. */
        public String jdbcUrl() {
            return "jdbc:postgresql://" + host + ':' + port + '/' + name;
        }
    }

    /** API HTTP publique. */
    public record ApiSettings(boolean enabled, String bind, int port, String corsOrigin,
                              List<NewsEntry> news) {
    }

    /** Entree d'actualite exposee par {@code GET /news}. */
    public record NewsEntry(String title, String body, String date) {
    }

    /** Messages configures. */
    public record MessageSettings(String prefix, List<String> firstJoin, List<String> join,
                                  List<String> quit) {
    }
}
