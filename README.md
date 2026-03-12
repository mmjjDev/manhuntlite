# ManhuntLite

Dream-style Manhunt mod for Minecraft 1.21.11 (Fabric). Server-side only.

## Features

- **Player Locator Bar Tracking** — Hunters see runners on the vanilla Player Locator Bar via `WaypointS2CPacket`. No client mods needed.
- **Cross-Dimension Tracking** — When a runner enters the Nether/End, hunters see the last portal position with proper coordinate scaling (8:1 Nether ratio).
- **Team Selection GUI** — Click the compass in the lobby to open a chest-based team picker (Runner/Hunter).
- **Ready System** — Glass pane toggle in hotbar slot 1. Game starts with 5-second countdown when all players are ready.
- **Lobby Protections** — Mixins prevent item drops, inventory manipulation, block breaking/placing, and PvP during lobby phase.
- **World Reset** — `/reset [seed]` command (OP-only) resets player states, inventories, world border, game rules, and time.
- **Win Conditions** — Runners win by killing the Ender Dragon. Hunters win by eliminating all runners.

## How It Works

### Game Flow
1. Players join → land in **LOBBY** with compass + ready toggle
2. Pick team (Runner or Hunter) via compass GUI
3. Toggle ready with glass pane → all ready triggers **5s countdown**
4. **RUNNING** — Hunters get real-time runner positions on their Locator Bar
5. **ENDED** — Dragon killed (runners win) or all runners eliminated (hunters win)
6. `/reset` to start over

### Tracking System
The mod uses Minecraft 1.21.11's new `WaypointS2CPacket` to send server-authoritative waypoints:
- `TRACK_POS` — Start showing a runner on a hunter's locator bar
- `UPDATE_POS` — Update position every second
- `UNTRACK` — Remove a waypoint (runner eliminated, game ends)

Runners see NO waypoints. Hunters see ONLY runners (non-runner player waypoints are suppressed).

## Project Structure

```
src/main/java/dev/manhunt/
├── ManhuntLite.java              # Mod entrypoint, event registration
├── game/
│   ├── GameState.java            # LOBBY → STARTING → RUNNING → ENDED
│   └── GameManager.java          # Central orchestrator, phase transitions
├── lobby/
│   └── LobbyManager.java         # Hotbar items (compass, ready toggle)
├── team/
│   ├── TeamManager.java          # Runner/Hunter assignments, chest GUI
│   └── TeamSelectionScreenHandler.java  # Click interception for team GUI
├── ready/
│   └── ReadyManager.java         # Ready toggle, countdown trigger
├── tracking/
│   └── TrackingManager.java      # WaypointS2CPacket-based locator bar tracking
├── command/
│   └── ResetCommand.java         # /reset [seed] operator command
├── world/
│   ├── WorldResetManager.java    # Soft reset (player states, game rules)
│   └── WaitingWorldManager.java  # Temporary waiting area during reset
└── mixin/
    ├── ItemDropMixin.java         # Prevent Q-key drops in lobby
    └── LobbyRestrictionMixin.java # Prevent inventory manipulation in lobby
```

## Installation

1. Build the JAR (see [BUILD.md](BUILD.md)) or download from releases
2. Install [Fabric Loader 0.18.4+](https://fabricmc.net/use/installer/) for Minecraft 1.21.11
3. Download [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=1.21.11) for 1.21.11
4. Drop both JARs into your server's `mods/` folder
5. Start the server — ManhuntLite loads automatically

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/reset` | OP (level 2) | Reset world with random seed |
| `/reset <seed>` | OP (level 2) | Reset world with specific seed |

## Configuration

No config file needed. The mod auto-configures game rules on reset:
- `locatorBar true` (required for tracking)
- `keepInventory false`
- `naturalRegeneration true`
- `doDaylightCycle true`
- Difficulty: Normal

## Technical Notes

- **Server-side only** — No client mod required. Works with vanilla clients on 1.21.11.
- **Fabric API dependency** — Uses: Events (lifecycle, player, entity), Networking, Commands v2.
- **Mixin targets** — `PlayerEntity.dropSelectedItem()` and `PlayerScreenHandler.onSlotClick()`.
- **Memory** — Minimal overhead. Tracking updates run once per second per hunter.

## License

MIT
