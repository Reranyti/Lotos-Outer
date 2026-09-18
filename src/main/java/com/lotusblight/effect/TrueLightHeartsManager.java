package com.lotusblight.effect;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.LotusPlayerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Manages the +2-heart absorption bonus granted by glowing_berry once the
 * player has used up their free inner-voice scenes (see GlowingBerryItem).
 * Deliberately NOT the vanilla Absorption MobEffect — that would fight over
 * the same shared {@code getAbsorptionAmount()} float with anything else
 * that also uses vanilla Absorption (golden apples, enchanted golden
 * apples), and those are explicitly meant to still be able to stack more on
 * top without our bonus clobbering or being clobbered by them. Instead this
 * tracks its own +4.0f delta: added once when granted, subtracted once when
 * LotusPlayerState#getTrueLightHeartsExpireAt passes, and re-added after a
 * death respawn if the bonus was still active — since vanilla resets
 * absorption to 0 on respawn regardless of source.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrueLightHeartsManager {
    /** "Вторая полоска" — a second absorption row appearing below the normal health row: 10 hearts / 20 points, one row, not two. */
    public static final float BONUS_ABSORPTION = 20.0f;

    private static final int CHECK_INTERVAL_TICKS = 100;

    private TrueLightHeartsManager() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % CHECK_INTERVAL_TICKS != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            long expiresAt = LotusPlayerState.getTrueLightHeartsExpireAt(player);
            if (expiresAt <= 0) continue;
            if (player.level().getGameTime() >= expiresAt) {
                player.setAbsorptionAmount(Math.max(0f, player.getAbsorptionAmount() - BONUS_ABSORPTION));
                LotusPlayerState.setTrueLightHeartsExpireAt(player, 0);
            }
        }
    }

    /**
     * Vanilla clears absorption on death/respawn regardless of source — re-grant if the bonus
     * hadn't actually expired yet. This also fires (isWasDeath=false) when a new player instance
     * is constructed on a non-death respawn, e.g. returning from the End through the exit portal —
     * that path's own absorption carry-over isn't guaranteed the same way health/XP are, so top up
     * rather than blindly re-add, in case vanilla did carry it over and we'd otherwise double-grant.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        long expiresAt = LotusPlayerState.getTrueLightHeartsExpireAt(newPlayer);
        if (expiresAt <= newPlayer.level().getGameTime()) return;

        if (event.isWasDeath()) {
            newPlayer.setAbsorptionAmount(newPlayer.getAbsorptionAmount() + BONUS_ABSORPTION);
        } else if (newPlayer.getAbsorptionAmount() < BONUS_ABSORPTION) {
            // Top up TO the bonus amount, not add it on top - this branch fires whenever vanilla
            // absorption might have partially carried over (e.g. an End exit-portal clone), and
            // adding here would stack with whatever carried over instead of just restoring the
            // bonus, permanently stranding the difference (the expiry check above only ever
            // subtracts BONUS_ABSORPTION once).
            newPlayer.setAbsorptionAmount(BONUS_ABSORPTION);
        }
    }
}
