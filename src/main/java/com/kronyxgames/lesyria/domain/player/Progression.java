package com.kronyxgames.lesyria.domain.player;

/**
 * Courbe de progression des joueurs.
 *
 * <p>L'experience est une quantite <strong>cumulee</strong> : le niveau se
 * deduit de l'experience totale, ce qui evite toute desynchronisation entre les
 * deux colonnes en base.</p>
 *
 * <p>Courbe : atteindre le niveau {@code L} demande
 * {@code BASE * (L-1) * L / 2} points (niveau 2 : 100, niveau 3 : 300,
 * niveau 4 : 600, niveau 5 : 1000, niveau 10 : 4500).</p>
 */
public final class Progression {

    /** Experience necessaire pour passer du niveau 1 au niveau 2. */
    public static final long BASE_EXPERIENCE = 100L;

    /** Niveau maximum pris en compte (au dela, l'experience continue de s'accumuler). */
    public static final int MAX_LEVEL = 100;

    private Progression() {
    }

    /**
     * @param level niveau vise (1 = depart)
     * @return experience totale necessaire pour atteindre ce niveau
     */
    public static long cumulativeExperienceForLevel(int level) {
        if (level <= 1) {
            return 0L;
        }
        int capped = Math.min(level, MAX_LEVEL);
        return BASE_EXPERIENCE * (capped - 1L) * capped / 2L;
    }

    /**
     * @param experience experience cumulee
     * @return le niveau correspondant
     */
    public static int levelFor(long experience) {
        long safe = Math.max(0L, experience);
        int level = 1;
        while (level < MAX_LEVEL && safe >= cumulativeExperienceForLevel(level + 1)) {
            level++;
        }
        return level;
    }

    /**
     * @param experience experience cumulee
     * @return l'experience restante avant le niveau suivant, ou 0 au niveau max
     */
    public static long experienceToNextLevel(long experience) {
        int level = levelFor(experience);
        if (level >= MAX_LEVEL) {
            return 0L;
        }
        return cumulativeExperienceForLevel(level + 1) - Math.max(0L, experience);
    }

    /**
     * @param experience experience cumulee
     * @return la progression dans le niveau courant, entre 0 et 1
     */
    public static double levelProgress(long experience) {
        int level = levelFor(experience);
        if (level >= MAX_LEVEL) {
            return 1.0d;
        }
        long floor = cumulativeExperienceForLevel(level);
        long ceiling = cumulativeExperienceForLevel(level + 1);
        long span = ceiling - floor;
        if (span <= 0) {
            return 1.0d;
        }
        return Math.clamp((double) (experience - floor) / span, 0.0d, 1.0d);
    }
}
