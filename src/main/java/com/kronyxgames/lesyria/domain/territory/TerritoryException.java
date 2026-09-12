package com.kronyxgames.lesyria.domain.territory;

import com.kronyxgames.lesyria.domain.LesyriaException;

/**
 * Erreur de regle metier liee aux territoires.
 */
public class TerritoryException extends LesyriaException {

    private static final long serialVersionUID = 1L;

    public TerritoryException(String message) {
        super(message);
    }
}
