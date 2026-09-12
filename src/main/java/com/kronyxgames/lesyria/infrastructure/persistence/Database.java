package com.kronyxgames.lesyria.infrastructure.persistence;

import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Point d'acces unique a PostgreSQL.
 *
 * <p>Responsabilites :</p>
 * <ul>
 *   <li>pool de connexions HikariCP ;</li>
 *   <li>application des migrations Flyway au demarrage ;</li>
 *   <li>executeur dedie pour que <strong>aucune requete SQL ne tourne sur le
 *       thread principal</strong> du serveur Minecraft (voir
 *       {@link #supply(Database.SqlFunction)}) ;</li>
 *   <li>helper transactionnel {@link #inTransaction(Database.SqlFunction)}.</li>
 * </ul>
 *
 * <p>Les lectures necessaires au demarrage (chargement des nations, de l'index
 * territorial) utilisent volontairement {@link #supplySync(Database.SqlFunction)}
 * : le serveur n'accepte encore aucun joueur a cet instant, bloquer brievement
 * est donc sans consequence, alors que cela garantit un cache coherent avant
 * l'ouverture.</p>
 */
public final class Database implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final ExecutorService executor;
    private final Logger logger;
    private volatile boolean closing;

    private Database(HikariDataSource dataSource, ExecutorService executor, Logger logger) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.logger = logger;
    }

    /**
     * Ouvre le pool, verifie la connexion puis applique les migrations.
     *
     * @param settings configuration de la base
     * @param logger   logger du plugin
     * @return la base prete a l'emploi
     * @throws SQLException si la base est injoignable
     */
    public static Database open(LesyriaConfig.DatabaseSettings settings, Logger logger)
            throws SQLException {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(settings.jdbcUrl());
        // Pilote declare explicitement : sur Bukkit/Paper, DriverManager ne
        // decouvre pas les pilotes charges par le classloader du plugin. Hikari
        // instancie alors la classe directement, sans passer par le ServiceLoader.
        hikari.setDriverClassName("org.postgresql.Driver");
        hikari.setUsername(settings.user());
        hikari.setPassword(settings.password());
        hikari.setPoolName("lesyria-pool");
        hikari.setMaximumPoolSize(settings.poolSize());
        hikari.setMinimumIdle(Math.min(2, settings.poolSize()));
        hikari.setConnectionTimeout(settings.connectionTimeoutMs());
        hikari.setValidationTimeout(Math.min(3000, settings.connectionTimeoutMs()));
        hikari.setKeepaliveTime(TimeUnit.MINUTES.toMillis(2));
        hikari.setMaxLifetime(TimeUnit.MINUTES.toMillis(30));
        hikari.addDataSourceProperty("ApplicationName", "lesyria-plugin");
        hikari.addDataSourceProperty("reWriteBatchedInserts", "true");

        HikariDataSource dataSource = new HikariDataSource(hikari);

        // Detecte immediatement un mot de passe ou un schema invalide
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(3)) {
                throw new SQLException("connexion PostgreSQL invalide");
            }
        } catch (SQLException ex) {
            dataSource.close();
            throw ex;
        }

        Database database = new Database(dataSource,
                Executors.newFixedThreadPool(settings.poolSize(), namedFactory()),
                logger);
        database.migrate();
        return database;
    }

    private static ThreadFactory namedFactory() {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "lesyria-db-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private void migrate() throws SQLException {
        // Flyway scanne les emplacements `classpath:` avec le classloader de
        // contexte du thread. Dans un plugin Bukkit/Paper, celui-ci pointe sur
        // le serveur : les migrations du plugin seraient invisibles. On force
        // donc temporairement le classloader du plugin.
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        try {
            MigrateResult result = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .table("lesyria_schema_history")
                    .load()
                    .migrate();
            logger.info("Base de donnees: " + result.migrationsExecuted
                    + " migration(s) appliquee(s), schema en version "
                    + result.targetSchemaVersion);
        } catch (RuntimeException ex) {
            // Flyway encapsule les erreurs SQL: on les remonte telles quelles
            throw new SQLException("application des migrations impossible: " + ex.getMessage(), ex);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    /**
     * Execute un travail SQL synchrone dans une transaction.
     *
     * <p>A n'utiliser que hors du thread principal, sauf pendant le demarrage.</p>
     *
     * @param work travail a executer
     * @param <T>  type du resultat
     * @return le resultat
     * @throws SQLException si la transaction echoue
     */
    public <T> T inTransaction(SqlFunction<T> work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException ex) {
                safeRollback(connection, ex);
                throw ex;
            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // la connexion sera recyclee par le pool
                }
            }
        }
    }

    private void safeRollback(Connection connection, Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
            logger.log(Level.SEVERE, "annulation de transaction impossible", rollbackFailure);
        }
    }

    /**
     * Execute un travail SQL de facon synchrone (demarrage uniquement).
     *
     * @param work travail a executer
     * @param <T>  type du resultat
     * @return le resultat
     */
    public <T> T supplySync(SqlFunction<T> work) {
        try {
            return inTransaction(work);
        } catch (SQLException ex) {
            throw new PersistenceException("requete SQL impossible: " + ex.getMessage(), ex);
        }
    }

    /**
     * Execute un travail SQL sur l'executeur dedie.
     *
     * @param work travail a executer
     * @param <T>  type du resultat
     * @return un futur complete sur l'executeur dedie
     */
    public <T> CompletableFuture<T> supply(SqlFunction<T> work) {
        if (closing) {
            return CompletableFuture.failedFuture(
                    new PersistenceException("base de donnees en cours d'arret"));
        }
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return inTransaction(work);
                    } catch (SQLException ex) {
                        throw new PersistenceException("requete SQL impossible: " + ex.getMessage(), ex);
                    }
                },
                executor);
    }

    /**
     * @return vrai si la base repond
     */
    public boolean isHealthy() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(3);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "verification de la base echouee", ex);
            return false;
        }
    }

    /** @return l'executeur dedie aux operations de base de donnees. */
    public ExecutorService executor() {
        return executor;
    }

    @Override
    public void close() {
        closing = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        dataSource.close();
        logger.info("Pool PostgreSQL ferme");
    }

    /** Travail SQL produisant un resultat. */
    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    /** Travail SQL sans resultat. */
    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
