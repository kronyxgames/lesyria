# Lesyria Features

Lesyria is a custom Minecraft MMORPG server featuring an open-world Earth at 1:1 scale.

## Core Features

### Earth-Scale World
- **Scale**: 1 block = 1 meter
- **Size**: Supports Earth's ~40,075 km circumference
- **Generation**: Custom ChunkGenerator for "earth" world
- **Current State**: Flat world; real topography planned

### Cross-Platform Support
- Java Edition and Bedrock Edition via GeyserMC
- Seamless connectivity

### Economy System
- **Currency**: Lesyria Coins
- **Starting Balance**: 100 coins on join
- **Trading**: Buy/sell items globally
- **Prices**: Dynamic (currently fixed)
  - Diamond: 100 coins
  - Iron Ingot: 20 coins
  - Coal: 5 coins

### Quest System
- **Quests Available**:
  - gather_wood: Collect 10 oak logs, reward 50 coins
  - mine_coal: Collect 5 coal, reward 25 coins
- **Mechanics**: Start/complete via commands
- **Future**: NPC-driven quests, storylines

### NPC System
- **Types**:
  - Quest Master: Provides quest info
  - Shopkeeper: Facilitates trading
- **Locations**: Spawned in major cities
- **Interaction**: Currently command-based; GUI planned

### Teleportation
- **Home System**: Set and teleport to personal home
- **City Teleport**: Instant travel to cities
- **Spawn**: Central hub

## Planned Features

- Real Earth topography using SRTM data
- Advanced quests with multiple objectives
- Guilds and factions
- Player housing and building
- Events and dungeons
- Multi-world support (space, underwater, etc.)

## Technical Features

- **Plugin Architecture**: Modular, extensible
- **Data Storage**: YAML configs (upgrade to database planned)
- **Performance**: Optimized for large worlds
- **Compatibility**: Paper API 1.20.4+

## Gameplay Loop

1. Join server, receive starting money
2. Explore the Earth-scale world
3. Complete quests for rewards
4. Trade items via economy
5. Build homes, interact with NPCs
6. Progress through content

## Balance and Economy

- Economy is player-driven
- Quests provide income
- Items have value based on rarity
- Inflation control via sinks (planned)