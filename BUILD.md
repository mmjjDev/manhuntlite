# ManhuntLite - Build Instructions

## Requirements
- **Java 21** (JDK, not JRE) — [Download Adoptium](https://adoptium.net/temurin/releases/?version=21)
- **Internet connection** (first build downloads ~300MB of Minecraft + Fabric dependencies)

## One-Command Build

### Linux / macOS
```bash
cd manhuntlite
chmod +x gradlew
./gradlew build
```

### Windows
```cmd
cd manhuntlite
gradlew.bat build
```

## Output
The compiled mod JAR will be at:
```
build/libs/manhuntlite-1.0.0.jar
```

Drop this file into your Fabric server's `mods/` folder.

## Server Requirements
- Minecraft **1.21.11** server with **Fabric Loader 0.18.4+**
- **Fabric API** mod installed (download from [Modrinth](https://modrinth.com/mod/fabric-api/versions?g=1.21.11))

## First Build Notes
- First build takes 2-5 minutes (downloads + decompiles Minecraft)
- Subsequent builds take ~10 seconds
- If you get OOM errors, set: `export GRADLE_OPTS="-Xmx2g"`

## Troubleshooting

**"Could not resolve fabric-loom"**
→ Check internet connection. The Fabric Maven must be reachable.

**"Java version mismatch"**
→ Make sure `java -version` shows 21. Set `JAVA_HOME` to your JDK 21 path.

**"WaypointS2CPacket not found"**
→ This class is new in 1.21.11. Make sure gradle.properties has `minecraft_version=1.21.11`.
  If building for an older MC version, the tracking system needs to be rewritten to use
  compass-based tracking instead.
