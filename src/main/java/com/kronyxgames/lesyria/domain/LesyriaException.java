package com.kronyxgames.lesyria.domain;

/**
 * Erreur metier generique de Lesyria.
 *
 * <p>Toutes les erreurs de regle du jeu (economie, nation, territoire, ville)
 * en derivent. Cela permet a la couche presentation de distinguer clairement :</p>
 * <ul>
 *   <li>une <strong>erreur metier</strong> : message affiche au joueur ;</li>
 *   <li>une <strong>erreur technique</strong> : message generique + log serveur.</li>
 * </ul>
 */
public class LesyriaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LesyriaException(String message) {
        super(message);
    }

    public LesyriaException(String message, Throwable cause) {
        super(message, cause);
    }
}
