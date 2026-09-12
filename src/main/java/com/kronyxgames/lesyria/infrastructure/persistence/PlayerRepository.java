package com.kronyxgames.lesyria.infrastructure.persistence;

import com.kronyxgames.lesyria.domain.player.PlayerHome;
import com.kronyxgames.lesyria.domain.player.PlayerProfile;
import com.kronyxgames.lesyria.domain.player.PlayerStatistics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistance des profils joueurs.
 *
 * <p>Le solde n'est <strong>jamais</strong> ecrit ici : toutes les mutations de
 * monnaie passent par {@link EconomyRepository}, seul ecrivain des soldes. Cela
 * garantit qu'aucun chemin de code ne peut modifier un solde sans journaliser
 * l'operation.</p>
 *
 * <p>Toutes les methodes prennent une {@link Connection} : elles participent
 * ainsi a la transaction ouverte par le service appelant.</p>
 */
public final class PlayerRepository {

    private static final String COLUMNS = """
            uuid, username, first_join, last_join, balance, home_world, home_x, home_y, home_z,
            home_yaw, home_pitch, nation_id, level, experience, stat_kills, stat_deaths,
            stat_blocks_placed, stat_blocks_broken, stat_quests_completed
            """;

    private static final String INSERT = """
            INSERT INTO player_profiles
                (uuid, username, first_join, last_join, balance, level, experience)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (uuid) DO NOTHING
            """;

