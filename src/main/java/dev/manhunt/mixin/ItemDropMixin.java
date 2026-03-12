package dev.manhunt.mixin;

import dev.manhunt.game.GameManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to prevent players from dropping items during lobby phase.
 * Intercepts the dropSelectedItem method on PlayerEntity.
 * This prevents the Q-key drop and any other item drop mechanic.
 */
@Mixin(PlayerEntity.class)
public abstract class ItemDropMixin {

    /**
     * Cancel item drops when in lobby phase.
     * The method dropSelectedItem is called when a player presses Q or drags items out of inventory.
     */
    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void manhuntlite$preventLobbyDrop(boolean entireStack, CallbackInfoReturnable<ItemStack> cir) {
        // Only apply to server-side players
        if ((Object) this instanceof ServerPlayerEntity) {
            if (GameManager.getInstance().isInLobby()) {
                cir.setReturnValue(ItemStack.EMPTY);
            }
        }
    }
}
