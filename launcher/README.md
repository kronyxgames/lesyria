# Lesyria Launcher

Custom launcher for easy connection to the Lesyria Minecraft server.

## Features

- Simple GUI for username input
- Auto-connect to play.lesyria.com
- Support for Java and Bedrock editions (Bedrock not fully implemented)

## Building

```bash
mvn clean package
```

Run with:
```bash
java -jar target/lesyria-launcher-1.0-SNAPSHOT.jar
```

## Note

This is a prototype launcher. Full implementation requires Minecraft authentication and proper game launching. For production, consider integrating with official launchers or using tools like MultiMC.