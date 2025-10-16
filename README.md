# Lesyria Minecraft Server

A custom Minecraft MMORPG server plugin written in Java, featuring an open-world Earth at 1:1 scale, supporting both Java Edition and Bedrock Edition clients via GeyserMC.

## Features

- **Earth-Scale World**: Custom world generator for a 1:1 scale Earth (40,000 km diameter).
- Cross-platform support (Java and Bedrock)
- **Economy System**: Virtual currency (Lesyria Coins), buy/sell items.
- **Quest System**: Complete quests for rewards (e.g., gather items).
- **NPC System**: Quest givers and shopkeepers in cities.
- Custom commands: /home, /spawn, /eco, /quest
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
- `/eco balance`: Check your Lesyria Coins balance.
- `/eco buy <item> <qty>`: Buy items (e.g., /eco buy diamond 1).
- `/eco sell <item> <qty>`: Sell items.
- `/quest start <quest_name>`: Start a quest (e.g., /quest start gather_wood).
- `/quest complete <quest_name>`: Complete a quest if requirements met.

Available cities: paris, london, tokyo.
Available quests: gather_wood, mine_coal.

## World Scale

The world is generated at 1:1 scale with the real Earth:
- 1 block = 1 meter
- Earth circumference: ~40,075 km
- Minecraft world limit: ~60,000 km (sufficient for Earth)

## NPCs and Gameplay

- **Quest NPCs**: Interact with "Quest Master" in cities to get quest info.
- **Shop NPCs**: Buy/sell items with "Shopkeeper".
- Starting money: 100 Lesyria Coins on join.

## Documentation

Detailed documentation is available in the `docs/` folder:

- [Deployment Guide](docs/DEPLOYMENT.md) - How to set up and run the server
- [Commands Reference](docs/COMMANDS.md) - All available commands
- [Features Overview](docs/FEATURES.md) - Detailed feature descriptions
- [Architecture](docs/ARCHITECTURE.md) - Code structure and design
- [Domain & Hosting](docs/DOMAIN_HOSTING.md) - Setting up play.lesyria.com and hosting

## Launchers

- **Java Launcher**: Basic Swing app in `launcher/` folder. Build with Maven.
- **Electron Launcher**: Desktop app with TypeScript in `electron-launcher/` folder. Uses API for server info.

## API

REST API exposed on port 8080 with endpoints:
- `/status`: Server status and player count
- `/news`: Latest news
- `/economy/rates`: Item prices

## Development

This plugin is in early development. Future features include:
- Real topography generation using SRTM data
- Advanced NPC interactions and GUI
- Database persistence
- More quests, cities, and content

Contributions are welcome!