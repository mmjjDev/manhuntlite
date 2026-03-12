package dev.manhunt.tracking;

import dev.manhunt.ManhuntLite;
import dev.manhunt.team.TeamManager;
import net.minecraft.network.packet.s2c.play.WaypointS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.waypoint.Waypoint;

import java.util.*;

/**
 * Manages the Player Locator Bar tracking system.
 *
 * Uses WaypointS2CPacket to send server-authoritative waypoints to hunter clients.
 * Hunters see ONLY runners on their locator bar — all other player waypoints are suppressed.
 *
 * Cross-dimension tracking:
 * - Same dimension: shows runner's real-time position
 * - Different dimension: shows runner's last known portal location
 *
 * Update frequency: every 20 ticks (1 second) to avoid packet spam.
 */
public class TrackingManager {

    // Tracks which runner UUIDs each hunter is currently tracking
    // Key: hunter UUID, Value: set of runner UUIDs being tracked
    private final Map<UUID, Set<UUID>> activeTracking = new HashMap<>();

    // Stores last known portal positions for cross-dimension tracking
    // Key: player UUID, Value: last portal block position in the dimension they left
    private final Map<UUID, PortalRecord> lastPortalPositions = new HashMap<>();

    // Update every 20 ticks (1 second)
    private int tickCounter = 0;
    private static final int UPDATE_INTERVAL = 20;

    // Runner waypoint color: bright green (0x55FF55)
    private static final int RUNNER_COLOR = 0x55FF55;

    /**
     * Records a player's last portal usage for cross-dimension tracking.
     */
    public record PortalRecord(BlockPos position, RegistryKey<World> dimensionKey) {}

    public void init(MinecraftServer server) {
        activeTracking.clear();
        lastPortalPositions.clear();
        tickCounter = 0;
    }

    // ─── Tracking Lifecycle ──────────────────────────────────────

    /**
     * Called when the game starts. Sends initial TRACK packets to all hunters
     * for each runner, and UNTRACK packets to suppress non-runner waypoints.
     */
    public void startTracking(MinecraftServer server, TeamManager teamManager) {
        Set<UUID> runners = teamManager.getRunners();
        Set<UUID> hunters = teamManager.getHunters();

        for (UUID hunterUuid : hunters) {
            ServerPlayerEntity hunter = server.getPlayerManager().getPlayer(hunterUuid);
            if (hunter == null) continue;

            Set<UUID> trackedRunners = new HashSet<>();

            // Untrack ALL players first to clear the locator bar
            for (ServerPlayerEntity allPlayer : server.getPlayerManager().getPlayerList()) {
                if (!runners.contains(allPlayer.getUuid())) {
                    sendUntrack(hunter, allPlayer.getUuid());
                }
            }

            // Send TRACK for each runner
            for (UUID runnerUuid : runners) {
                ServerPlayerEntity runner = server.getPlayerManager().getPlayer(runnerUuid);
                if (runner == null) continue;

                Vec3i pos = getTrackingPosition(hunter, runner);
                Waypoint.Config config = createRunnerConfig();
                sendTrackPos(hunter, runnerUuid, config, pos);
                trackedRunners.add(runnerUuid);
            }

            activeTracking.put(hunterUuid, trackedRunners);
        }

        // Runners should NOT see other runners or hunters on their locator bar
        // Untrack all players for each runner
        for (UUID runnerUuid : runners) {
            ServerPlayerEntity runner = server.getPlayerManager().getPlayer(runnerUuid);
            if (runner == null) continue;

            for (ServerPlayerEntity allPlayer : server.getPlayerManager().getPlayerList()) {
                if (!allPlayer.getUuid().equals(runnerUuid)) {
                    sendUntrack(runner, allPlayer.getUuid());
                }
            }
        }

        ManhuntLite.LOGGER.info("[ManhuntLite] Tracking started: {} hunters tracking {} runners",
                hunters.size(), runners.size());
    }

    /**
     * Called every server tick. Updates runner positions for all hunters.
     */
    public void tick(MinecraftServer server, TeamManager teamManager) {
        tickCounter++;
        if (tickCounter < UPDATE_INTERVAL) return;
        tickCounter = 0;

        Set<UUID> hunters = teamManager.getHunters();
        Set<UUID> aliveRunners = teamManager.getAliveRunners();

        for (UUID hunterUuid : hunters) {
            ServerPlayerEntity hunter = server.getPlayerManager().getPlayer(hunterUuid);
            if (hunter == null) continue;

            Set<UUID> tracked = activeTracking.getOrDefault(hunterUuid, Set.of());

            for (UUID runnerUuid : aliveRunners) {
                ServerPlayerEntity runner = server.getPlayerManager().getPlayer(runnerUuid);
                if (runner == null) continue;

                Vec3i pos = getTrackingPosition(hunter, runner);
                Waypoint.Config config = createRunnerConfig();

                if (tracked.contains(runnerUuid)) {
                    // Already tracking — send UPDATE
                    sendUpdatePos(hunter, runnerUuid, config, pos);
                } else {
                    // New runner (joined mid-game?) — send TRACK
                    sendTrackPos(hunter, runnerUuid, config, pos);
                    activeTracking.computeIfAbsent(hunterUuid, k -> new HashSet<>()).add(runnerUuid);
                }
            }

            // Untrack eliminated runners
            Set<UUID> toRemove = new HashSet<>();
            for (UUID trackedUuid : tracked) {
                if (!aliveRunners.contains(trackedUuid)) {
                    sendUntrack(hunter, trackedUuid);
                    toRemove.add(trackedUuid);
                }
            }
            tracked.removeAll(toRemove);

            // Suppress any new player waypoints that aren't runners
            for (ServerPlayerEntity allPlayer : server.getPlayerManager().getPlayerList()) {
                UUID pid = allPlayer.getUuid();
                if (!aliveRunners.contains(pid) && !pid.equals(hunterUuid)) {
                    sendUntrack(hunter, pid);
                }
            }
        }
    }

