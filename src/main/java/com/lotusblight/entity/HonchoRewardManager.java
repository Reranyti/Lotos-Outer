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

/**
 * Permanent heart "blessing" for feeding Honcho vials - the positive mirror of BlackHeartManager's
 * curse. "передача Хончо делает Хончо зависимее от вас" - every vial after the first still grants
 * this (stacking, same shape as BlackHeartManager's own count-based modifier), not just a one-time
 * reward, so the bond keeps deepening the more you feed him instead of capping at one vial.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HonchoRewardManager {
    private static final UUID MODIFIER_ID = UUID.fromString("a1c3f5e7-1b2d-4a6c-8e9f-0d1c2b3a4f5e");
    private static final double BONUS_HEALTH_PER_VIAL = 2.0;

    private HonchoRewardManager() {}

    /** Called once per vial fed, however many times that ends up being - the dependency count itself is what actually accumulates. Returns the new total, so callers can react to specific milestones. */
    public static int grantBlessing(ServerPlayer player) {
        int count = LotusPlayerState.incrementHonchoDependencyCount(player);
        applyModifier(player, count);
        player.heal((float) BONUS_HEALTH_PER_VIAL);
        return count;
    }

    private static void applyModifier(ServerPlayer player, int dependencyCount) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(MODIFIER_ID);
        if (dependencyCount > 0) {
            attr.addPermanentModifier(new AttributeModifier(MODIFIER_ID, "Honcho's blessing",
                    BONUS_HEALTH_PER_VIAL * dependencyCount, AttributeModifier.Operation.ADDITION));
        }
    }

    /** Reapplies on (re)join, same reasoning as BlackHeartManager's own login hook. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        applyModifier(player, LotusPlayerState.getHonchoDependencyCount(player));
    }
}
