package com.kronyxgames.lesyria.application.nation;

import com.kronyxgames.lesyria.domain.nation.Nation;
import com.kronyxgames.lesyria.domain.nation.NationMember;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache memoire des nations et des adhesions.
 *
 * <p>Indispensable : la resolution des permissions territoriales se fait sur le
 * thread principal, a chaque bloc casse ou pose. Passer par la base de donnees
 * serait impossible ; on maintient donc un index politique en memoire,
 * construit au demarrage et mis a jour apres chaque operation validee.</p>
 */
public final class NationCache {

    private final Map<Long, Nation> nations = new ConcurrentHashMap<>();
    private final Map<UUID, NationMember> memberships = new ConcurrentHashMap<>();
    private final Map<Long, Set<UUID>> membersByNation = new ConcurrentHashMap<>();
    private final Map<String, Long> idsByName = new ConcurrentHashMap<>();

    /**
     * Recharge entierement le cache (demarrage et rechargement).
     *
     * @param loadedNations nations persistees
     * @param loadedMembers adhesions persistees
     */
    public void load(List<Nation> loadedNations, List<NationMember> loadedMembers) {
        clear();
        loadedNations.forEach(this::putNation);
        loadedMembers.forEach(this::putMember);
    }

    /** Vide le cache. */
    public void clear() {
        nations.clear();
        memberships.clear();
        membersByNation.clear();
        idsByName.clear();
    }

    /**
     * @param nation nation a referencer
     */
    public void putNation(Nation nation) {
        Nation previous = nations.put(nation.id(), nation);
        if (previous != null) {
            idsByName.remove(previous.name().toLowerCase(Locale.ROOT));
            idsByName.remove(previous.tag().toLowerCase(Locale.ROOT));
        }
        idsByName.put(nation.name().toLowerCase(Locale.ROOT), nation.id());
        idsByName.put(nation.tag().toLowerCase(Locale.ROOT), nation.id());
    }

    /**
     * @param nationId nation a retirer
     */
    public void removeNation(long nationId) {
        Nation removed = nations.remove(nationId);
        if (removed != null) {
            idsByName.remove(removed.name().toLowerCase(Locale.ROOT));
            idsByName.remove(removed.tag().toLowerCase(Locale.ROOT));
        }
        Set<UUID> members = membersByNation.remove(nationId);
        if (members != null) {
            members.forEach(memberships::remove);
        }
    }

    /**
     * @param member adhesion a referencer
     */
    public void putMember(NationMember member) {
        NationMember previous = memberships.put(member.uuid(), member);
        if (previous != null && previous.nationId() != member.nationId()) {
            Set<UUID> old = membersByNation.get(previous.nationId());
            if (old != null) {
                old.remove(member.uuid());
            }
        }
        membersByNation.computeIfAbsent(member.nationId(), key -> ConcurrentHashMap.newKeySet())
                .add(member.uuid());
    }

    /**
     * Met a jour la tresorerie en cache apres une operation economique reussie.
     *
     * @param nationId    nation concernee
     * @param newTreasury nouveau solde
     */
    public void applyTreasury(long nationId, long newTreasury) {
        Nation nation = nations.get(nationId);
        if (nation != null) {
            putNation(nation.withTreasury(newTreasury));
        }
    }

    /**
     * @param uuid joueur
     */
    public void removeMember(UUID uuid) {
        NationMember removed = memberships.remove(uuid);
        if (removed != null) {
            Set<UUID> members = membersByNation.get(removed.nationId());
            if (members != null) {
                members.remove(uuid);
            }
        }
    }

    /**
     * @param nationId nation recherchie
     * @return la nation, ou {@code null}
     */
    public Nation byId(Long nationId) {
        return nationId == null ? null : nations.get(nationId);
    }

    /**
     * @param name    nom ou etiquette (insensible a la casse)
     * @return la nation, ou {@code null}
     */
    public Nation byName(String name) {
        if (name == null) {
            return null;
        }
        Long id = idsByName.get(name.trim().toLowerCase(Locale.ROOT));
        return id == null ? null : nations.get(id);
    }

    /**
     * @param uuid joueur
     * @return son adhesion, ou {@code null}
     */
    public NationMember membershipOf(UUID uuid) {
        return uuid == null ? null : memberships.get(uuid);
    }

    /**
     * @param nationId nation
     * @return les membres connus
     */
    public Set<UUID> membersOf(long nationId) {
        return membersByNation.getOrDefault(nationId, Set.of());
    }

    /**
     * @param nationId nation
     * @return le nombre de membres
     */
    public int memberCount(long nationId) {
        return membersOf(nationId).size();
    }

    /** @return toutes les nations en cache. */
    public Collection<Nation> all() {
        return nations.values();
    }

    /** @return le nombre de nations en cache. */
    public int nationCount() {
        return nations.size();
    }

    /** @return le nombre d'adhesions en cache. */
    public int membershipCount() {
        return memberships.size();
    }
}
