# Lesyria Server Deployment Guide

This guide explains how to deploy and run the Lesyria Minecraft MMORPG server.

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Paper server JAR (download from https://papermc.io/)
- GeyserMC plugin for Bedrock support (download from https://download.geysermc.org/)

## Building the Plugin

1. Clone or download the Lesyria project.
2. Navigate to the project directory.
3. Build the plugin:

```bash
mvn clean package
```

This creates `target/lesyria-1.0-SNAPSHOT.jar`.

## Server Setup

1. Create a new directory for your server (e.g., `lesyria-server`).
2. Download the latest Paper server JAR and place it in the directory.
3. Create `server.properties` with the following key settings:

```
level-name=earth
online-mode=true
max-players=100
difficulty=normal
```

4. Create a `plugins` folder and place the following JARs:
   - `lesyria-1.0-SNAPSHOT.jar`
   - `Geyser-Spigot.jar` (for Bedrock support)

5. Run the server for the first time:

```bash
java -Xmx4G -Xms4G -jar paper-1.20.4-xxx.jar nogui
```

This generates the world and config files. Stop the server after initial run.

## Configuration

### Plugin Configuration

The plugin uses `plugins/Lesyria/config.yml` for settings. Default values are set automatically.

### World Generation

The world "earth" uses a custom generator at 1:1 scale. Note: Real topography is not yet implemented; it's a flat world for now.

## Running the Server

Start the server:

```bash
java -Xmx4G -Xms4G -jar paper-1.20.4-xxx.jar nogui
```

For background running, use a process manager like `screen` or `systemd`.

## Connecting to the Server

- **Java Edition**: Connect to `localhost:25565` (or your server's IP).
- **Bedrock Edition**: Connect to the server's IP on port 19132 (GeyserMC default).

## Troubleshooting

- **World not generating**: Ensure `level-name=earth` in server.properties.
- **Plugin not loading**: Check console for errors; ensure Paper version matches.
- **Bedrock connection issues**: Verify GeyserMC is installed and configured.

## Performance Tips

- Allocate sufficient RAM (4-8GB recommended).
- Use a fast SSD for world storage.
- Monitor TPS with `/tps` command.

## Updating

1. Stop the server.
2. Replace the plugin JAR with the new version.
3. Start the server.
4. Backup world files before major updates.