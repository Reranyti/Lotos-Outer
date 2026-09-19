package com.lotusblight.advancement;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Periodic, throttled check for {@code lotusblight:survivor_of_the_bloom}:
 * awards the advancement once a player has spent {@link #REQUIRED_TICKS}
 * continuously within {@link #RANGE} of a mature (phase {@link
 * OutbreakRecord#MAX_PHASE}) outbreak.
 *
 * <p>Design choice: "survive near it" reads as an endurance challenge, not
 * a cumulative errand, so the progress counter resets on leaving range —
 * but with a short {@link #GRACE_TICKS} grace window so a player who
 * briefly steps out (dodging a guardian, crossing a stream) isn't punished
 * for the whole run. Progress only actually resets to zero once they've
 * been out of range continuously for longer than the grace window; while
 * within the grace window the counter simply pauses.
 *
 * <p>Per-player progress lives in that player's persistent data (survives
 * logout/relog and server restarts, same trick used for the "checked once"
 * flags elsewhere in this package) rather than a static in-memory map.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SurvivorOfTheBloomListener {
    private static final String DONE_KEY = "LotusBlightSurvivorDone";
    private static final String PROGRESS_KEY = "LotusBlightSurvivorTicks";
    private static final String GRACE_KEY = "LotusBlightSurvivorGraceTicks";

    private static final int CHECK_INTERVAL_TICKS = 20;
    /** How close counts as "near" a mature outbreak. */
    private static final double RANGE = 40.0;
    /** 6000 ticks = 5 real-world minutes of continuous exposure. */
    private static final int REQUIRED_TICKS = 6000;
    /** How long a player can be out of range before their progress actually resets, instead of just pausing. */
    private static final int GRACE_TICKS = 100;

    private SurvivorOfTheBloomListener() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.getPersistentData().getBoolean(DONE_KEY)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0) return;

        var tag = player.getPersistentData();
        boolean nearMatureOutbreak = isNearMatureOutbreak(level, player);

        if (nearMatureOutbreak) {
            tag.putInt(GRACE_KEY, 0);
            int progress = tag.getInt(PROGRESS_KEY) + CHECK_INTERVAL_TICKS;
            if (progress >= REQUIRED_TICKS) {
                tag.putBoolean(DONE_KEY, true);
                tag.remove(PROGRESS_KEY);
                tag.remove(GRACE_KEY);
                SurvivorOfTheBloomTrigger.INSTANCE.trigger(player);
                return;
            }
            tag.putInt(PROGRESS_KEY, progress);
        } else if (tag.getInt(PROGRESS_KEY) > 0) {
            int grace = tag.getInt(GRACE_KEY) + CHECK_INTERVAL_TICKS;
            if (grace > GRACE_TICKS) {
                tag.putInt(PROGRESS_KEY, 0);
                tag.putInt(GRACE_KEY, 0);
            } else {
                tag.putInt(GRACE_KEY, grace);
            }
        }
    }

    private static boolean isNearMatureOutbreak(ServerLevel level, ServerPlayer player) {
        OutbreakSavedData data = OutbreakSavedData.get(level);
        for (OutbreakRecord record : data.outbreaksWithin(player.blockPosition(), RANGE, false)) {
            if (record.phase() >= OutbreakRecord.MAX_PHASE) {
                return true;
            }
        }
        return false;
    }
}
