package com.kronyxgames.lesyria.domain.city;

import com.kronyxgames.lesyria.domain.LesyriaException;

/**
 * Erreur de regle metier liee aux villes.
 */
public class CityException extends LesyriaException {

    private static final long serialVersionUID = 1L;

    public CityException(String message) {
        super(message);
    }
}
