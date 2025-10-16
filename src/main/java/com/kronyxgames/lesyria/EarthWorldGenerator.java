package com.kronyxgames.lesyria;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.Material;
import org.bukkit.block.Biome;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class EarthWorldGenerator extends ChunkGenerator {

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // Basic flat world for now - replace with real Earth topography later
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Set bedrock
                chunkData.setBlock(x, worldInfo.getMinHeight(), z, Material.BEDROCK);

                // Set grass layer
                for (int y = worldInfo.getMinHeight() + 1; y < 64; y++) {
                    chunkData.setBlock(x, y, z, Material.DIRT);
                }
                chunkData.setBlock(x, 64, z, Material.GRASS_BLOCK);
            }
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new EarthBiomeProvider();
    }

    private static class EarthBiomeProvider extends BiomeProvider {
        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            // Simple biome assignment - expand to real Earth biomes
            if (y > 60) {
                return Biome.PLAINS;
            } else {
                return Biome.OCEAN;
            }
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return Arrays.asList(Biome.PLAINS, Biome.OCEAN, Biome.FOREST, Biome.DESERT);
        }
    }
}