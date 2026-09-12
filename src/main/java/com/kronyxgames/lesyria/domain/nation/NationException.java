package com.kronyxgames.lesyria.domain.nation;

import com.kronyxgames.lesyria.domain.LesyriaException;

/**
 * Erreur de regle metier liee aux nations.
 *
 * <p>Le message est destine au joueur.</p>
 */
public class NationException extends LesyriaException {

    private static final long serialVersionUID = 1L;

    public NationException(String message) {
        super(message);
    }
}
