package com.lotusblight.data;

import com.lotusblight.LotusBlight;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge only copies the "PlayerPersisted" sub-tag of getPersistentData() onto the new player
 * entity on respawn / End exit - everything LotusPlayerState and the advancement listeners keep
 * under their own "LotusBlight*" keys was silently dropped, so dying reset the branch, the traitor
 * flag, Honcho progress, StarFall, reputation and the one-time dialogue gifts.
 *
 * Runs at HIGHEST so every other Clone handler (BlackHeartManager, TrueLightHeartsManager,
 * HonchoRewardManager) already sees the carried-over state on the new player.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerStateCarryOver {
    private static final String KEY_PREFIX = "LotusBlight";

    private PlayerStateCarryOver() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag from = event.getOriginal().getPersistentData();
        CompoundTag to = event.getEntity().getPersistentData();
        for (String key : from.getAllKeys()) {
            if (!key.startsWith(KEY_PREFIX)) continue;
            var value = from.get(key);
            if (value != null) {
                to.put(key, value.copy());
            }
        }
    }
}
