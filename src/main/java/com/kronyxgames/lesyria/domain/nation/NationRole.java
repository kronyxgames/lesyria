package com.kronyxgames.lesyria.domain.nation;

import java.util.Locale;

/** Role d'un membre dans une nation. */
public enum NationRole {

    /** Chef de la nation : seul role a pouvoir dissoudre ou transferer la nation. */
    LEADER,
    /** Officier : peut revendiquer, inviter, exclure et gerer les guerres. */
    OFFICER,
    /** Membre simple. */
    MEMBER;

    /** @return vrai si le role peut gerer la nation (revendications, invitations...). */
    public boolean canManage() {
        return this == LEADER || this == OFFICER;
    }

    /**
     * @param raw valeur brute
     * @return le role correspondant
     * @throws IllegalArgumentException si inconnu
     */
    public static NationRole parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException("role inconnu: '" + raw + "'");
        }
    }
}
