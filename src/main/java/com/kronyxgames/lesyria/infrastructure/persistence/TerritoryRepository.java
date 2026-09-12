package com.kronyxgames.lesyria.infrastructure.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.kronyxgames.lesyria.domain.territory.AccessLevel;
import com.kronyxgames.lesyria.domain.territory.Territory;
import com.kronyxgames.lesyria.domain.territory.TerritoryPermission;
import com.kronyxgames.lesyria.domain.territory.TerritoryPermissions;
import com.kronyxgames.lesyria.domain.territory.TerritoryStatus;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistance des territoires.
 *
 * <p>Les permissions sont stockees dans une colonne {@code JSONB} : ajouter une
 * permission ne demande aucune migration, l'enum Java reste la source de
 * verite.</p>
 */
public final class TerritoryRepository {

    private static final Type PERMISSION_MAP_TYPE =
            new TypeToken<Map<String, String>>() { }.getType();

    private static final String COLUMNS = """
            id, name, nation_id, owner_uuid, world, min_chunk_x, min_chunk_z, max_chunk_x,
            max_chunk_z, status, permissions, created_at
            """;

    private final Gson gson = new Gson();

    /**
     * @param connection connexion
     * @param territory  territoire a inserer
     * @return le territoire avec son identifiant
     * @throws SQLException en cas d'erreur SQL
     */
    public Territory insert(Connection connection, Territory territory) throws SQLException {
        String sql = """
                INSERT INTO territories
                    (name, nation_id, owner_uuid, world, min_chunk_x, min_chunk_z, max_chunk_x,
                     max_chunk_z, status, permissions, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, territory.name());
            statement.setLong(2, territory.nationId());
            if (territory.ownerUuid() == null) {
                statement.setNull(3, Types.OTHER);
            } else {
                SqlSupport.setUuid(statement, 3, territory.ownerUuid());
            }
            statement.setString(4, territory.world());
            statement.setInt(5, territory.minChunkX());
            statement.setInt(6, territory.minChunkZ());
            statement.setInt(7, territory.maxChunkX());
            statement.setInt(8, territory.maxChunkZ());
            statement.setString(9, territory.status().name());
            statement.setObject(10, serializePermissions(territory.permissions()), Types.OTHER);
            SqlSupport.setInstant(statement, 11, territory.createdAt());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return territory.withId(keys.getLong(1));
                }
            }
        }
        throw new SQLException("creation du territoire sans identifiant genere");
    }

    /**
     * @param connection connexion
     * @param territory  territoire a mettre a jour
     * @throws SQLException en cas d'erreur SQL
     */
    public void update(Connection connection, Territory territory) throws SQLException {
        String sql = """
                UPDATE territories SET name = ?, status = ?, permissions = ?, owner_uuid = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, territory.name());
            statement.setString(2, territory.status().name());
            statement.setObject(3, serializePermissions(territory.permissions()), Types.OTHER);
            if (territory.ownerUuid() == null) {
                statement.setNull(4, Types.OTHER);
            } else {
                SqlSupport.setUuid(statement, 4, territory.ownerUuid());
            }
            statement.setLong(5, territory.id());
            statement.executeUpdate();
        }
    }

    /**
     * @param connection connexion
     * @param id         identifiant du territoire
     * @throws SQLException en cas d'erreur SQL
     */
    public void delete(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("DELETE FROM territories WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    public Optional<Territory> findById(Connection connection, long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM territories WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    public Optional<Territory> findByName(Connection connection, String name) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM territories WHERE lower(name) = lower(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    public List<Territory> findByNation(Connection connection, long nationId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM territories WHERE nation_id = ? ORDER BY created_at";
        return query(connection, sql, nationId);
    }

    /**
     * @param connection connexion
     * @return tous les territoires, charges au demarrage pour construire l'index
     * @throws SQLException en cas d'erreur SQL
     */
    public List<Territory> findAll(Connection connection) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM territories ORDER BY id";
        return query(connection, sql, null);
    }

    public int countByNation(Connection connection, long nationId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM territories WHERE nation_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private List<Territory> query(Connection connection, String sql, Long nationId)
            throws SQLException {
        List<Territory> territories = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (nationId != null) {
                statement.setLong(1, nationId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    territories.add(map(resultSet));
                }
            }
        }
        return territories;
    }

    private Territory map(ResultSet resultSet) throws SQLException {
        return new Territory(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getLong("nation_id"),
                SqlSupport.getUuid(resultSet, "owner_uuid"),
                resultSet.getString("world"),
                resultSet.getInt("min_chunk_x"),
                resultSet.getInt("min_chunk_z"),
                resultSet.getInt("max_chunk_x"),
                resultSet.getInt("max_chunk_z"),
                parseStatus(resultSet.getString("status")),
                parsePermissions(resultSet.getString("permissions")),
                SqlSupport.getInstant(resultSet, "created_at"));
    }

    private static TerritoryStatus parseStatus(String raw) throws SQLException {
        try {
            return TerritoryStatus.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new SQLException("statut de territoire invalide en base: " + raw, ex);
        }
    }

    /**
     * @param json representation JSON des permissions
     * @return les permissions
     * @throws SQLException si le JSON stocke est corrompu
     */
    private TerritoryPermissions parsePermissions(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return TerritoryPermissions.none();
        }
        try {
            Map<String, String> raw = gson.fromJson(json, PERMISSION_MAP_TYPE);
            if (raw == null) {
                return TerritoryPermissions.none();
            }
            Map<TerritoryPermission, AccessLevel> levels = new EnumMap<>(TerritoryPermission.class);
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                levels.put(TerritoryPermission.valueOf(entry.getKey()),
                        AccessLevel.parse(entry.getValue()));
            }
            return TerritoryPermissions.of(levels);
        } catch (IllegalArgumentException | JsonSyntaxException ex) {
            throw new SQLException("permissions territoriales invalides en base: " + json, ex);
        }
    }

    private String serializePermissions(TerritoryPermissions permissions) {
        Map<String, String> raw = new LinkedHashMap<>();
        for (Map.Entry<TerritoryPermission, AccessLevel> entry : permissions.asMap().entrySet()) {
            raw.put(entry.getKey().name(), entry.getValue().name());
        }
        return gson.toJson(raw);
    }
}
