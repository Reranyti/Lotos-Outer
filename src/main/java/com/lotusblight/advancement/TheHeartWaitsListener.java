package com.lotusblight.advancement;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Periodic, throttled check for {@code lotusblight:the_heart_waits}: awards
 * the advancement once a player gets within {@link #RANGE} of the world's
 * lotus heart — encountering the landmark, not picking up an item.
 *
 * <p>Only one heart can ever exist per dimension (see {@link
 * OutbreakSavedData#claimHeart()}), grown at the anchor position of
 * whichever outbreak matured first. That means finding it doesn't need a
 * world scan at all: {@link OutbreakSavedData#isHeartClaimed()} tells us in
 * O(1) whether a heart exists yet, and if so we just check the (small)
 * outbreak list for the one whose anchor block is actually {@code
 * lotusblight:lotus_heart} — the same lookup {@code DialogueAnswerPacket}
 * and {@code MapSyncManager} already use. Far cheaper than {@link
 * TwoInfectionsListener}'s block scan, which has to search for a block
 * that isn't tracked anywhere.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TheHeartWaitsListener {
    private static final String FLAG_KEY = "LotusBlightHeartWaitsChecked";
    private static final int CHECK_INTERVAL_TICKS = 60;
    private static final double RANGE = 24.0;

    private TheHeartWaitsListener() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.getPersistentData().getBoolean(FLAG_KEY)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0) return;

        OutbreakSavedData data = OutbreakSavedData.get(level);
        if (!data.isHeartClaimed()) return;

        for (OutbreakRecord record : data.outbreaksWithin(player.blockPosition(), RANGE, false)) {
            if (level.hasChunkAt(record.pos()) && level.getBlockState(record.pos()).is(ModBlocks.LOTUS_HEART.get())) {
                player.getPersistentData().putBoolean(FLAG_KEY, true);
                TheHeartWaitsTrigger.INSTANCE.trigger(player);
                return;
            }
        }
    }
}
