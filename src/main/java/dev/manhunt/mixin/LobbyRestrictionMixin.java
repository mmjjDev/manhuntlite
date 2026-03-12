package dev.manhunt.mixin;

import dev.manhunt.game.GameManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to prevent inventory manipulation during lobby phase.
 * Targets the player's own inventory screen handler to block:
 * - Moving items between slots
 * - Shift-clicking items
 * - Hotbar swapping
 * - Number key swapping
 *
 * This ensures lobby items (compass, glass pane) stay in their assigned slots.
 */
@Mixin(PlayerScreenHandler.class)
public abstract class LobbyRestrictionMixin {

    /**
     * Intercept all slot clicks in the player inventory during lobby.
     * Cancel any click that would move items around.
     */
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void manhuntlite$preventInventoryManipulation(
            int slotIndex, int button, SlotActionType actionType, PlayerEntity player,
            CallbackInfo ci) {

        // Only apply to server-side players in lobby
        if (!(player instanceof ServerPlayerEntity)) return;
        if (!GameManager.getInstance().isInLobby()) return;

        // Block ALL inventory manipulation during lobby
        // This prevents moving, swapping, or rearranging lobby items
        switch (actionType) {
            case PICKUP:        // Left/right click pickup
            case QUICK_MOVE:    // Shift-click
            case SWAP:          // Number key swap
            case THROW:         // Q key throw from slot
            case QUICK_CRAFT:   // Click-drag
            case PICKUP_ALL:    // Double-click collect
                ci.cancel();
                break;
            case CLONE:         // Middle-click (creative only) — allow
                break;
        }
    }
}