    /**
     * Insere le profil s'il n'existe pas encore.
     *
     * @param connection connexion transactionnelle
     * @param profile    profil a creer
     * @return vrai si une ligne a ete inseree
     * @throws SQLException en cas d'erreur SQL
     */
    public boolean insert(Connection connection, PlayerProfile profile) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            SqlSupport.setUuid(statement, 1, profile.uuid());
            statement.setString(2, profile.username());
            SqlSupport.setInstant(statement, 3, profile.firstJoin());
            SqlSupport.setInstant(statement, 4, profile.lastJoin());
            statement.setLong(5, profile.balance());
            statement.setInt(6, profile.level());
            statement.setLong(7, profile.experience());
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * @param connection connexion
     * @param uuid       identifiant du joueur
     * @return le profil, vide s'il n'existe pas
     * @throws SQLException en cas d'erreur SQL
     */
    public Optional<PlayerProfile> findByUuid(Connection connection, UUID uuid) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM player_profiles WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            SqlSupport.setUuid(statement, 1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    /**
     * @param connection connexion
     * @param username   pseudo (insensible a la casse)
     * @return le profil, vide si aucun joueur ne porte ce pseudo
     * @throws SQLException en cas d'erreur SQL
     */
    public Optional<PlayerProfile> findByUsername(Connection connection, String username)
            throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM player_profiles WHERE lower(username) = lower(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    /**
     * @param connection connexion
     * @return tous les profils (administrations et statistiques globales)
     * @throws SQLException en cas d'erreur SQL
     */
    public List<PlayerProfile> findAll(Connection connection) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM player_profiles ORDER BY first_join";
        List<PlayerProfile> profiles = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                profiles.add(map(resultSet));
            }
        }
        return profiles;
    }

    /**
     * Met a jour les champs du profil hors solde.
     *
     * @param connection connexion
     * @param profile    profil a jour
     * @throws SQLException en cas d'erreur SQL
     */
    public void update(Connection connection, PlayerProfile profile) throws SQLException {
        String sql = """
                UPDATE player_profiles SET
                    username = ?, last_join = ?, home_world = ?, home_x = ?, home_y = ?, home_z = ?,
                    home_yaw = ?, home_pitch = ?, nation_id = ?, level = ?, experience = ?,
                    stat_kills = ?, stat_deaths = ?, stat_blocks_placed = ?, stat_blocks_broken = ?,
                    stat_quests_completed = ?, updated_at = now()
                WHERE uuid = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.username());
            SqlSupport.setInstant(statement, 2, profile.lastJoin());
            PlayerHome home = profile.home();
            if (home == null) {
                statement.setString(3, null);
                statement.setNull(4, java.sql.Types.DOUBLE);
                statement.setNull(5, java.sql.Types.DOUBLE);
                statement.setNull(6, java.sql.Types.DOUBLE);
                statement.setNull(7, java.sql.Types.REAL);
                statement.setNull(8, java.sql.Types.REAL);
            } else {
                statement.setString(3, home.world());
                statement.setDouble(4, home.x());
                statement.setDouble(5, home.y());
                statement.setDouble(6, home.z());
                statement.setFloat(7, home.yaw());
                statement.setFloat(8, home.pitch());
            }
            if (profile.nationId() == null) {
                statement.setNull(9, java.sql.Types.BIGINT);
            } else {
                statement.setLong(9, profile.nationId());
            }
            statement.setInt(10, profile.level());
            statement.setLong(11, profile.experience());
            PlayerStatistics stats = profile.statistics();
            statement.setInt(12, stats.kills());
            statement.setInt(13, stats.deaths());
            statement.setLong(14, stats.blocksPlaced());
            statement.setLong(15, stats.blocksBroken());
            statement.setInt(16, stats.questsCompleted());
            SqlSupport.setUuid(statement, 17, profile.uuid());
            statement.executeUpdate();
        }
    }

    /**
     * @param connection connexion
     * @param uuid       joueur
     * @param home       nouveau domicile ({@code null} pour effacer)
     * @throws SQLException en cas d'erreur SQL
     */
    public void updateHome(Connection connection, UUID uuid, PlayerHome home) throws SQLException {
        String sql = """
                UPDATE player_profiles SET
                    home_world = ?, home_x = ?, home_y = ?, home_z = ?, home_yaw = ?,
                    home_pitch = ?, updated_at = now()
                WHERE uuid = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (home == null) {
                statement.setString(1, null);
                statement.setNull(2, java.sql.Types.DOUBLE);
                statement.setNull(3, java.sql.Types.DOUBLE);
                statement.setNull(4, java.sql.Types.DOUBLE);
                statement.setNull(5, java.sql.Types.REAL);
                statement.setNull(6, java.sql.Types.REAL);
            } else {
                statement.setString(1, home.world());
                statement.setDouble(2, home.x());
                statement.setDouble(3, home.y());
                statement.setDouble(4, home.z());
                statement.setFloat(5, home.yaw());
                statement.setFloat(6, home.pitch());
            }
            SqlSupport.setUuid(statement, 7, uuid);
            statement.executeUpdate();
        }
    }

    /**
     * @param connection connexion
     * @param uuid       joueur
     * @param nationId   nation d'appartenance ({@code null} pour aucune)
     * @throws SQLException en cas d'erreur SQL
     */
    public void updateNation(Connection connection, UUID uuid, Long nationId) throws SQLException {
        String sql = "UPDATE player_profiles SET nation_id = ?, updated_at = now() WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (nationId == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setLong(1, nationId);
            }
            SqlSupport.setUuid(statement, 2, uuid);
            statement.executeUpdate();
        }
    }

    /**
     * @param connection connexion
     * @param uuid       joueur
     * @param instant    instant de connexion
     * @throws SQLException en cas d'erreur SQL
     */
    public void updateLastJoin(Connection connection, UUID uuid, Instant instant) throws SQLException {
        String sql = "UPDATE player_profiles SET last_join = ?, updated_at = now() WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            SqlSupport.setInstant(statement, 1, instant);
            SqlSupport.setUuid(statement, 2, uuid);
            statement.executeUpdate();
        }
    }

    /**
     * @param connection connexion
     * @param profile    profil dont la progression et les statistiques sont ecrites
     * @throws SQLException en cas d'erreur SQL
     */
    public void updateProgress(Connection connection, PlayerProfile profile) throws SQLException {
        String sql = """
                UPDATE player_profiles SET
                    level = ?, experience = ?, stat_kills = ?, stat_deaths = ?,
                    stat_blocks_placed = ?, stat_blocks_broken = ?, stat_quests_completed = ?,
                    updated_at = now()
                WHERE uuid = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, profile.level());
            statement.setLong(2, profile.experience());
            PlayerStatistics stats = profile.statistics();
            statement.setInt(3, stats.kills());
            statement.setInt(4, stats.deaths());
            statement.setLong(5, stats.blocksPlaced());
            statement.setLong(6, stats.blocksBroken());
            statement.setInt(7, stats.questsCompleted());
            SqlSupport.setUuid(statement, 8, profile.uuid());
            statement.executeUpdate();
        }
    }

    /**
     * @param connection connexion
     * @return nombre de profils enregistres
     * @throws SQLException en cas d'erreur SQL
     */
    public long count(Connection connection) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT COUNT(*) FROM player_profiles");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private PlayerProfile map(ResultSet resultSet) throws SQLException {
        String homeWorld = resultSet.getString("home_world");
        PlayerHome home = homeWorld == null ? null : new PlayerHome(
                homeWorld,
                resultSet.getDouble("home_x"),
                resultSet.getDouble("home_y"),
                resultSet.getDouble("home_z"),
                resultSet.getFloat("home_yaw"),
                resultSet.getFloat("home_pitch"));

        long nationId = resultSet.getLong("nation_id");
        Long nation = resultSet.wasNull() ? null : nationId;

        return new PlayerProfile(
                SqlSupport.getUuid(resultSet, "uuid"),
                resultSet.getString("username"),
                SqlSupport.getInstant(resultSet, "first_join"),
                SqlSupport.getInstant(resultSet, "last_join"),
                resultSet.getLong("balance"),
                home,
                nation,
                resultSet.getInt("level"),
                resultSet.getLong("experience"),
                new PlayerStatistics(
                        resultSet.getInt("stat_kills"),
                        resultSet.getInt("stat_deaths"),
                        resultSet.getLong("stat_blocks_placed"),
                        resultSet.getLong("stat_blocks_broken"),
                        resultSet.getInt("stat_quests_completed")));
    }
}
