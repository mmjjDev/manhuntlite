package dev.manhunt.world;

import dev.manhunt.ManhuntLite;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.Set;

/**
 * Manages a temporary waiting area for players during world reset.
 *
 * Since creating custom dimensions at runtime is complex and fragile,
 * this uses a practical approach: builds a small platform at extreme Y
 * coordinates in The End dimension (which is rarely used during manhunt
 * and loads quickly).
 *
 * The waiting area:
 * - Located at Y=200 in The End
 * - Small bedrock platform (5x5)
 * - Players set to adventure mode (can't break bedrock)
 * - Invisible effect applied
 * - Surrounded by barrier blocks for safety
 */
public class WaitingWorldManager {

    // Waiting platform location in The End
    private static final int PLATFORM_X = 0;
    private static final int PLATFORM_Y = 200;
    private static final int PLATFORM_Z = 0;
    private static final int PLATFORM_RADIUS = 3;

    /**
     * Builds the waiting platform and teleports all players to it.
     */
    public static void sendToWaiting(MinecraftServer server) {
        ServerWorld endWorld = server.getWorld(
                net.minecraft.world.World.END
        );

        if (endWorld == null) {
            ManhuntLite.LOGGER.warn("[ManhuntLite] End dimension not available, skipping waiting area.");
            return;
        }

        // Build the platform
        buildPlatform(endWorld);

        // Teleport all players
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.changeGameMode(GameMode.ADVENTURE);

            player.teleport(
                    endWorld,
                    PLATFORM_X + 0.5,
                    PLATFORM_Y + 1.0,
                    PLATFORM_Z + 0.5,
                    Set.of(),
                    0.0f, 0.0f,
                    false
            );

            // Make invisible during wait
            // In 1.21.11 Yarn, StatusEffects fields are RegistryEntry<StatusEffect>
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.INVISIBILITY,
                    600, // 30 seconds (enough time for reset)
                    0,
                    false,
                    false
            ));
        }

        ManhuntLite.LOGGER.info("[ManhuntLite] Players moved to waiting area.");
    }

    /**
     * Returns all players from the waiting area to the overworld spawn.
     */
    public static void returnFromWaiting(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        BlockPos spawn = overworld.getSpawnPos();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Remove invisibility
            player.clearStatusEffects();

            // Set back to survival
            player.changeGameMode(GameMode.SURVIVAL);

            // Teleport to overworld spawn
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

        ManhuntLite.LOGGER.info("[ManhuntLite] Players returned from waiting area.");
    }

    /**
     * Builds a small bedrock platform with barrier walls at the waiting location.
     */
    private static void buildPlatform(ServerWorld world) {
        // Floor: bedrock platform
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                world.setBlockState(
                        new BlockPos(PLATFORM_X + x, PLATFORM_Y, PLATFORM_Z + z),
                        Blocks.BEDROCK.getDefaultState()
                );
            }
        }

        // Walls: barrier blocks (2 blocks high)
        for (int y = 1; y <= 2; y++) {
            for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
                // North and south walls
                world.setBlockState(
                        new BlockPos(PLATFORM_X + x, PLATFORM_Y + y, PLATFORM_Z - PLATFORM_RADIUS),
                        Blocks.BARRIER.getDefaultState()
                );
                world.setBlockState(
                        new BlockPos(PLATFORM_X + x, PLATFORM_Y + y, PLATFORM_Z + PLATFORM_RADIUS),
                        Blocks.BARRIER.getDefaultState()
                );
            }
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                // East and west walls
                world.setBlockState(
                        new BlockPos(PLATFORM_X - PLATFORM_RADIUS, PLATFORM_Y + y, PLATFORM_Z + z),
                        Blocks.BARRIER.getDefaultState()
                );
                world.setBlockState(
                        new BlockPos(PLATFORM_X + PLATFORM_RADIUS, PLATFORM_Y + y, PLATFORM_Z + z),
                        Blocks.BARRIER.getDefaultState()
                );
            }
        }

        // Ceiling: barrier blocks
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                world.setBlockState(
                        new BlockPos(PLATFORM_X + x, PLATFORM_Y + 3, PLATFORM_Z + z),
                        Blocks.BARRIER.getDefaultState()
                );
            }
        }

        // Clear air inside
        for (int y = 1; y <= 2; y++) {
            for (int x = -PLATFORM_RADIUS + 1; x < PLATFORM_RADIUS; x++) {
                for (int z = -PLATFORM_RADIUS + 1; z < PLATFORM_RADIUS; z++) {
                    world.setBlockState(
                            new BlockPos(PLATFORM_X + x, PLATFORM_Y + y, PLATFORM_Z + z),
                            Blocks.AIR.getDefaultState()
                    );
                }
            }
        }
    }
}
