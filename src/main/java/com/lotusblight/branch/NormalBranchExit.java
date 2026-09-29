package com.lotusblight.branch;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.NormalBranchExitPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * What follows the empty place. A death there marks the player, and the next respawn (or login, if the
 * game was closed in between) tells that player's client to start the exit. The server only keeps the
 * mark and sends the signal - everything the exit does happens on the player's own client, so on a
 * shared server nobody else is touched. Also finishes an entry scene that was cut short by leaving.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID)
public final class NormalBranchExit {
    private NormalBranchExit() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (NormalBranchLock.isLocked() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().equals(NormalBranchDimension.HOLLOW)) return;
        if (!LotusPlayerState.isNormalBranchEntered(player)) return;
        LotusPlayerState.setNormalBranchExitPending(player, true);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || NormalBranchLock.isLocked()) return;
        if (event.getEntity() instanceof ServerPlayer player && LotusPlayerState.isNormalBranchExitPending(player)) {
            start(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (NormalBranchLock.isLocked() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (LotusPlayerState.isNormalBranchExitPending(player)) {
            start(player);
        } else if (LotusPlayerState.isNormalBranchEntered(player) && !LotusPlayerState.isNormalBranchArrived(player)) {
            // Left mid-scene: the run was dropped before it could move the player, so play it again.
            NormalBranchScene.start(player);
        }
    }

    /** Tells this player's client to start the exit. */
    public static void start(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NormalBranchExitPacket());
    }
}
