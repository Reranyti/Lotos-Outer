package com.lotusblight.escape;

import com.lotusblight.LotusConfig;
import com.lotusblight.data.HonchoSavedData;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.ShowStarFallPacket;
import net.exmo.meteor_shower.event.MeteorShowerEventManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * The real, non-command trigger for StarFall (see {@link com.lotusblight.command.LotusCommands#starFallTrigger}
 * for the admin/testing path this mirrors). Fires once per player, once world infection crosses
 * {@link #TRIGGER_FRACTION} of {@link LotusConfig#WORLD_INFECTION_REFERENCE} - double the Побег
 * event's own 15% (see {@link LotusChaseEvent#maybeUnlock}), so StarFall reads as the later,
 * heavier escalation of the two. Only fires for a player who has already committed to a dialogue
 * branch (undecided players have no script to show) and only once ever per player, tracked via
 * {@link LotusPlayerState#hasSeenStarFall}.
 */
public final class StarFallEvent {
    private static final int SWEEP_INTERVAL_TICKS = 100;
    private static final float TRIGGER_FRACTION = 0.30f;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0) return;

        long totalInfected = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (OutbreakRecord outbreak : OutbreakSavedData.get(level).allOutbreaks()) {
                totalInfected += outbreak.infectedBlockCount();
            }
        }
        long threshold = (long) (LotusConfig.WORLD_INFECTION_REFERENCE.get() * TRIGGER_FRACTION);
        if (totalInfected < threshold) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int branch = LotusPlayerState.getDialogueBranch(player);
            if (branch == LotusPlayerState.BRANCH_UNDECIDED) continue;
            if (LotusPlayerState.hasSeenStarFall(player)) continue;

            LotusPlayerState.markStarFallSeen(player);
            // Inverted on purpose: "на войне вы сражаетесь с лотосом а не с НИМ [Звёздным Светом]" -
            // a RESISTANCE player is fighting the same enemy Star Light is (the Lotus), so they get
            // the friendly/allied script; an ALLIANCE player sided WITH the Lotus, so Star Light
            // treats them as hostile and they get the damage/rod-removal script instead.
            boolean allianceBranch = branch == LotusPlayerState.BRANCH_RESISTANCE;
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShowStarFallPacket(allianceBranch));
            if (player.level() instanceof ServerLevel level) {
                MeteorShowerEventManager.forceShower(level, MeteorShowerEventManager.ShowerScale.LARGE);
                spawnHoncho(level, player);
            }
        }
    }

    /** "появляется ПОСЛЕ [StarFall]" - one Honcho per world (HonchoSavedData), dropped in near whoever lived through StarFall first; he's persistent and just wanders/waits from then on (see HonchoEntity). */
    private void spawnHoncho(ServerLevel level, ServerPlayer player) {
        HonchoSavedData honchoData = HonchoSavedData.get(level.getServer().overworld());
        if (honchoData.isSpawned()) return;
        var entity = com.lotusblight.registry.ModEntities.HONCHO.get().create(level);
        if (entity == null) return;
        var pos = player.blockPosition().offset(2, 0, 2);
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        // A fixed +2/+2 offset lands inside a wall or a tree trunk often enough - fall back to the
        // player's own spot, which is known to be free.
        if (!level.hasChunkAt(pos) || !level.noCollision(entity)) {
            entity.moveTo(player.getX(), player.getY(), player.getZ(), 0f, 0f);
        }
        if (level.addFreshEntity(entity)) {
            honchoData.markSpawned();
        }
    }
}
