package dev.manhunt.game;

/**
 * Represents the current phase of the Manhunt game.
 *
 * LOBBY    - Players are in the waiting area, selecting teams and readying up.
 * STARTING - Countdown is active (5 seconds), game is about to begin.
 * RUNNING  - The game is in progress. Hunters track runners.
 * ENDED    - A win condition has been met. Awaiting reset.
 */
public enum GameState {
    LOBBY,
    STARTING,
    RUNNING,
    ENDED
}
