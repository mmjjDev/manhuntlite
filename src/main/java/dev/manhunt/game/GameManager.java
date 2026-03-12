package dev.manhunt.game;

import dev.manhunt.lobby.LobbyManager;
import dev.manhunt.ready.ReadyManager;
import dev.manhunt.team.TeamManager;
import dev.manhunt.tracking.TrackingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;
import java.util.UUID;

/**
 * Central game orchestrator.
 * Manages phase transitions, delegates to sub-managers, and enforces game rules.
 */
public class GameManager {

    private static GameManager instance;

    private GameState state = GameState.LOBBY;
    private MinecraftServer server;

    // Sub-managers
    private final TeamManager teamManager = new TeamManager();
    private final ReadyManager readyManager = new ReadyManager();
    private final TrackingManager trackingManager = new TrackingManager();
    private final LobbyManager lobbyManager = new LobbyManager();

    // Countdown state
    private int countdownTicks = -1;
    private static final int COUNTDOWN_SECONDS = 5;

    private GameManager() {}

    public static GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    /**
     * Called once when the server starts.
     */
    public void init(MinecraftServer server) {
        this.server = server;
        this.state = GameState.LOBBY;
        trackingManager.init(server);
    }

    /**
     * Called every server tick. Drives countdown and tracking updates.
     */
    public void tick() {
        if (state == GameState.STARTING) {
            tickCountdown();
        }
        if (state == GameState.RUNNING) {
            trackingManager.tick(server, teamManager);
        }
    }

    // ─── Phase Transitions ───────────────────────────────────────

    /**
     * Attempts to begin the countdown. Validates that all conditions are met:
     * - At least 1 runner, 1 hunter
     * - All players ready
     */
    public void tryStartCountdown() {
        if (state != GameState.LOBBY) return;

        Set<UUID> runners = teamManager.getRunners();
        Set<UUID> hunters = teamManager.getHunters();

        if (runners.isEmpty()) {
            broadcast("§c[Manhunt] Brak runnerów! Ktoś musi wybrać drużynę.");
            return;
        }
        if (hunters.isEmpty()) {
            broadcast("§c[Manhunt] Brak hunterów! Ktoś musi wybrać drużynę.");
            return;
        }

        int totalPlayers = server.getPlayerManager().getPlayerList().size();
        int readyCount = readyManager.getReadyCount();

        if (readyCount < totalPlayers) {
            broadcast("§c[Manhunt] Nie wszyscy są gotowi! (" + readyCount + "/" + totalPlayers + ")");
            return;
        }

        // All conditions met — start countdown
        state = GameState.STARTING;
        countdownTicks = COUNTDOWN_SECONDS * 20; // 20 ticks per second
        broadcast("§a[Manhunt] Wszyscy gotowi! Start za " + COUNTDOWN_SECONDS + " sekund...");
    }

    /**
     * Handles the 5-second countdown tick logic.
     */
    private void tickCountdown() {
        if (countdownTicks <= 0) return;

        countdownTicks--;

        // Announce each full second
        if (countdownTicks % 20 == 0 && countdownTicks > 0) {
            int secondsLeft = countdownTicks / 20;
            broadcast("§e[Manhunt] " + secondsLeft + "...");
        }

        // Countdown finished
        if (countdownTicks <= 0) {
            startGame();
        }
    }

    /**
     * Transitions to RUNNING. Teleports players, clears lobby items, starts tracking.
     */
    private void startGame() {
        state = GameState.RUNNING;
        broadcast("§a§l[Manhunt] START! Polowanie rozpoczęte!");

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Clear lobby hotbar items
            player.getInventory().clear();

            // Teleport to world spawn
            var spawnPos = server.getOverworld().getSpawnPos();
            player.teleport(
                    server.getOverworld(),
                    spawnPos.getX() + 0.5,
                    spawnPos.getY(),
                    spawnPos.getZ() + 0.5,
                    Set.of(),
                    player.getYaw(),
                    player.getPitch(),
                    false
            );

            // Reset player state
            player.setHealth(player.getMaxHealth());
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(5.0f);
            player.setExperienceLevel(0);
            player.setExperiencePoints(0);
        }

        // Initialize tracking — send waypoints to all hunters
        trackingManager.startTracking(server, teamManager);
    }

    /**
     * Called when a runner dies or the Ender Dragon is killed.
     */
    public void endGame(String reason) {
        if (state != GameState.RUNNING) return;
        state = GameState.ENDED;

        broadcast("§6§l[Manhunt] " + reason);
        broadcast("§e[Manhunt] Użyj /reset aby zresetować świat.");

        // Stop tracking
        trackingManager.stopTracking(server);
    }

    /**
     * Resets everything back to LOBBY state.
     */
    public void resetToLobby() {
        state = GameState.LOBBY;
        teamManager.reset();
        readyManager.reset();
        trackingManager.stopTracking(server);
        countdownTicks = -1;

        // Give lobby items to all online players
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.getInventory().clear();
            lobbyManager.giveLobbyItems(player);
        }

        broadcast("§a[Manhunt] Lobby zresetowane. Wybierzcie drużyny!");
    }

    // ─── Player Join / Leave ─────────────────────────────────────

    public void onPlayerJoin(ServerPlayerEntity player) {
        if (state == GameState.LOBBY || state == GameState.STARTING) {
            player.getInventory().clear();
            lobbyManager.giveLobbyItems(player);
        }
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        teamManager.removePlayer(uuid);
        readyManager.setReady(uuid, false);

        // If we were in countdown and conditions no longer met, cancel
        if (state == GameState.STARTING) {
            cancelCountdown();
        }

        // If runner leaves during game, check if any runners remain
        if (state == GameState.RUNNING && teamManager.getRunners().isEmpty()) {
            endGame("Wszyscy runnerzy wyszli! Hunterzy wygrywają!");
        }
    }

    /**
     * Cancels an active countdown and returns to LOBBY.
     */
    public void cancelCountdown() {
        if (state != GameState.STARTING) return;
        state = GameState.LOBBY;
        countdownTicks = -1;
        broadcast("§c[Manhunt] Odliczanie anulowane!");
    }

    /**
     * Called when a runner dies during the game.
     */
    public void onRunnerDeath(ServerPlayerEntity runner) {
        if (state != GameState.RUNNING) return;

        UUID uuid = runner.getUuid();
        String name = runner.getName().getString();

        // Remove from runners, make them a spectator-hunter
        teamManager.eliminateRunner(uuid);
        broadcast("§c[Manhunt] " + name + " został wyeliminowany!");

        // Check if all runners are eliminated
        if (teamManager.getAliveRunners().isEmpty()) {
            endGame("Wszyscy runnerzy wyeliminowani! Hunterzy wygrywają!");
        }
    }

    /**
     * Called when the Ender Dragon is killed.
     */
    public void onDragonKill() {
        if (state != GameState.RUNNING) return;
        endGame("Smok pokonany! Runnerzy wygrywają!");
    }

    // ─── Utility ─────────────────────────────────────────────────

    public void broadcast(String message) {
        if (server == null) return;
        server.getPlayerManager().broadcast(Text.literal(message), false);
    }

    // ─── Getters ─────────────────────────────────────────────────

    public GameState getState() {
        return state;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public ReadyManager getReadyManager() {
        return readyManager;
    }

    public TrackingManager getTrackingManager() {
        return trackingManager;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public boolean isInLobby() {
        return state == GameState.LOBBY || state == GameState.STARTING;
    }

    public boolean isRunning() {
        return state == GameState.RUNNING;
    }
}
