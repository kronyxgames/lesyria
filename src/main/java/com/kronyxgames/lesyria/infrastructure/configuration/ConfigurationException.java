package com.kronyxgames.lesyria.infrastructure.configuration;

/**
 * Erreur de configuration.
 *
 * <p>Levee au demarrage lorsque {@code config.yml} (ou une variable
 * d'environnement) contient une valeur inutilisable. Le plugin refuse alors de
 * s'activer plutot que de fonctionner avec une configuration incoherente.</p>
 */
public class ConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
