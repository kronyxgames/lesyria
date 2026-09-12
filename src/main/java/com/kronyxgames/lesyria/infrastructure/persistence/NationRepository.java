package com.kronyxgames.lesyria.infrastructure.persistence;

import com.kronyxgames.lesyria.domain.nation.Nation;
import com.kronyxgames.lesyria.domain.nation.NationMember;
import com.kronyxgames.lesyria.domain.nation.NationMemberView;
import com.kronyxgames.lesyria.domain.nation.NationRole;
import com.kronyxgames.lesyria.domain.nation.NationSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistance des nations, de leurs membres et de leurs invitations.
 *
 * <p>La tresorerie n'est pas modifiee ici : elle appartient a
 * {@link EconomyRepository}, comme les soldes joueurs.</p>
 */
public final class NationRepository {

    private static final String COLUMNS = """
            id, name, tag, leader_uuid, capital_city_id, treasury, description, open_join, created_at
            """;

    /**
     * @param connection connexion
     * @param nation     nation a creer
     * @return la nation avec son identifiant
     * @throws SQLException en cas d'erreur SQL
     */
    public Nation insert(Connection connection, Nation nation) throws SQLException {
        String sql = """
                INSERT INTO nations (name, tag, leader_uuid, capital_city_id, treasury, description,
                                     open_join, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, nation.name());
            statement.setString(2, nation.tag());
            SqlSupport.setUuid(statement, 3, nation.leaderUuid());
            if (nation.capitalCityId() == null) {
                statement.setNull(4, Types.BIGINT);
            } else {
                statement.setLong(4, nation.capitalCityId());
            }
            statement.setLong(5, nation.treasury());
            statement.setString(6, nation.description());
            statement.setBoolean(7, nation.openJoin());
            SqlSupport.setInstant(statement, 8, nation.createdAt());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return nation.withId(keys.getLong(1));
                }
            }
        }
        throw new SQLException("creation de nation sans identifiant genere");
    }

    /**
     * Met a jour les champs editables (hors tresorerie).
     *
     * @param connection connexion
     * @param nation     nation a jour
     * @throws SQLException en cas d'erreur SQL
     */
    public void update(Connection connection, Nation nation) throws SQLException {
        String sql = """
                UPDATE nations SET name = ?, tag = ?, leader_uuid = ?, capital_city_id = ?,
                                   description = ?, open_join = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nation.name());
            statement.setString(2, nation.tag());
            SqlSupport.setUuid(statement, 3, nation.leaderUuid());
            if (nation.capitalCityId() == null) {
                statement.setNull(4, Types.BIGINT);
            } else {
                statement.setLong(4, nation.capitalCityId());
            }
            statement.setString(5, nation.description());
            statement.setBoolean(6, nation.openJoin());
            statement.setLong(7, nation.id());
            statement.executeUpdate();
        }
    }

    public void delete(Connection connection, long nationId) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("DELETE FROM nations WHERE id = ?")) {
            statement.setLong(1, nationId);
            statement.executeUpdate();
        }
    }

    public Optional<Nation> findById(Connection connection, long nationId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM nations WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    public Optional<Nation> findByName(Connection connection, String name) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM nations WHERE lower(name) = lower(?) OR lower(tag) = lower(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    public List<Nation> findAll(Connection connection) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM nations ORDER BY created_at";
        List<Nation> nations = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                nations.add(map(resultSet));
            }
        }
        return nations;
    }

    /**
     * @param connection connexion
     * @return les nations avec leurs compteurs (membres, territoires)
     * @throws SQLException en cas d'erreur SQL
     */
    public List<NationSummary> findAllSummaries(Connection connection) throws SQLException {
        String sql = """
                SELECT n.id, n.name, n.tag, n.leader_uuid, n.capital_city_id, n.treasury,
                       n.description, n.open_join, n.created_at,
                       (SELECT COUNT(*) FROM nation_members m WHERE m.nation_id = n.id) AS member_count,
                       (SELECT COUNT(*) FROM territories t WHERE t.nation_id = n.id) AS territory_count
                FROM nations n
                ORDER BY n.created_at
                """;
        List<NationSummary> summaries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                summaries.add(new NationSummary(map(resultSet), resultSet.getInt("member_count"),
                        resultSet.getInt("territory_count")));
            }
        }
        return summaries;
    }

    /**
     * @param connection connexion
     * @param name       nom souhaite
     * @param tag        etiquette souhaitee
     * @return vrai si le nom OU l'etiquette est deja pris
     * @throws SQLException en cas d'erreur SQL
     */
    public boolean nameOrTagTaken(Connection connection, String name, String tag) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM nations WHERE lower(name) = lower(?) OR lower(tag) = lower(?))";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, tag);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    /**
     * @param connection connexion
     * @return toutes les adhesions (chargees au demarrage pour le cache politique)
     * @throws SQLException en cas d'erreur SQL
     */
    public List<NationMember> findAllMembers(Connection connection) throws SQLException {
        String sql = "SELECT nation_id, uuid, role, joined_at FROM nation_members ORDER BY joined_at";
        List<NationMember> members = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                members.add(mapMember(resultSet));
            }
        }
        return members;
    }

    /**
     * @param connection connexion
     * @param nationId   nation
     * @return les membres avec leur pseudo, tries par role puis anciennete
     * @throws SQLException en cas d'erreur SQL
     */
    public List<NationMemberView> findMemberViews(Connection connection, long nationId)
            throws SQLException {
        String sql = """
                SELECT m.uuid, p.username, m.role, m.joined_at
                FROM nation_members m
                JOIN player_profiles p ON p.uuid = m.uuid
                WHERE m.nation_id = ?
                ORDER BY CASE m.role WHEN 'LEADER' THEN 0 WHEN 'OFFICER' THEN 1 ELSE 2 END,
                         m.joined_at
                """;
        List<NationMemberView> members = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(new NationMemberView(
                            SqlSupport.getUuid(resultSet, "uuid"),
                            resultSet.getString("username"),
                            NationRole.parse(resultSet.getString("role")),
                            SqlSupport.getInstant(resultSet, "joined_at")));
                }
            }
        }
        return members;
    }

    public List<NationMember> findMembers(Connection connection, long nationId) throws SQLException {
        String sql = """
                SELECT nation_id, uuid, role, joined_at FROM nation_members
                WHERE nation_id = ? ORDER BY joined_at
                """;
        List<NationMember> members = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(mapMember(resultSet));
                }
            }
        }
        return members;
    }

    public void insertMember(Connection connection, NationMember member) throws SQLException {
        String sql = "INSERT INTO nation_members (nation_id, uuid, role, joined_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, member.nationId());
            SqlSupport.setUuid(statement, 2, member.uuid());
            statement.setString(3, member.role().name());
            SqlSupport.setInstant(statement, 4, member.joinedAt());
            statement.executeUpdate();
        }
    }

    public void updateMemberRole(Connection connection, long nationId, UUID uuid, NationRole role)
            throws SQLException {
        String sql = "UPDATE nation_members SET role = ? WHERE nation_id = ? AND uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, role.name());
            statement.setLong(2, nationId);
            SqlSupport.setUuid(statement, 3, uuid);
            statement.executeUpdate();
        }
    }

    public void deleteMember(Connection connection, long nationId, UUID uuid) throws SQLException {
        String sql = "DELETE FROM nation_members WHERE nation_id = ? AND uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            SqlSupport.setUuid(statement, 2, uuid);
            statement.executeUpdate();
        }
    }

    public void insertInvite(Connection connection, long nationId, UUID uuid, UUID invitedBy,
                             Instant createdAt) throws SQLException {
        String sql = """
                INSERT INTO nation_invites (nation_id, uuid, invited_by, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (nation_id, uuid) DO UPDATE SET created_at = EXCLUDED.created_at,
                                                            invited_by = EXCLUDED.invited_by
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            SqlSupport.setUuid(statement, 2, uuid);
            if (invitedBy == null) {
                statement.setNull(3, Types.OTHER);
            } else {
                SqlSupport.setUuid(statement, 3, invitedBy);
            }
            SqlSupport.setInstant(statement, 4, createdAt);
            statement.executeUpdate();
        }
    }

    public void deleteInvite(Connection connection, long nationId, UUID uuid) throws SQLException {
        String sql = "DELETE FROM nation_invites WHERE nation_id = ? AND uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            SqlSupport.setUuid(statement, 2, uuid);
            statement.executeUpdate();
        }
    }

    /**
     * @param connection connexion
     * @param uuid       joueur
     * @return les identifiants des nations ayant invite ce joueur
     * @throws SQLException en cas d'erreur SQL
     */
    public List<Long> findInvitedNations(Connection connection, UUID uuid) throws SQLException {
        String sql = "SELECT nation_id FROM nation_invites WHERE uuid = ?";
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            SqlSupport.setUuid(statement, 1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getLong(1));
                }
            }
        }
        return ids;
    }

    /**
     * @param connection connexion
     * @param nationId   nation
     * @param uuid       joueur
     * @return vrai si une invitation existe
     * @throws SQLException en cas d'erreur SQL
     */
    public boolean hasInvite(Connection connection, long nationId, UUID uuid) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM nation_invites WHERE nation_id = ? AND uuid = ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            SqlSupport.setUuid(statement, 2, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    /**
     * Purge les invitations expirees.
     *
     * @param connection connexion
     * @param before     instant limite
     * @return nombre d'invitations supprimees
     * @throws SQLException en cas d'erreur SQL
     */
    public int deleteInvitesOlderThan(Connection connection, Instant before) throws SQLException {
        String sql = "DELETE FROM nation_invites WHERE created_at < ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            SqlSupport.setInstant(statement, 1, before);
            return statement.executeUpdate();
        }
    }

    private Nation map(ResultSet resultSet) throws SQLException {
        long capitalId = resultSet.getLong("capital_city_id");
        Long capital = resultSet.wasNull() ? null : capitalId;
        return new Nation(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("tag"),
                SqlSupport.getUuid(resultSet, "leader_uuid"),
                capital,
                resultSet.getLong("treasury"),
                resultSet.getString("description"),
                resultSet.getBoolean("open_join"),
                SqlSupport.getInstant(resultSet, "created_at"));
    }

    private NationMember mapMember(ResultSet resultSet) throws SQLException {
        return new NationMember(
                resultSet.getLong("nation_id"),
                SqlSupport.getUuid(resultSet, "uuid"),
                NationRole.parse(resultSet.getString("role")),
                SqlSupport.getInstant(resultSet, "joined_at"));
    }
}
