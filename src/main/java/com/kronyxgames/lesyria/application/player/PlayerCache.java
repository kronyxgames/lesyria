package com.kronyxgames.lesyria.application.player;

import com.kronyxgames.lesyria.domain.player.PlayerProfile;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache memoire des profils joueurs.
 *
 * <p>Le serveur doit pouvoir repondre <strong>sans requete SQL</strong> aux
 * questions posees sur le thread principal (solde affiche, domicile, nation
 * d'un joueur). Le cache est alimente a la connexion et mis a jour apres chaque
 * ecriture reussie.</p>
 *
 * <p>{@link #apply(PlayerProfile)} ne fait rien si le profil n'est pas deja en
 * cache : cela evite de "ressusciter" un joueur deconnecte depuis une
 * operation asynchrone terminee tardivement.</p>
 */
public final class PlayerCache {

    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();

    /**
     * @param uuid identifiant du joueur
     * @return le profil en cache, ou {@code null} s'il n'est pas charge
     */
    public PlayerProfile get(UUID uuid) {
        return uuid == null ? null : profiles.get(uuid);
    }

    /**
     * @param username pseudo (insensible a la casse)
     * @return le profil en cache, ou {@code null}
     */
    public PlayerProfile byName(String username) {
        if (username == null) {
            return null;
        }
        UUID uuid = byName.get(username.toLowerCase(java.util.Locale.ROOT));
        return uuid == null ? null : profiles.get(uuid);
    }

    /**
     * @param uuid identifiant du joueur
     * @return vrai si le profil est en cache
     */
    public boolean contains(UUID uuid) {
        return uuid != null && profiles.containsKey(uuid);
    }

    /**
     * Charge ou remplace un profil.
     *
     * @param profile profil a jour
     */
    public void put(PlayerProfile profile) {
        PlayerProfile previous = profiles.put(profile.uuid(), profile);
        if (previous != null && !previous.username().equalsIgnoreCase(profile.username())) {
            byName.remove(previous.username().toLowerCase(java.util.Locale.ROOT));
        }
        byName.put(profile.username().toLowerCase(java.util.Locale.ROOT), profile.uuid());
    }

    /**
     * Remplace le profil en cache uniquement s'il y est deja.
     *
     * @param profile profil a jour
     * @return vrai si le cache a ete mis a jour
     */
    public boolean apply(PlayerProfile profile) {
        if (!profiles.containsKey(profile.uuid())) {
            return false;
        }
        put(profile);
        return true;
    }

    /**
     * Met a jour le solde en cache d'un joueur (apres une transaction reussie).
     *
     * @param uuid       joueur
     * @param newBalance nouveau solde
     */
    public void applyBalance(UUID uuid, long newBalance) {
        PlayerProfile profile = profiles.get(uuid);
        if (profile != null) {
            profiles.put(uuid, profile.withBalance(newBalance));
        }
    }

    /**
     * @param uuid joueur
     */
    public void remove(UUID uuid) {
        PlayerProfile removed = profiles.remove(uuid);
        if (removed != null) {
            byName.remove(removed.username().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** @return les profils actuellement charges. */
    public Collection<PlayerProfile> all() {
        return profiles.values();
    }

    /** @return le nombre de profils charges. */
    public int size() {
        return profiles.size();
    }
}
