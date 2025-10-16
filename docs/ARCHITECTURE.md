# Lesyria Architecture

This document describes the code architecture of the Lesyria plugin.

## Project Structure

```
lesyria/
├── src/main/java/com/kronyxgames/lesyria/
│   ├── LesyriaPlugin.java          # Main plugin class
│   ├── EarthWorldGenerator.java    # World generation
│   ├── HomeCommand.java            # /home command
│   ├── SpawnCommand.java           # /spawn command
│   ├── EconomyManager.java         # Economy logic
│   ├── EconomyCommand.java         # /eco command
│   ├── Quest.java                  # Quest data model
│   ├── QuestManager.java           # Quest logic
│   ├── QuestCommand.java           # /quest command
│   ├── NPCManager.java             # NPC spawning
│   └── EarthBiomeProvider.java     # Biome generation (inner class)
├── src/main/resources/
│   └── plugin.yml                  # Plugin metadata
├── docs/                           # Documentation
├── pom.xml                         # Maven build file
└── README.md                       # Project overview
```

## Key Classes

### LesyriaPlugin
- **Extends**: JavaPlugin
- **Implements**: Listener
- **Responsibilities**:
  - Plugin lifecycle (onEnable/onDisable)
  - Command registration
  - Event handling (player join)
  - World generator registration
  - Manager initialization

### Managers
- **EconomyManager**: Handles player balances, transactions
- **QuestManager**: Manages quest definitions and progress
- **NPCManager**: Spawns and manages NPCs

### Commands
- Each command has its own class implementing CommandExecutor
- Handle parsing, validation, and execution

### World Generation
- **EarthWorldGenerator**: Implements ChunkGenerator for terrain
- **EarthBiomeProvider**: Provides biomes (basic implementation)

## Data Flow

1. **Player Join**: EconomyManager gives starting money
2. **Commands**: Parsed by CommandExecutors, delegate to Managers
3. **Economy**: Transactions update player balances in memory
4. **Quests**: QuestManager checks completion, rewards via EconomyManager
5. **NPCs**: Spawned on enable, static for now

## Data Storage

- **Current**: In-memory HashMaps, persisted to YAML config
- **Planned**: Database (MySQL/PostgreSQL) for scalability

## Dependencies

- **Paper API**: Server integration
- **Bukkit**: Minecraft API
- **Maven Shade**: Fat JAR creation

## Extensibility

- Modular managers for easy feature addition
- Command pattern for new commands
- Event-driven architecture for interactions

## Performance Considerations

- World generation: Chunk-based, should scale
- Economy: In-memory, fast but not persistent across restarts
- NPCs: Static spawns, low overhead

## Security

- Input validation in commands
- No direct database access yet
- Standard Minecraft permissions

## Future Improvements

- Database integration
- Asynchronous operations
- Caching layers
- API for external integrations