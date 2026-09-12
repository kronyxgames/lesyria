package com.kronyxgames.lesyria.domain.economy;

/**
 * Fonds insuffisants pour effectuer une operation.
 *
 * <p>Exception levee <strong>avant</strong> toute ecriture : l'economie n'est
 * jamais laissee dans un etat intermediaire.</p>
 */
public class InsufficientFundsException extends EconomyException {

    private static final long serialVersionUID = 1L;

    private final transient EconomyActor actor;
    private final long required;
    private final long available;

    public InsufficientFundsException(EconomyActor actor, long required, long available) {
        super(String.format("solde insuffisant (%s): %d requis, %d disponible",
                actor.identifier(), required, available));
        this.actor = actor;
        this.required = required;
        this.available = available;
    }

    public EconomyActor actor() {
        return actor;
    }

    public long required() {
        return required;
    }

    public long available() {
        return available;
    }
}
