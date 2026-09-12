package com.kronyxgames.lesyria.domain.economy;

import java.util.Objects;
import java.util.UUID;

/**
 * Titulaire d'un compte economique.
 *
 * <p>Trois natures de comptes existent dans Lesyria : les joueurs, les nations
 * (tresorerie) et le systeme (source ou puits de monnaie : recompenses,
 * administration, marche). Les operations entre comptes sont atomiques et
 * suivent le meme chemin de code, ce qui evite les incoherences.</p>
 */
public sealed interface EconomyActor
        permits EconomyActor.PlayerActor, EconomyActor.NationActor, EconomyActor.SystemActor {

    /** @return le type de compte, tel que stocke dans le journal economique. */
    String kind();

    /** @return l'identifiant textuel du compte. */
    String identifier();

    /**
     * Cle utilisee pour ordonner les verrous en base et eviter tout interblocage.
     *
     * @return une cle unique et stable
     */
    default String lockKey() {
        return kind() + ':' + identifier();
    }

    /** @return vrai si le compte possede une ligne en base (donc un solde a verrouiller). */
    default boolean persisted() {
        return true;
    }

    /**
     * @param uuid identifiant du joueur
     * @return un compte joueur
     */
    static EconomyActor player(UUID uuid) {
        return new PlayerActor(uuid);
    }

    /**
     * @param nationId identifiant de la nation
     * @return la tresorerie de la nation
     */
    static EconomyActor nation(long nationId) {
        return new NationActor(nationId);
    }

    /**
     * @param label raison lisible (ex: {@code marche}, {@code administration})
     * @return le compte systeme
     */
    static EconomyActor system(String label) {
        return new SystemActor(label);
    }

    /** Compte d'un joueur. */
    record PlayerActor(UUID uuid) implements EconomyActor {

        public PlayerActor {
            Objects.requireNonNull(uuid, "uuid");
        }

        @Override
        public String kind() {
            return "PLAYER";
        }

        @Override
        public String identifier() {
            return uuid.toString();
        }
    }

    /** Tresorerie d'une nation. */
    record NationActor(long nationId) implements EconomyActor {

        @Override
        public String kind() {
            return "NATION";
        }

        @Override
        public String identifier() {
            return Long.toString(nationId);
        }
    }

    /**
     * Compte systeme : il n'a pas de solde, il cree ou detruit de la monnaie.
     */
    record SystemActor(String label) implements EconomyActor {

        public SystemActor {
            Objects.requireNonNull(label, "label");
        }

        @Override
        public String kind() {
            return "SYSTEM";
        }

        @Override
        public String identifier() {
            return label;
        }

        @Override
        public boolean persisted() {
            return false;
        }
    }
}
