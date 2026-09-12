package com.kronyxgames.lesyria.infrastructure.persistence;

import com.kronyxgames.lesyria.domain.economy.EconomyActor;
import com.kronyxgames.lesyria.domain.economy.EconomyException;
import com.kronyxgames.lesyria.domain.economy.InsufficientFundsException;
import com.kronyxgames.lesyria.domain.economy.TransactionRecord;
import com.kronyxgames.lesyria.domain.economy.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seul ecrivain des soldes de Lesyria.
 *
 * <p>Toute mutation de monnaie passe par {@link #transfer} : les comptes
 * concernes sont verrouilles ({@code SELECT ... FOR UPDATE}) dans un ordre
 * deterministe, le solde est verifie, les deux lignes sont mises a jour et
 * l'operation est journalisee <strong>dans la meme transaction</strong>.</p>
 *
 * <p>Consequence : un virement {@code A -100 / B +100} est atomique. Si une
 * etape echoue, aucune des deux lignes n'est modifiee et aucune ecriture de
 * journal n'est conservee.</p>
 *
 * <p>Le verrouillage ordonne par {@code lockKey} evite les interblocages quand
 * deux virements croises (A vers B et B vers A) sont executes en parallele.</p>
 */
public final class EconomyRepository {

    /**
     * Execute un mouvement de monnaie atomique.
     *
     * @param connection connexion transactionnelle (obligatoire)
     * @param source     compte debite (systeme pour une creation de monnaie)
     * @param target     compte credite (systeme pour une destruction de monnaie)
     * @param amount     montant strictement positif
     * @param type       nature de l'operation
     * @param reason     motif lisible journalise
     * @return l'ecriture de journal correspondante
     * @throws SQLException               en cas d'erreur SQL
     * @throws InsufficientFundsException si le compte source est insuffisamment approvisionne
     */
    public TransactionRecord transfer(Connection connection, EconomyActor source, EconomyActor target,
                                      long amount, TransactionType type, String reason)
            throws SQLException {
        if (amount <= 0) {
            throw new EconomyException("le montant doit etre strictement positif");
        }
        if (source.lockKey().equals(target.lockKey())) {
            throw new EconomyException("le compte source et le compte destination sont identiques");
        }

        // 1. Verrouillage deterministe des comptes persistants
        List<EconomyActor> toLock = new ArrayList<>(2);
        if (source.persisted()) {
            toLock.add(source);
        }
        if (target.persisted()) {
            toLock.add(target);
        }
        toLock.sort(Comparator.comparing(EconomyActor::lockKey));

        Map<String, Long> balances = new HashMap<>();
        for (EconomyActor actor : toLock) {
            balances.put(actor.lockKey(), readBalance(connection, actor, true));
        }

        // 2. Verification des fonds avant toute ecriture
        long sourceBefore = source.persisted() ? balances.get(source.lockKey()) : 0L;
        if (source.persisted() && sourceBefore < amount) {
            throw new InsufficientFundsException(source, amount, sourceBefore);
        }
        long targetBefore = target.persisted() ? balances.get(target.lockKey()) : 0L;

        // 3. Application des deltas
        if (source.persisted()) {
            applyDelta(connection, source, -amount);
        }
        if (target.persisted()) {
            applyDelta(connection, target, amount);
        }

        Long sourceAfter = source.persisted() ? sourceBefore - amount : null;
        Long targetAfter = target.persisted() ? targetBefore + amount : null;

        // 4. Journalisation
        long journalId = insertJournal(connection, type, source, target, amount, reason,
                sourceAfter, targetAfter);

        return new TransactionRecord(journalId, type, source, target, amount, reason, sourceAfter,
                targetAfter, Instant.now());
    }

    /**
     * @param connection connexion
     * @param actor      compte
     * @return le solde courant
     * @throws SQLException en cas d'erreur SQL
     */
    public long balanceOf(Connection connection, EconomyActor actor) throws SQLException {
        return readBalance(connection, actor, false);
    }

    private long readBalance(Connection connection, EconomyActor actor, boolean lock)
            throws SQLException {
        if (!actor.persisted()) {
            throw new EconomyException("le compte systeme ne possede pas de solde");
        }
        try (PreparedStatement statement = connection.prepareStatement(balanceSql(actor, lock))) {
            bindAccount(statement, 1, actor);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new EconomyException("compte economique introuvable: " + actor.lockKey());
                }
                return resultSet.getLong(1);
            }
        }
    }

    private void applyDelta(Connection connection, EconomyActor actor, long delta) throws SQLException {
        String sql = switch (actor) {
            case EconomyActor.PlayerActor ignored ->
                    "UPDATE player_profiles SET balance = balance + ?, updated_at = now() WHERE uuid = ?";
            case EconomyActor.NationActor ignored ->
                    "UPDATE nations SET treasury = treasury + ? WHERE id = ?";
            case EconomyActor.SystemActor ignored ->
                    throw new EconomyException("le compte systeme ne peut pas etre modifie");
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, delta);
            bindAccount(statement, 2, actor);
            if (statement.executeUpdate() != 1) {
                throw new EconomyException("mise a jour du compte impossible: " + actor.lockKey());
            }
        }
    }

    private String balanceSql(EconomyActor actor, boolean lock) {
        String base = switch (actor) {
            case EconomyActor.PlayerActor ignored ->
                    "SELECT balance FROM player_profiles WHERE uuid = ?";
            case EconomyActor.NationActor ignored ->
                    "SELECT treasury FROM nations WHERE id = ?";
            case EconomyActor.SystemActor ignored ->
                    throw new EconomyException("le compte systeme ne possede pas de solde");
        };
        return lock ? base + " FOR UPDATE" : base;
    }

    private void bindAccount(PreparedStatement statement, int index, EconomyActor actor)
            throws SQLException {
        switch (actor) {
            case EconomyActor.PlayerActor player -> SqlSupport.setUuid(statement, index, player.uuid());
            case EconomyActor.NationActor nation -> statement.setLong(index, nation.nationId());
            case EconomyActor.SystemActor ignored ->
                    throw new EconomyException("le compte systeme n'est pas adressable en base");
        }
    }

    private long insertJournal(Connection connection, TransactionType type, EconomyActor source,
                               EconomyActor target, long amount, String reason, Long sourceAfter,
                               Long targetAfter) throws SQLException {
        String sql = """
                INSERT INTO economy_transactions
                    (type, source_kind, source_id, target_kind, target_id, amount, reason,
                     source_balance_after, target_balance_after)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, type.name());
            statement.setString(2, source.kind());
            statement.setString(3, source.identifier());
            statement.setString(4, target.kind());
            statement.setString(5, target.identifier());
            statement.setLong(6, amount);
            statement.setString(7, reason);
            if (sourceAfter == null) {
                statement.setNull(8, java.sql.Types.BIGINT);
            } else {
                statement.setLong(8, sourceAfter);
            }
            if (targetAfter == null) {
                statement.setNull(9, java.sql.Types.BIGINT);
            } else {
                statement.setLong(9, targetAfter);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("ecriture du journal economique sans identifiant genere");
    }
}
