package com.kronyxgames.lesyria.infrastructure.configuration;

import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig.ApiSettings;
import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig.CitySettings;
import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig.DatabaseSettings;
import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig.EconomySettings;
import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig.MessageSettings;
import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig.NationSettings;
import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig.PlayerSettings;
import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig.ProgressionSettings;
import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig.PvpSettings;
import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig.ServerSettings;
import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig.SpawnSettings;
import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig.TeleportSettings;
import com.kronyxgames.lesyria.infrastructure.configuration.LesyriaConfig.TerritorySettings;

import java.util.Objects;

/**
 * Porteur de configuration rechargeable.
 *
 * <p>{@link LesyriaConfig} reste un objet immuable (une photo de la
 * configuration). Ce porteur permet a {@code /lesyria reload} de remplacer
 * cette photo sans reconstruire les services : chaque service lit la
 * configuration courante a chaque appel.</p>
 *
 * <p>Ne sont pas rechargeables a chaud : l'acces a la base de donnees (pool deja
 * ouvert) et l'adresse d'ecoute de l'API HTTP (socket deja liee). Ces valeurs
 * sont signalees a l'administrateur lors du rechargement.</p>
 */
public final class ConfigHolder {

    private volatile LesyriaConfig current;

    public ConfigHolder(LesyriaConfig initial) {
        this.current = Objects.requireNonNull(initial, "initial");
    }

    /**
     * @param config nouvelle configuration
     */
    public void set(LesyriaConfig config) {
        this.current = Objects.requireNonNull(config, "config");
    }

    /** @return la configuration courante. */
    public LesyriaConfig get() {
        return current;
    }

    public ServerSettings server() {
        return current.server();
    }

    public SpawnSettings spawn() {
        return current.spawn();
    }

    public TeleportSettings teleport() {
        return current.teleport();
    }

    public EconomySettings economy() {
        return current.economy();
    }

    public TerritorySettings territory() {
        return current.territory();
    }

    public NationSettings nation() {
        return current.nation();
    }

    public CitySettings city() {
        return current.city();
    }

    public PvpSettings pvp() {
        return current.pvp();
    }

    public ProgressionSettings progression() {
        return current.progression();
    }

    public PlayerSettings player() {
        return current.player();
    }

    public DatabaseSettings database() {
        return current.database();
    }

    public ApiSettings api() {
        return current.api();
    }

    public MessageSettings messages() {
        return current.messages();
    }
}
