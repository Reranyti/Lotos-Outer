package com.lotusblight.entity;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.LotusPlayerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Permanent +1 heart "blessing" for completing Honcho's quest - the positive mirror of BlackHeartManager's curse. */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HonchoRewardManager {
    private static final UUID MODIFIER_ID = UUID.fromString("a1c3f5e7-1b2d-4a6c-8e9f-0d1c2b3a4f5e");
    private static final double BONUS_HEALTH = 2.0;

    private HonchoRewardManager() {}

    public static void grantBlessing(ServerPlayer player) {
        applyModifier(player);
        player.heal((float) BONUS_HEALTH);
    }

    private static void applyModifier(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(MODIFIER_ID);
        attr.addPermanentModifier(new AttributeModifier(MODIFIER_ID, "Honcho's blessing",
                BONUS_HEALTH, AttributeModifier.Operation.ADDITION));
    }

    /** Reapplies on (re)join, same reasoning as BlackHeartManager's own login hook. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!LotusPlayerState.hasCompletedHonchoQuest(player)) return;
        applyModifier(player);
    }
}
