package dev.manhunt.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import dev.manhunt.game.GameManager;
import dev.manhunt.world.WorldResetManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * Operator-only /reset command.
 *
 * Usage:
 *   /reset         — reset world with a random seed
 *   /reset <seed>  — reset world with a specific seed
 *
 * Process:
 * 1. Teleport all players to waiting area
 * 2. Schedule world deletion and regeneration
 * 3. Teleport players back to new spawn
 * 4. Return to lobby phase
 */
public class ResetCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("reset")
                        .requires(source -> source.hasPermissionLevel(2)) // OP level 2+
                        .executes(context -> {
                            // No seed provided — random
                            return executeReset(context.getSource(), null);
                        })
                        .then(CommandManager.argument("seed", LongArgumentType.longArg())
                                .executes(context -> {
                                    long seed = LongArgumentType.getLong(context, "seed");
                                    return executeReset(context.getSource(), seed);
                                })
                        )
        );
    }

    private static int executeReset(ServerCommandSource source, Long seed) {
        // Generate random seed if none provided
        if (seed == null) {
            seed = new java.util.Random().nextLong();
        }

        GameManager gm = GameManager.getInstance();

        // Announce reset
        gm.broadcast("§e[Manhunt] Reset świata...");
        gm.broadcast("§a[Manhunt] Nowy seed: " + seed);

        // Execute the world reset process
        WorldResetManager.performReset(gm.getServer(), seed);

        // Return to lobby
        gm.resetToLobby();

        source.sendFeedback(() -> Text.literal("§a[Manhunt] Świat zresetowany!"), true);
        return 1;
    }
}
