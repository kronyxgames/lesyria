package com.kronyxgames.lesyria.application.combat;

/**
 * Resultat de l'evaluation d'une action PvP.
 *
 * @param allowed vrai si les degats sont autorises
 * @param reason  explication affichee au joueur lorsque l'action est refusee
 */
public record PvpDecision(boolean allowed, String reason) {

    /** @return une autorisation. */
    public static PvpDecision allow() {
        return new PvpDecision(true, "");
    }

    /**
     * @param reason motif du refus
     * @return un refus motive
     */
    public static PvpDecision deny(String reason) {
        return new PvpDecision(false, reason);
    }
}
