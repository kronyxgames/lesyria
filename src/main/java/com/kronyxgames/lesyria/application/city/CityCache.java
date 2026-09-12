package com.kronyxgames.lesyria.application.city;

import com.kronyxgames.lesyria.domain.city.City;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache memoire des villes.
 *
 * <p>Necessaire pour repondre instantanement a {@code /spawn &lt;ville&gt;} et
 * pour verifier la distance minimale entre deux villes sans requete SQL.</p>
 */
public final class CityCache {

    private final Map<Long, City> cities = new ConcurrentHashMap<>();
    private final Map<String, Long> idsByName = new ConcurrentHashMap<>();

    /**
     * @param loaded villes persistees
     */
    public void load(List<City> loaded) {
        clear();
        loaded.forEach(this::put);
    }

    /** Vide le cache. */
    public void clear() {
        cities.clear();
        idsByName.clear();
    }

    /**
     * @param city ville a referencer
     */
    public void put(City city) {
        City previous = cities.put(city.id(), city);
        if (previous != null) {
            idsByName.remove(previous.name().toLowerCase(Locale.ROOT));
        }
        idsByName.put(city.name().toLowerCase(Locale.ROOT), city.id());
    }

    /**
     * @param cityId identifiant
     */
    public void remove(long cityId) {
        City removed = cities.remove(cityId);
        if (removed != null) {
            idsByName.remove(removed.name().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * @param nationId nation
     */
    public void removeNation(long nationId) {
        new ArrayList<>(cities.values()).stream()
                .filter(city -> city.nationId() == nationId)
                .forEach(city -> remove(city.id()));
    }

    /**
     * @param name nom de ville (insensible a la casse)
     * @return la ville, ou {@code null}
     */
    public City byName(String name) {
        if (name == null) {
            return null;
        }
        Long id = idsByName.get(name.trim().toLowerCase(Locale.ROOT));
        return id == null ? null : cities.get(id);
    }

    /**
     * @param cityId identifiant
     * @return la ville, ou {@code null}
     */
    public City byId(long cityId) {
        return cities.get(cityId);
    }

    /**
     * @param nationId nation
     * @return ses villes
     */
    public List<City> ofNation(long nationId) {
        List<City> result = new ArrayList<>();
        for (City city : cities.values()) {
            if (city.nationId() == nationId) {
                result.add(city);
            }
        }
        return result;
    }

    /** @return toutes les villes connues. */
    public Collection<City> all() {
        return cities.values();
    }

    /** @return le nombre de villes en cache. */
    public int size() {
        return cities.size();
    }
}
