package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.map.ClientMapCache;
import com.lotusblight.map.MapMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gives a mature outbreak an audible presence, not just a visual one — the
 * mod has no dedicated ambient sample of its own, so this reuses vanilla
 * sounds that already carry the right feeling: amethyst's soft chime for
 * "something faintly alive in the air", sculk's crackle for "the corruption
 * is actively taking ground". Client-only, local playback (not level.playSound,
 * which would broadcast to every player) — this is purely atmosphere for
 * whoever happens to be standing there.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InfectionAmbientSound {
    private static final int CHECK_INTERVAL_TICKS = 100;

    private InfectionAmbientSound() {}

    private static double rangeForPhase(int phase) {
        return switch (phase) {
            case 4 -> 32.0;
            case 3 -> 22.0;
            default -> 0.0; // no ambient stinger below phase 3 - the haze alone carries phase 2
        };
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0) return;

        var playerPos = player.blockPosition();
        int bestPhase = 0;
        for (MapMarker marker : ClientMapCache.markers()) {
            double range = rangeForPhase(marker.phase());
            if (range <= 0) continue;
            if (marker.pos().distSqr(playerPos) >= range * range) continue;
            if (marker.phase() > bestPhase) bestPhase = marker.phase();
        }
        if (bestPhase == 0) return;

        // Roughly one stinger every ~15-25s per player while lingering near a mature outbreak,
        // not a nonstop loop - phase 4 is noisier than phase 3.
        int odds = bestPhase >= 4 ? 3 : 5;
        if (level.random.nextInt(odds) != 0) return;

        var sound = level.random.nextBoolean() ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.SCULK_BLOCK_SPREAD;
        float pitch = 0.7f + level.random.nextFloat() * 0.2f;
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, 0.5f, pitch,
                level.random, player.getX(), player.getY(), player.getZ()));
    }
}
