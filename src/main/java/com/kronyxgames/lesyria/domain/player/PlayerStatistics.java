package com.kronyxgames.lesyria.domain.player;

/**
 * Statistiques persistantes d'un joueur.
 *
 * @param kills           joueurs tues
 * @param deaths          morts subies
 * @param blocksPlaced    blocs poses
 * @param blocksBroken    blocs casses
 * @param questsCompleted quetes terminees
 */
public record PlayerStatistics(
        int kills,
        int deaths,
        long blocksPlaced,
        long blocksBroken,
        int questsCompleted) {

    public static PlayerStatistics empty() {
        return new PlayerStatistics(0, 0, 0, 0, 0);
    }

    public PlayerStatistics withKill() {
        return new PlayerStatistics(kills + 1, deaths, blocksPlaced, blocksBroken, questsCompleted);
    }

    public PlayerStatistics withDeath() {
        return new PlayerStatistics(kills, deaths + 1, blocksPlaced, blocksBroken, questsCompleted);
    }

    public PlayerStatistics withBlockPlaced() {
        return new PlayerStatistics(kills, deaths, blocksPlaced + 1, blocksBroken, questsCompleted);
    }

    public PlayerStatistics withBlockBroken() {
        return new PlayerStatistics(kills, deaths, blocksPlaced, blocksBroken + 1, questsCompleted);
    }

    public PlayerStatistics withQuestCompleted() {
        return new PlayerStatistics(kills, deaths, blocksPlaced, blocksBroken, questsCompleted + 1);
    }

    /** @return ratio tue/mort, 0 si le joueur n'est jamais mort. */
    public double killDeathRatio() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }
}