    /**
     * Stops all tracking. Sends UNTRACK for every tracked waypoint.
     */
    public void stopTracking(MinecraftServer server) {
        for (var entry : activeTracking.entrySet()) {
            ServerPlayerEntity hunter = server.getPlayerManager().getPlayer(entry.getKey());
            if (hunter == null) continue;

            for (UUID runnerUuid : entry.getValue()) {
                sendUntrack(hunter, runnerUuid);
            }
        }
        activeTracking.clear();
        lastPortalPositions.clear();
        tickCounter = 0;
    }

    // ─── Cross-Dimension Tracking ────────────────────────────────

    /**
     * Records a player's position when they enter a portal.
     * Called from a mixin or event when a player changes dimension.
     */
    public void onPlayerChangeDimension(ServerPlayerEntity player, BlockPos portalPos,
                                         RegistryKey<World> fromDimension) {
        lastPortalPositions.put(player.getUuid(), new PortalRecord(portalPos, fromDimension));
    }

    /**
     * Determines the position to show on the hunter's locator bar.
     *
     * Rules:
     * - Same dimension: runner's real block position
     * - Different dimension: runner's last portal position
     *   (scaled if Nether<->Overworld: Nether coords * 8 = Overworld coords)
     */
    private Vec3i getTrackingPosition(ServerPlayerEntity hunter, ServerPlayerEntity runner) {
        // Same dimension — direct tracking
        if (hunter.getWorld().getRegistryKey().equals(runner.getWorld().getRegistryKey())) {
            return runner.getBlockPos();
        }

        // Different dimensions — use last portal position
        PortalRecord record = lastPortalPositions.get(runner.getUuid());
        if (record != null && record.dimensionKey().equals(hunter.getWorld().getRegistryKey())) {
            // Runner's last position was in the hunter's current dimension
            return record.position();
        }

        // Fallback: calculate approximate cross-dimension position
        return calculateCrossDimensionPos(hunter, runner);
    }

    /**
     * Calculates cross-dimension position with Nether coordinate scaling.
     * Nether coords * 8 = Overworld coords.
     */
    private Vec3i calculateCrossDimensionPos(ServerPlayerEntity hunter, ServerPlayerEntity runner) {
        BlockPos runnerPos = runner.getBlockPos();
        boolean hunterInNether = hunter.getWorld().getRegistryKey().equals(World.NETHER);
        boolean runnerInNether = runner.getWorld().getRegistryKey().equals(World.NETHER);

        if (hunterInNether && !runnerInNether) {
            // Hunter in Nether, runner in Overworld/End
            // Scale Overworld coords down to Nether scale
            return new Vec3i(runnerPos.getX() / 8, runnerPos.getY(), runnerPos.getZ() / 8);
        } else if (!hunterInNether && runnerInNether) {
            // Hunter in Overworld/End, runner in Nether
            // Scale Nether coords up to Overworld scale
            return new Vec3i(runnerPos.getX() * 8, runnerPos.getY(), runnerPos.getZ() * 8);
        }

        // End <-> Overworld or other combos: use raw coords
        return runnerPos;
    }

    // ─── Packet Helpers ──────────────────────────────────────────

    /**
     * Creates a Waypoint.Config for runner waypoints.
     * Uses bright green color to distinguish runners.
     */
    private Waypoint.Config createRunnerConfig() {
        return new Waypoint.Config(
                Waypoint.Config.DEFAULT.style(),
                Optional.of(RUNNER_COLOR)
        );
    }

    /**
     * Sends a TRACK_POS packet to start showing a waypoint.
     */
    private void sendTrackPos(ServerPlayerEntity target, UUID source, Waypoint.Config config, Vec3i pos) {
        try {
            WaypointS2CPacket packet = WaypointS2CPacket.trackPos(source, config, pos);
            target.networkHandler.sendPacket(packet);
        } catch (Exception e) {
            ManhuntLite.LOGGER.warn("[ManhuntLite] Failed to send trackPos packet: {}", e.getMessage());
        }
    }

    /**
     * Sends an UPDATE_POS packet to update an existing waypoint's position.
     */
    private void sendUpdatePos(ServerPlayerEntity target, UUID source, Waypoint.Config config, Vec3i pos) {
        try {
            WaypointS2CPacket packet = WaypointS2CPacket.updatePos(source, config, pos);
            target.networkHandler.sendPacket(packet);
        } catch (Exception e) {
            ManhuntLite.LOGGER.warn("[ManhuntLite] Failed to send updatePos packet: {}", e.getMessage());
        }
    }

    /**
     * Sends an UNTRACK packet to remove a waypoint from the locator bar.
     */
    private void sendUntrack(ServerPlayerEntity target, UUID source) {
        try {
            WaypointS2CPacket packet = WaypointS2CPacket.untrack(source);
            target.networkHandler.sendPacket(packet);
        } catch (Exception e) {
            ManhuntLite.LOGGER.warn("[ManhuntLite] Failed to send untrack packet: {}", e.getMessage());
        }
    }
}
