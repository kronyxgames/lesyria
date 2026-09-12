package com.kronyxgames.lesyria.application.territory;

import com.kronyxgames.lesyria.domain.territory.ChunkKey;
import com.kronyxgames.lesyria.domain.territory.Territory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Index memoire {@code chunk -> territoire}.
 *
 * <p>C'est la piece qui rend le gameplay territorial compatible avec les
 * performances d'un serveur Minecraft : savoir a qui appartient un chunk est une
 * lecture de {@link Map}, pas une requete SQL. Aucun evenement Bukkit ne doit
 * declencher de requete base de donnees.</p>
 *
 * <p>La taille est bornee par construction :</p>
 * <ul>
 *   <li>un territoire est un rectangle de chunks plafonne par la configuration ;</li>
 *   <li>le nombre de territoires par nation est plafonne.</li>
 * </ul>
 */
public final class TerritoryIndex {

    private final Map<ChunkKey, Long> chunkToTerritory = new ConcurrentHashMap<>();
    private final Map<Long, Territory> territories = new ConcurrentHashMap<>();

    /**
     * Indexe un territoire (idempotent : reindexe ses chunks).
     *
     * @param territory territoire a indexer
     */
    public void index(Territory territory) {
        if (territory.id() == null) {
            throw new IllegalArgumentException("un territoire non persiste ne peut pas etre indexe");
        }
        territories.put(territory.id(), territory);
        for (int x = territory.minChunkX(); x <= territory.maxChunkX(); x++) {
            for (int z = territory.minChunkZ(); z <= territory.maxChunkZ(); z++) {
                chunkToTerritory.put(new ChunkKey(territory.world(), x, z), territory.id());
            }
        }
    }

    /**
     * Retire un territoire de l'index.
     *
     * @param territoryId identifiant du territoire
     */
    public void unindex(long territoryId) {
        Territory territory = territories.remove(territoryId);
        if (territory == null) {
            return;
        }
        for (int x = territory.minChunkX(); x <= territory.maxChunkX(); x++) {
            for (int z = territory.minChunkZ(); z <= territory.maxChunkZ(); z++) {
                chunkToTerritory.remove(new ChunkKey(territory.world(), x, z), territoryId);
            }
        }
    }

    /**
     * @param world nom du monde
     * @param x     chunk X
     * @param z     chunk Z
     * @return le territoire du chunk, ou {@code null} en zone sauvage
     */
    public Territory at(String world, int x, int z) {
        Long id = chunkToTerritory.get(new ChunkKey(world, x, z));
        return id == null ? null : territories.get(id);
    }

    /**
     * @param territoryId identifiant du territoire
     * @return le territoire, ou {@code null}
     */
    public Territory byId(long territoryId) {
        return territories.get(territoryId);
    }

    /**
     * @param nationId nation
     * @return les territoires de cette nation
     */
    public List<Territory> ofNation(long nationId) {
        List<Territory> result = new ArrayList<>();
        for (Territory territory : territories.values()) {
            if (territory.nationId() == nationId) {
                result.add(territory);
            }
        }
        return result;
    }

    /**
     * Verifie qu'aucun chunk du candidat n'est deja revendique.
     *
     * @param candidate territoire candidat
     * @return vrai si toute la zone est libre
     */
    public boolean isFree(Territory candidate) {
        for (int x = candidate.minChunkX(); x <= candidate.maxChunkX(); x++) {
            for (int z = candidate.minChunkZ(); z <= candidate.maxChunkZ(); z++) {
                if (chunkToTerritory.containsKey(new ChunkKey(candidate.world(), x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Vide l'index. */
    public void clear() {
        chunkToTerritory.clear();
        territories.clear();
    }

    /** @return tous les territoires indexes. */
    public Collection<Territory> all() {
        return territories.values();
    }

    /** @return le nombre de territoires indexes. */
    public int size() {
        return territories.size();
    }

    /** @return le nombre de chunks revendiques. */
    public int chunkCount() {
        return chunkToTerritory.size();
    }
}
