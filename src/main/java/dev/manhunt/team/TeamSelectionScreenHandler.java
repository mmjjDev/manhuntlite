package dev.manhunt.team;

import dev.manhunt.game.GameManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Custom screen handler for the team selection chest GUI.
 * Intercepts all clicks to prevent item theft and delegates
 * team selection logic to TeamManager.
 */
public class TeamSelectionScreenHandler extends GenericContainerScreenHandler {

    public TeamSelectionScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
    }

    /**
     * Override slot click to prevent any item movement.
     * Only process clicks on the runner/hunter icon slots.
     */
    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // Block all item movement — this is a read-only GUI
        if (slotIndex < 0 || slotIndex >= 27) {
            // Clicked outside the GUI inventory (player inventory area) — ignore
            return;
        }

        // Delegate to TeamManager for team selection logic
        if (player instanceof ServerPlayerEntity serverPlayer) {
            GameManager.getInstance().getTeamManager().handleGuiClick(serverPlayer, slotIndex);
        }

        // Don't call super — prevents any item pickup/swap
    }

    /**
     * Prevent shift-clicking items into the GUI.
     */
    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    /**
     * Always allow the screen to stay open.
     */
    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
