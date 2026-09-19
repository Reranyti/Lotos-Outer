package com.lotusblight.advancement;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBiomes;
import com.lotusblight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Periodic, throttled check for {@code lotusblight:two_infections}: awards
 * the advancement to any player who is simultaneously within range of a
 * lotus outbreak anchor and a Blessing-biome suppressor block ({@code
 * lotusblight:blessing_nodule}).
 *
 * <p>Cost control: outbreak proximity is checked first via
 * {@link OutbreakSavedData#outbreaksWithin} (O(outbreak count), same as
 * {@link com.lotusblight.map.MapSyncManager}) — this is cheap and almost
 * always false, so it gates the rest. Only if it passes do we also require
 * the player to currently be standing in the Blessing biome before running
 * the one genuinely expensive step: a small bounded block scan for the
 * suppressor block itself, which (unlike outbreaks) isn't tracked in any
 * registry and has to be looked up in the world. Both gates together mean
 * the scan only ever runs for a player standing right at the seam between
 * an infection and the Blessing biome — a deliberately rare spot, matching
 * the "two sides at once" theme.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TwoInfectionsListener {
    private static final String FLAG_KEY = "LotusBlightTwoInfectionsChecked";
    private static final int CHECK_INTERVAL_TICKS = 60;
    /** Range within which the outbreak anchor and the suppressor block both need to fall — a "you can see it from here" distance, matching the scan-radius scale used elsewhere in the map system. */
    private static final double DETECTION_RADIUS = 48.0;
    /** Bounded search radius for the suppressor block itself, kept much tighter than DETECTION_RADIUS so the scan stays cheap (see class doc). */
    private static final int NODULE_SCAN_RADIUS_H = 16;
    private static final int NODULE_SCAN_RADIUS_V = 10;

    private TwoInfectionsListener() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.getPersistentData().getBoolean(FLAG_KEY)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0) return;

        BlockPos playerPos = player.blockPosition();
        OutbreakSavedData data = OutbreakSavedData.get(level);
        if (data.outbreaksWithin(playerPos, DETECTION_RADIUS, false).isEmpty()) return;
        if (!level.getBiome(playerPos).is(ModBiomes.BLESSING_BIOME)) return;
        if (!findNearbyNodule(level, playerPos)) return;

        player.getPersistentData().putBoolean(FLAG_KEY, true);
        TwoInfectionsTrigger.INSTANCE.trigger(player);
    }

    private static boolean findNearbyNodule(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -NODULE_SCAN_RADIUS_H; dx <= NODULE_SCAN_RADIUS_H; dx++) {
            for (int dz = -NODULE_SCAN_RADIUS_H; dz <= NODULE_SCAN_RADIUS_H; dz++) {
                cursor.setX(center.getX() + dx);
                cursor.setZ(center.getZ() + dz);
                if (!level.hasChunkAt(cursor)) continue;
                for (int dy = -NODULE_SCAN_RADIUS_V; dy <= NODULE_SCAN_RADIUS_V; dy++) {
                    cursor.setY(center.getY() + dy);
                    if (level.getBlockState(cursor).is(ModBlocks.BLESSING_NODULE.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
