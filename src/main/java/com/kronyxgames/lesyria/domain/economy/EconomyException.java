package com.kronyxgames.lesyria.domain.economy;

import com.kronyxgames.lesyria.domain.LesyriaException;

/**
 * Erreur metier de l'economie.
 *
 * <p>Les messages portes par cette exception sont destines a etre affiches au
 * joueur : ils doivent rester clairs et sans detail technique.</p>
 */
public class EconomyException extends LesyriaException {

    private static final long serialVersionUID = 1L;

    public EconomyException(String message) {
        super(message);
    }

    public EconomyException(String message, Throwable cause) {
        super(message, cause);
    }

    /** @return vrai si l'erreur est due a un solde insuffisant. */
    public boolean isInsufficientFunds() {
        return this instanceof InsufficientFundsException;
    }
}
