package com.kronyxgames.lesyria.infrastructure.persistence;

import com.kronyxgames.lesyria.domain.city.City;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistance des villes.
 */
public final class CityRepository {

    private static final String COLUMNS = """
            id, nation_id, territory_id, name, world, x, y, z, yaw, pitch, created_at
            """;

    /**
     * @param connection connexion
     * @param city       ville a creer
     * @return la ville avec son identifiant
     * @throws SQLException en cas d'erreur SQL
     */
    public City insert(Connection connection, City city) throws SQLException {
        String sql = """
                INSERT INTO cities (nation_id, territory_id, name, world, x, y, z, yaw, pitch, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, city.nationId());
            if (city.territoryId() == null) {
                statement.setNull(2, Types.BIGINT);
            } else {
                statement.setLong(2, city.territoryId());
            }
            statement.setString(3, city.name());
            statement.setString(4, city.world());
            statement.setDouble(5, city.x());
            statement.setDouble(6, city.y());
            statement.setDouble(7, city.z());
            statement.setFloat(8, city.yaw());
            statement.setFloat(9, city.pitch());
            SqlSupport.setInstant(statement, 10, city.createdAt());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return city.withId(keys.getLong(1));
                }
            }
        }
        throw new SQLException("creation de ville sans identifiant genere");
    }

    /**
     * @param connection connexion
     * @param city       ville a jour
     * @throws SQLException en cas d'erreur SQL
     */
    public void update(Connection connection, City city) throws SQLException {
        String sql = """
                UPDATE cities SET name = ?, world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?,
                                  territory_id = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, city.name());
            statement.setString(2, city.world());
            statement.setDouble(3, city.x());
            statement.setDouble(4, city.y());
            statement.setDouble(5, city.z());
            statement.setFloat(6, city.yaw());
            statement.setFloat(7, city.pitch());
            if (city.territoryId() == null) {
                statement.setNull(8, Types.BIGINT);
            } else {
                statement.setLong(8, city.territoryId());
            }
            statement.setLong(9, city.id());
            statement.executeUpdate();
        }
    }

    public void delete(Connection connection, long cityId) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("DELETE FROM cities WHERE id = ?")) {
            statement.setLong(1, cityId);
            statement.executeUpdate();
        }
    }

    public Optional<City> findById(Connection connection, long cityId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM cities WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    public Optional<City> findByName(Connection connection, String name) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM cities WHERE lower(name) = lower(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    public List<City> findByNation(Connection connection, long nationId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM cities WHERE nation_id = ? ORDER BY created_at";
        return query(connection, sql, nationId);
    }

    /**
     * @param connection connexion
     * @return toutes les villes (utilisees pour /spawn &lt;ville&gt;)
     * @throws SQLException en cas d'erreur SQL
     */
    public List<City> findAll(Connection connection) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM cities ORDER BY created_at";
        return query(connection, sql, null);
    }

    private List<City> query(Connection connection, String sql, Long nationId) throws SQLException {
        List<City> cities = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (nationId != null) {
                statement.setLong(1, nationId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    cities.add(map(resultSet));
                }
            }
        }
        return cities;
    }

    private City map(ResultSet resultSet) throws SQLException {
        long territoryId = resultSet.getLong("territory_id");
        Long territory = resultSet.wasNull() ? null : territoryId;
        return new City(
                resultSet.getLong("id"),
                resultSet.getLong("nation_id"),
                territory,
                resultSet.getString("name"),
                resultSet.getString("world"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z"),
                resultSet.getFloat("yaw"),
                resultSet.getFloat("pitch"),
                SqlSupport.getInstant(resultSet, "created_at"));
    }
}
