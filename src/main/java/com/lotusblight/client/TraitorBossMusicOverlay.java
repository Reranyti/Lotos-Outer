package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Looping playback for the traitor boss fight's theme, driven by {@link TraitorBossFight} via
 * {@code com.lotusblight.map.TraitorBossThemePacket}. Same static-state-plus-tick-handler shape as
 * {@link WorldLotusLectureOverlay}/{@link InnerVoiceOverlay}, but neither of those ever needs to
 * loop (their SimpleSoundInstances just play once per line/scene) - there's no existing precedent
 * in this codebase for a truly looping SimpleSoundInstance, so this approximates one by polling
 * whether playback is still active each client tick and restarting it when it isn't.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TraitorBossMusicOverlay {
    private static boolean shouldLoop;
    private static SimpleSoundInstance instance;

    private TraitorBossMusicOverlay() {}

    public static void start() {
        shouldLoop = true;
        if (instance != null) {
            Minecraft.getInstance().getSoundManager().stop(instance);
        }
        instance = SimpleSoundInstance.forUI(ModSounds.TRAITOR_BOSS_THEME.get(), 1.0f, 1.0f);
        Minecraft.getInstance().getSoundManager().play(instance);
    }

    public static void stop() {
        shouldLoop = false;
        if (instance != null) {
            Minecraft.getInstance().getSoundManager().stop(instance);
            instance = null;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!shouldLoop) return;
        // isActive() false once the track finishes - that's the "loop point" this polls for.
        if (instance == null || !Minecraft.getInstance().getSoundManager().isActive(instance)) {
            instance = SimpleSoundInstance.forUI(ModSounds.TRAITOR_BOSS_THEME.get(), 1.0f, 1.0f);
            Minecraft.getInstance().getSoundManager().play(instance);
        }
    }
}
