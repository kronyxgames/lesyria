# Lesyria Minecraft Server

A custom Minecraft MMORPG server plugin written in Java, featuring an open-world Earth at 1:1 scale, supporting both Java Edition and Bedrock Edition clients via GeyserMC.

## Features

- **Earth-Scale World**: Custom world generator for a 1:1 scale Earth (40,000 km diameter).
- Cross-platform support (Java and Bedrock)
- Custom commands: /home, /spawn
- Modular architecture for easy extension

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Paper server (download from https://papermc.io/)

### Building

```bash
mvn clean package
```

This will create `target/lesyria-1.0-SNAPSHOT.jar`.

### Installation

1. Download and run a Paper server.
2. Create a new world named "earth" in server.properties (level-name=earth).
3. Place the built jar in the `plugins/` folder of your Paper server.
4. Install GeyserMC plugin for Bedrock support (download from https://download.geysermc.org/v2/projects/geysermc/members/versions/latest/builds/latest/downloads/spigot).
5. Start the server.

### Commands

- `/home`: Teleport to your set home location.
- `/home set`: Set your current location as home.
- `/spawn`: Teleport to spawn.
- `/spawn [city]`: Teleport to a major city (e.g., /spawn paris).

Available cities: paris, london, tokyo.

## World Scale

The world is generated at 1:1 scale with the real Earth:
- 1 block = 1 meter
- Earth circumference: ~40,075 km
- Minecraft world limit: ~60,000 km (sufficient for Earth)

## Development

This plugin is in early development. Future features include:
- Real topography generation
- NPC quests
- Economy system
- More cities and teleportation

Contributions are welcome!