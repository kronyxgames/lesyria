package com.kronyxgames.lesyria.application.economy;

import com.kronyxgames.lesyria.application.player.PlayerCache;
import com.kronyxgames.lesyria.domain.economy.EconomyActor;
import com.kronyxgames.lesyria.domain.economy.EconomyException;
import com.kronyxgames.lesyria.domain.economy.TransactionRecord;
import com.kronyxgames.lesyria.domain.economy.TransactionType;
import com.kronyxgames.lesyria.infrastructure.configuration.ConfigHolder;
import com.kronyxgames.lesyria.infrastructure.persistence.Database;
import com.kronyxgames.lesyria.infrastructure.persistence.EconomyRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Service economique de Lesyria.
 *
 * <p>Toutes les operations passent par {@link EconomyRepository#transfer} :
 * verrouillage des comptes, verification des fonds, mise a jour et
 * journalisation dans une seule transaction. Le service ajoute :</p>
 * <ul>
 *   <li>la validation des montants (positifs, plafond anti-abus) ;</li>
 *   <li>le rafraichissement du cache apres commit ;</li>
 *   <li>la trace dans les logs serveur ;</li>
 *   <li>la taxe eventuelle sur les paiements entre joueurs.</li>
 * </ul>
 *
 * <p>{@link #transferInTransaction(Connection, EconomyActor, EconomyActor, long,
 * TransactionType, String)} permet a un autre service (revendication
 * territoriale, fondation de nation...) d'inclure un mouvement de monnaie dans
 * <em>sa</em> transaction : soit l'action metier et son paiement reussissent,
 * soit rien n'est ecrit.</p>
 */
public final class EconomyService {

    private final Database database;
    private final EconomyRepository repository;
    private final PlayerCache playerCache;
    private final ConfigHolder config;
    private final Logger logger;

    public EconomyService(Database database, EconomyRepository repository, PlayerCache playerCache,
                          ConfigHolder config, Logger logger) {
        this.database = database;
        this.repository = repository;
        this.playerCache = playerCache;
        this.config = config;
        this.logger = logger;
    }

    /**
     * @param actor compte
     * @return le solde courant
     */
    public CompletableFuture<Long> balanceOf(EconomyActor actor) {
        return database.supply(connection -> repository.balanceOf(connection, actor));
    }

    /**
     * Execute un mouvement de monnaie dans la transaction de l'appelant.
     *
     * @param connection connexion transactionnelle
     * @param source     compte debite
     * @param target     compte credite
     * @param amount     montant strictement positif
     * @param type       nature de l'operation
     * @param reason     motif journalise
     * @return l'ecriture de journal
     * @throws SQLException en cas d'erreur SQL
     */
    public TransactionRecord transferInTransaction(Connection connection, EconomyActor source,
                                                   EconomyActor target, long amount,
                                                   TransactionType type, String reason)
            throws SQLException {
        validateAmount(amount);
        return repository.transfer(connection, source, target, amount, type, reason);
    }

    /**
     * Virement atomique entre deux comptes.
     *
     * @param source compte debite
     * @param target compte credite
     * @param amount montant
     * @param type   nature
     * @param reason motif
     * @return le futur de l'ecriture de journal
     */
    public CompletableFuture<TransactionRecord> transfer(EconomyActor source, EconomyActor target,
                                                         long amount, TransactionType type,
                                                         String reason) {
        validateAmount(amount);
        return database.supply(connection -> transferInTransaction(connection, source, target, amount,
                        type, reason))
                .thenApply(record -> {
                    refreshCache(record);
                    logTransaction(record);
                    return record;
                });
    }

    /**
     * Credite un compte depuis le systeme (recompense, vente, don admin).
     *
     * @param target compte credite
     * @param amount montant
     * @param type   nature
     * @param reason motif
     * @return le futur de l'ecriture de journal
     */
    public CompletableFuture<TransactionRecord> credit(EconomyActor target, long amount,
                                                       TransactionType type, String reason) {
        return transfer(EconomyActor.system("system"), target, amount, type, reason);
    }

    /**
     * Debite un compte vers le systeme (taxe, achat, retrait admin).
     *
     * @param source compte debite
     * @param amount montant
     * @param type   nature
     * @param reason motif
     * @return le futur de l'ecriture de journal
     */
    public CompletableFuture<TransactionRecord> debit(EconomyActor source, long amount,
                                                      TransactionType type, String reason) {
        return transfer(source, EconomyActor.system("system"), amount, type, reason);
    }

