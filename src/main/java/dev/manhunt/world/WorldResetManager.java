package dev.manhunt.world;

import dev.manhunt.ManhuntLite;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.Difficulty;

import java.util.Set;

/**
 * Handles the world reset process.
 *
 * Full world regeneration with file deletion is extremely complex in a running server
 * and risks data corruption. Instead, this uses a practical approach:
 *
 * 1. Teleport all players to a safe waiting area high in the sky
 * 2. Set the world seed via level.dat manipulation (requires restart)
 * 3. OR: use the /seed command approach with world border reset
 *
 * For a production manhunt server, the recommended approach is:
 * - Stop the server
 * - Delete world folders (world, world_nether, world_the_end)
 * - Update server.properties with new seed
 * - Restart the server
 *
 * This manager provides a simplified in-game version that:
 * - Resets all player states
 * - Clears inventories
 * - Teleports to spawn
 * - Resets world border
 * - Sets game rules
 */
public class WorldResetManager {

    /**
     * Performs a soft reset:
     * - Resets all player states (health, hunger, XP, inventory)
     * - Teleports everyone to world spawn
     * - Configures game rules for manhunt
     * - Resets world border
     *
     * For a FULL world reset with new terrain, use the external reset script
     * (see README for details).
     */
    public static void performReset(MinecraftServer server, long seed) {
        ServerWorld overworld = server.getOverworld();

        // ─── Reset all player states ─────────────────────────────
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Clear everything
            player.getInventory().clear();
            player.clearStatusEffects();
            player.setHealth(player.getMaxHealth());
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(5.0f);
            player.setExperienceLevel(0);
            player.setExperiencePoints(0);
            player.setFireTicks(0);

            // Set to survival
            player.changeGameMode(GameMode.SURVIVAL);

            // Teleport to spawn
            BlockPos spawn = overworld.getSpawnPos();
            player.teleport(
                    overworld,
                    spawn.getX() + 0.5,
                    spawn.getY() + 1.0,
                    spawn.getZ() + 0.5,
                    Set.of(),
                    0.0f, 0.0f,
                    false
            );
        }

        // ─── Configure game rules for manhunt ────────────────────
        configureGameRules(server);

        // ─── Reset world border ──────────────────────────────────
        overworld.getWorldBorder().setCenter(0, 0);
        overworld.getWorldBorder().setSize(60000000); // Default max

        // ─── Set time to morning ─────────────────────────────────
        overworld.setTimeOfDay(0);

        // ─── Clear weather ───────────────────────────────────────
        overworld.setWeather(0, 0, false, false);

        // ─── Log the seed for reference ──────────────────────────
        ManhuntLite.LOGGER.info("[ManhuntLite] World reset performed. Seed for next full reset: {}", seed);
        ManhuntLite.LOGGER.info("[ManhuntLite] For full terrain regeneration, stop the server and delete world folders.");
    }

    /**
     * Configures game rules appropriate for a manhunt match.
     */
    private static void configureGameRules(MinecraftServer server) {
        // Execute game rule commands through the server
        var commandManager = server.getCommandManager();
        var source = server.getCommandSource();

        // Standard manhunt rules
        commandManager.executeWithPrefix(source, "gamerule playersSleepingPercentage 100");
        commandManager.executeWithPrefix(source, "gamerule announceAdvancements true");
        commandManager.executeWithPrefix(source, "gamerule showDeathMessages true");
        commandManager.executeWithPrefix(source, "gamerule naturalRegeneration true");
        commandManager.executeWithPrefix(source, "gamerule keepInventory false");
        commandManager.executeWithPrefix(source, "gamerule doDaylightCycle true");
        commandManager.executeWithPrefix(source, "gamerule doWeatherCycle true");
        commandManager.executeWithPrefix(source, "gamerule doMobSpawning true");
        commandManager.executeWithPrefix(source, "gamerule difficulty normal");

        // Locator bar enabled (for our tracking system)
        commandManager.executeWithPrefix(source, "gamerule locatorBar true");
    }

    /**
     * Generates a shell script for full world reset.
     * This is the recommended approach for production servers.
     *
     * The script:
     * 1. Stops the server
     * 2. Deletes world folders
     * 3. Updates server.properties with new seed
     * 4. Restarts the server
     */
    public static String generateResetScript(long seed) {
        return """
                #!/bin/bash
                # ManhuntLite Full World Reset Script
                # Generated seed: %d
                
                echo "Stopping server..."
                # Send stop command via RCON or screen
                # screen -S minecraft -p 0 -X stuff "stop$(printf '\r')"
                
                sleep 5
                
                echo "Deleting world files..."
                rm -rf world/
                rm -rf world_nether/
                rm -rf world_the_end/
                
                echo "Setting new seed..."
                sed -i 's/level-seed=.*/level-seed=%d/' server.properties
                
                echo "Starting server..."
                # java -jar fabric-server-launch.jar nogui
                
                echo "Done! New seed: %d"
                """.formatted(seed, seed, seed);
    }
}
