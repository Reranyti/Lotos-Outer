package com.lotusblight.map;

import com.lotusblight.LotusBlight;
import com.lotusblight.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Companion to {@link ChatOverhaulBranchColor}: reverts the gold True Light chat tint the moment it wears off. */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrueLightExpiryListener {
    private TrueLightExpiryListener() {}

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (!ChatOverhaulBranchColor.LOADED) return;
        if (event.getEffectInstance() == null || event.getEffectInstance().getEffect() != ModEffects.TRUE_LIGHT.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ChatOverhaulBranchColor.revertToBranchColor(player);
    }
}
