package com.kronyxgames.lesyria.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Petits utilitaires JDBC partages par les repositories.
 *
 * <p>Le pilote PostgreSQL n'expose pas {@link Instant} directement pour
 * {@code TIMESTAMPTZ} : on passe systematiquement par
 * {@link OffsetDateTime} en UTC pour eviter toute ambiguite de fuseau.</p>
 */
public final class SqlSupport {

    /** Code SQLState PostgreSQL pour une violation de contrainte unique. */
    public static final String UNIQUE_VIOLATION = "23505";

    /** Code SQLState PostgreSQL pour une violation de contrainte de verification. */
    public static final String CHECK_VIOLATION = "23514";

    private SqlSupport() {
    }

    public static void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        statement.setObject(index, value);
    }

    public static UUID getUuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    public static void setInstant(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        statement.setObject(index, OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
    }

    public static Instant getInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /**
     * @param ex exception SQL
     * @return vrai si l'erreur est une violation de contrainte unique
     */
    public static boolean isUniqueViolation(SQLException ex) {
        return UNIQUE_VIOLATION.equals(ex.getSQLState());
    }

    /**
     * @param ex exception SQL
     * @return vrai si l'erreur est une violation de contrainte de verification
     */
    public static boolean isCheckViolation(SQLException ex) {
        return CHECK_VIOLATION.equals(ex.getSQLState());
    }
}
