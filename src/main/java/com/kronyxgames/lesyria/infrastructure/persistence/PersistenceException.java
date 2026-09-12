package com.kronyxgames.lesyria.infrastructure.persistence;

/**
 * Erreur d'acces aux donnees.
 *
 * <p>Enveloppe les {@link java.sql.SQLException} pour les remonter vers les
 * taches asynchrones, qui ne peuvent pas declarer d'exception verifiee.</p>
 */
public class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public PersistenceException(String message) {
        super(message);
    }
}
