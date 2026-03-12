package dev.manhunt.ready;

import dev.manhunt.game.GameManager;
import dev.manhunt.lobby.LobbyManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manages player readiness state.
 * Players toggle ready/not-ready via the glass pane in hotbar slot 1.
 * When all players are ready (and team conditions met), triggers countdown.
 */
public class ReadyManager {

    private final Set<UUID> readyPlayers = new HashSet<>();

    /**
     * Toggles a player's ready state and updates their hotbar item.
     * Automatically triggers countdown check when toggling to ready.
     */
    public void toggleReady(ServerPlayerEntity player, GameManager gm) {
        UUID uuid = player.getUuid();
        String name = player.getName().getString();

        // Must have a team to ready up
        if (gm.getTeamManager().getTeam(uuid) == null) {
            player.sendMessage(
                    Text.literal("§c[Manhunt] Najpierw wybierz drużynę! (kliknij kompas)"),
                    false
            );
            return;
        }

        if (readyPlayers.contains(uuid)) {
            // Un-ready
            readyPlayers.remove(uuid);
            gm.getLobbyManager().updateReadyItem(player, false);
            gm.broadcast("§6[Manhunt] " + name + " już nie jest gotowy.");

            // Cancel countdown if active
            gm.cancelCountdown();
        } else {
            // Ready up
            readyPlayers.add(uuid);
            gm.getLobbyManager().updateReadyItem(player, true);
            gm.broadcast("§e[Manhunt] " + name + " jest gotowy!");
        }

        // Show ready count
        int total = gm.getServer().getPlayerManager().getPlayerList().size();
        int ready = readyPlayers.size();
        gm.broadcast("§b[Manhunt] Gotowi: " + ready + "/" + total);

        // Check if everyone is ready
        if (ready >= total && total > 1) {
            gm.tryStartCountdown();
        }
    }

    /**
     * Sets a player's ready state directly (used for cleanup on disconnect).
     */
    public void setReady(UUID uuid, boolean ready) {
        if (ready) {
            readyPlayers.add(uuid);
        } else {
            readyPlayers.remove(uuid);
        }
    }

    public boolean isReady(UUID uuid) {
        return readyPlayers.contains(uuid);
    }

    public int getReadyCount() {
        return readyPlayers.size();
    }

    /**
     * Clears all ready states. Called on game reset.
     */
    public void reset() {
        readyPlayers.clear();
    }
}
