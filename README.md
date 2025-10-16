# Lesyria Minecraft Server

A custom Minecraft server plugin written in Java, supporting both Java Edition and Bedrock Edition clients via GeyserMC.

## Features

- Cross-platform support (Java and Bedrock)
- Custom commands like /home
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
2. Place the built jar in the `plugins/` folder of your Paper server.
3. Install GeyserMC plugin for Bedrock support (download from https://download.geysermc.org/v2/projects/geysermc/members/versions/latest/builds/latest/downloads/spigot).
4. Start the server.

### Commands

- `/home`: Teleport to your set home location.
- `/home set`: Set your current location as home.

## Development

This plugin is in early development. Contributions are welcome!