    /**
     * Paiement d'un joueur a un autre, avec taxe eventuelle.
     *
     * <p>Le debiteur est toujours debite du montant demande ; le beneficiaire
     * recoit le montant moins la taxe, laquelle est detruite (puits de monnaie
     * configurable). Les deux ecritures sont dans la meme transaction.</p>
     *
     * @param from   payeur
     * @param to     beneficiaire
     * @param amount montant demande
     * @param reason motif
     * @return le futur de l'ecriture de journal
     */
    public CompletableFuture<TransactionRecord> payPlayer(UUID from, UUID to, long amount,
                                                          String reason) {
        validateAmount(amount);
        if (from.equals(to)) {
            throw new EconomyException("vous ne pouvez pas vous payer vous-meme");
        }
        long tax = taxOf(amount);
        long received = amount - tax;
        return database.supply(connection -> {
            TransactionRecord record = repository.transfer(connection, EconomyActor.player(from),
                    EconomyActor.player(to), received, TransactionType.PLAYER_PAYMENT, reason);
            if (tax > 0) {
                repository.transfer(connection, EconomyActor.player(from), EconomyActor.system("tax"),
                        tax, TransactionType.WITHDRAW, "taxe sur paiement");
            }
            return record;
        }).thenApply(record -> {
            refreshCache(record);
            logger.info("Virement joueur " + from + " -> " + to + " : " + amount
                    + " (taxe " + tax + ") - " + reason);
            return record;
        });
    }

    /**
     * Fixe le solde d'un compte a une valeur donnee.
     *
     * @param target     compte
     * @param newBalance nouveau solde (jamais negatif)
     * @param reason     motif
     * @return l'ecriture de journal, vide si le solde etait deja correct
     */
    public CompletableFuture<Optional<TransactionRecord>> setBalance(EconomyActor target,
                                                                     long newBalance, String reason) {
        if (newBalance < 0) {
            throw new EconomyException("le solde ne peut pas etre negatif");
        }
        return database.supply(connection -> {
            long current = repository.balanceOf(connection, target);
            if (current == newBalance) {
                return Optional.<TransactionRecord>empty();
            }
            if (newBalance > current) {
                return Optional.of(repository.transfer(connection, EconomyActor.system("admin"),
                        target, newBalance - current, TransactionType.ADMIN_SET, reason));
            }
            return Optional.of(repository.transfer(connection, target, EconomyActor.system("admin"),
                    current - newBalance, TransactionType.ADMIN_SET, reason));
        }).thenApply(record -> {
            record.ifPresent(this::refreshCache);
            record.ifPresent(this::logTransaction);
            return record;
        });
    }

    /** @return le nom de la monnaie. */
    public String currencyName() {
        return config.economy().currencyName();
    }

    /** @return le symbole de la monnaie. */
    public String currencySymbol() {
        return config.economy().currencySymbol();
    }

    /**
     * @param amount montant
     * @return la taxe a prelever
     */
    public long taxOf(long amount) {
        double percent = config.economy().playerTransferTaxPercent();
        if (percent <= 0.0d) {
            return 0L;
        }
        return (long) Math.floor(amount * percent / 100.0d);
    }

    /**
     * @param amount montant
     * @return le plafond configure
     */
    public long maxTransactionAmount() {
        return config.economy().maxTransactionAmount();
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new EconomyException("le montant doit etre strictement positif");
        }
        if (amount > config.economy().maxTransactionAmount()) {
            throw new EconomyException("le montant depasse le plafond autorise ("
                    + config.economy().maxTransactionAmount() + ")");
        }
    }

    private void refreshCache(TransactionRecord record) {
        if (record.target() instanceof EconomyActor.PlayerActor player
                && record.targetBalanceAfter() != null) {
            playerCache.applyBalance(player.uuid(), record.targetBalanceAfter());
        }
        if (record.source() instanceof EconomyActor.PlayerActor player
                && record.sourceBalanceAfter() != null) {
            playerCache.applyBalance(player.uuid(), record.sourceBalanceAfter());
        }
    }

    private void logTransaction(TransactionRecord record) {
        logger.info(String.format("Transaction #%d %s %s -> %s : %d %s",
                record.id(), record.type(), record.source().lockKey(), record.target().lockKey(),
                record.amount(), record.reason()));
    }
}
