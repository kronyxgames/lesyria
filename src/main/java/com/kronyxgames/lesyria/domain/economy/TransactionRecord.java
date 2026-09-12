package com.kronyxgames.lesyria.domain.economy;

import java.time.Instant;

/**
 * Ecriture du journal economique.
 *
 * @param id                  identifiant en base
 * @param type                nature de l'operation
 * @param source              compte debite ({@code null} si creation de monnaie)
 * @param target              compte credite ({@code null} si destruction de monnaie)
 * @param amount              montant strictement positif
 * @param reason              motif lisible
 * @param sourceBalanceAfter  solde de la source apres operation ({@code null} si systeme)
 * @param targetBalanceAfter  solde de la cible apres operation ({@code null} si systeme)
 * @param createdAt           horodatage
 */
public record TransactionRecord(
        long id,
        TransactionType type,
        EconomyActor source,
        EconomyActor target,
        long amount,
        String reason,
        Long sourceBalanceAfter,
        Long targetBalanceAfter,
        Instant createdAt) {

    public TransactionRecord {
        if (amount <= 0) {
            throw new IllegalArgumentException("le montant d'une transaction doit etre positif");
        }
    }
}
