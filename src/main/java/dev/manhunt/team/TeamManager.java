package dev.manhunt.team;

import dev.manhunt.game.GameManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

/**
 * Manages team assignments (Runner / Hunter).
 * Provides a chest-based GUI for team selection.
 */
public class TeamManager {

    // Team assignments: UUID -> Team
    private final Map<UUID, Team> teams = new HashMap<>();

    // Runners who have been eliminated (still tracked for spectating)
    private final Set<UUID> eliminatedRunners = new HashSet<>();

    /**
     * The two available teams.
     */
    public enum Team {
        RUNNER,
        HUNTER
    }

    // ─── GUI Constants ───────────────────────────────────────────

    // Slot layout in a 9-slot row (we use a 9x1 via GenericContainerScreenHandler 9x3)
    // Row 0: [ _ _ _ RUNNER _ HUNTER _ _ _ ]
    // We place icons at slots 2 and 6 in the top row for visual balance in a 3-row chest.
    private static final int RUNNER_SLOT = 11;  // Middle row, slot 3 (0-indexed: row1*9 + 2)
    private static final int HUNTER_SLOT = 15;  // Middle row, slot 7 (0-indexed: row1*9 + 6)

    // ─── Team Operations ─────────────────────────────────────────

    public void setTeam(UUID uuid, Team team) {
        teams.put(uuid, team);
    }

    public Team getTeam(UUID uuid) {
        return teams.get(uuid);
    }

    public boolean isRunner(UUID uuid) {
        return teams.get(uuid) == Team.RUNNER;
    }

    public boolean isHunter(UUID uuid) {
        return teams.get(uuid) == Team.HUNTER;
    }

    public Set<UUID> getRunners() {
        Set<UUID> runners = new HashSet<>();
        for (var entry : teams.entrySet()) {
            if (entry.getValue() == Team.RUNNER) {
                runners.add(entry.getKey());
            }
        }
        return runners;
    }

    public Set<UUID> getHunters() {
        Set<UUID> hunters = new HashSet<>();
        for (var entry : teams.entrySet()) {
            if (entry.getValue() == Team.HUNTER) {
                hunters.add(entry.getKey());
            }
        }
        return hunters;
    }

    /**
     * Returns runners who haven't been eliminated yet.
     */
    public Set<UUID> getAliveRunners() {
        Set<UUID> alive = getRunners();
        alive.removeAll(eliminatedRunners);
        return alive;
    }

    public void eliminateRunner(UUID uuid) {
        eliminatedRunners.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        teams.remove(uuid);
        eliminatedRunners.remove(uuid);
    }

    public void reset() {
        teams.clear();
        eliminatedRunners.clear();
    }

    // ─── Team Selection GUI ──────────────────────────────────────

    /**
     * Opens a chest GUI for the player to pick their team.
     * Uses a 3-row (27 slot) generic container with icons.
     */
    public void openTeamGui(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, playerEntity) -> {
                    // Create a 27-slot inventory for display
                    SimpleInventory gui = new SimpleInventory(27);

                    // Fill background with gray glass panes
                    ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
                    filler.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
                    for (int i = 0; i < 27; i++) {
                        gui.setStack(i, filler.copy());
                    }

                    // Runner icon — Feather
                    ItemStack runnerIcon = new ItemStack(Items.FEATHER);
                    runnerIcon.set(DataComponentTypes.CUSTOM_NAME,
                            Text.literal("Runner").formatted(Formatting.GREEN, Formatting.BOLD));
                    gui.setStack(RUNNER_SLOT, runnerIcon);

                    // Hunter icon — Iron Sword
                    ItemStack hunterIcon = new ItemStack(Items.IRON_SWORD);
                    hunterIcon.set(DataComponentTypes.CUSTOM_NAME,
                            Text.literal("Hunter").formatted(Formatting.RED, Formatting.BOLD));
                    gui.setStack(HUNTER_SLOT, hunterIcon);

                    // Create the screen handler with our custom inventory
                    return new TeamSelectionScreenHandler(syncId, playerInventory, gui);
                },
                Text.literal("Wybierz Drużynę").formatted(Formatting.DARK_PURPLE, Formatting.BOLD)
        ));
    }

    /**
     * Handles a click inside the team selection GUI.
     * Called from TeamSelectionScreenHandler.
     */
    public void handleGuiClick(ServerPlayerEntity player, int slotIndex) {
        String name = player.getName().getString();
        UUID uuid = player.getUuid();

        if (slotIndex == RUNNER_SLOT) {
            setTeam(uuid, Team.RUNNER);
            GameManager.getInstance().broadcast(
                    "§a[Manhunt] " + name + " dołączył do Runnerów!");
            player.closeHandledScreen();
        } else if (slotIndex == HUNTER_SLOT) {
            setTeam(uuid, Team.HUNTER);
            GameManager.getInstance().broadcast(
                    "§c[Manhunt] " + name + " dołączył do Hunterów!");
            player.closeHandledScreen();
        }
        // Clicking filler slots does nothing
    }
}
