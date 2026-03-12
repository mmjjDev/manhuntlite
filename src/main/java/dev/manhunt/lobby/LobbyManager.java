package dev.manhunt.lobby;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Manages the lobby hotbar items.
 * Slot 0: Compass (Team Selector)
 * Slot 1: Red/Lime Glass Pane (Ready Toggle)
 */
public class LobbyManager {

    /**
     * Gives a player the standard lobby hotbar items.
     * Called on join during LOBBY phase and after resets.
     */
    public void giveLobbyItems(ServerPlayerEntity player) {
        player.getInventory().clear();

        // Slot 0 — Team Selector Compass
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Wybierz Drużynę").formatted(Formatting.GOLD, Formatting.BOLD));
        player.getInventory().setStack(0, compass);

        // Slot 1 — Ready Toggle (starts as NOT ready)
        ItemStack readyItem = createNotReadyItem();
        player.getInventory().setStack(1, readyItem);
    }

    /**
     * Creates the "Not Ready" red glass pane item.
     */
    public static ItemStack createNotReadyItem() {
        ItemStack pane = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Nie gotowy").formatted(Formatting.RED, Formatting.BOLD));
        return pane;
    }

    /**
     * Creates the "Ready" lime glass pane item.
     */
    public static ItemStack createReadyItem() {
        ItemStack pane = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Gotowy!").formatted(Formatting.GREEN, Formatting.BOLD));
        return pane;
    }

    /**
     * Updates slot 1 to reflect the player's current ready state.
     */
    public void updateReadyItem(ServerPlayerEntity player, boolean ready) {
        ItemStack item = ready ? createReadyItem() : createNotReadyItem();
        player.getInventory().setStack(1, item);
    }
}
