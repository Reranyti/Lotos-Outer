package com.lotusblight.entity;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.ShowHonchoMeetingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * "у нас нет норм встречи с хончо...на войне" - a rare, exclusive "trip over a block" moment that
 * stands in for a proper first meeting: while walking, war-branch players who've lived through
 * StarFall have a tiny per-tick chance to stumble, see a hand reach down, and choose whether to
 * take it (see HonchoMeetingScreen/HonchoMeetingChoicePacket). Fires at most once per player.
 * "запинание работает только с тем что если игрок увидел звёздопад" - gated on hasSeenStarFall,
 * not on being physically near Honcho.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HonchoMeetingManager {
    /** "мизерным шансом" - roughly once every 10 minutes of qualifying walking on average at 20 tps. */
    private static final int TRIP_CHANCE = 12000;
    private static final double MIN_HORIZONTAL_SPEED_SQ = 0.005 * 0.005;

    private HonchoMeetingManager() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!player.onGround()) return;
        if (LotusPlayerState.getDialogueBranch(player) != LotusPlayerState.BRANCH_RESISTANCE) return;
        if (!LotusPlayerState.hasSeenStarFall(player)) return;
        if (LotusPlayerState.hasMetHoncho(player) || LotusPlayerState.isHonchoMeetingPending(player)) return;

        double dx = player.getDeltaMovement().x;
        double dz = player.getDeltaMovement().z;
        if (dx * dx + dz * dz < MIN_HORIZONTAL_SPEED_SQ) return;

        if (player.getRandom().nextInt(TRIP_CHANCE) != 0) return;

        LotusPlayerState.markHonchoMeetingPending(player);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShowHonchoMeetingPacket());
    }
}
