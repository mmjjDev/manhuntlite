package dev.manhunt;

import dev.manhunt.command.ResetCommand;
import dev.manhunt.game.GameManager;
import dev.manhunt.game.GameState;
import dev.manhunt.lobby.LobbyManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ManhuntLite — Dream-style Manhunt using the Player Locator Bar.
 * Server-side only Fabric mod for Minecraft 1.21.11.
 */
public class ManhuntLite implements ModInitializer {

    public static final String MOD_ID = "manhuntlite";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[ManhuntLite] Inicjalizacja...");

        // ─── Server Lifecycle ────────────────────────────────────

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            GameManager.getInstance().init(server);
            LOGGER.info("[ManhuntLite] Serwer gotowy! Manhunt załadowany.");
        });

        // ─── Server Tick ─────────────────────────────────────────

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            GameManager.getInstance().tick();
        });

        // ─── Player Join / Leave ─────────────────────────────────

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            GameManager.getInstance().onPlayerJoin(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            GameManager.getInstance().onPlayerLeave(player);
        });

        // ─── Right-Click Item (Team Selector + Ready Toggle) ─────

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            GameManager gm = GameManager.getInstance();
            if (!gm.isInLobby()) return ActionResult.PASS;

            var stack = player.getStackInHand(hand);
            if (stack.isEmpty()) return ActionResult.PASS;

            // Compass in slot 0 → open team selector GUI
            if (stack.isOf(Items.COMPASS)) {
                gm.getTeamManager().openTeamGui(serverPlayer);
                return ActionResult.SUCCESS;
            }

            // Glass pane in slot 1 → toggle ready
            if (stack.isOf(Items.RED_STAINED_GLASS_PANE) || stack.isOf(Items.LIME_STAINED_GLASS_PANE)) {
                gm.getReadyManager().toggleReady(serverPlayer, gm);
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });

        // ─── Lobby Restrictions: Block Break ─────────────────────

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity sp) {
                return !GameManager.getInstance().isInLobby();
            }
            return true;
        });

        // ─── Lobby Restrictions: Block Place (UseBlock) ──────────

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (player instanceof ServerPlayerEntity sp && GameManager.getInstance().isInLobby()) {
                // Allow right-click of lobby items, block everything else
                var stack = player.getStackInHand(hand);
                if (stack.isOf(Items.COMPASS) || stack.isOf(Items.RED_STAINED_GLASS_PANE)
                        || stack.isOf(Items.LIME_STAINED_GLASS_PANE)) {
                    return ActionResult.PASS; // Let UseItemCallback handle it
                }
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // ─── Lobby Restrictions: Attack Block ────────────────────

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient) return ActionResult.PASS;
            if (player instanceof ServerPlayerEntity && GameManager.getInstance().isInLobby()) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // ─── Lobby Restrictions: Attack Entity (no PvP in lobby) ─

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (player instanceof ServerPlayerEntity && GameManager.getInstance().isInLobby()) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // ─── Entity Death: Runner elimination + Dragon kill ──────

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            GameManager gm = GameManager.getInstance();
            if (!gm.isRunning()) return;

            // Runner death
            if (entity instanceof ServerPlayerEntity deadPlayer) {
                if (gm.getTeamManager().isRunner(deadPlayer.getUuid())) {
                    gm.onRunnerDeath(deadPlayer);
                }
            }

            // Ender Dragon killed
            if (entity instanceof EnderDragonEntity) {
                gm.onDragonKill();
            }
        });

        // ─── Commands ────────────────────────────────────────────

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ResetCommand.register(dispatcher);
        });

        LOGGER.info("[ManhuntLite] Wszystko załadowane!");
    }
}
