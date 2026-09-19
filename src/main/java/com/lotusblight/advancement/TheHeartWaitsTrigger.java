package com.lotusblight.advancement;

import com.google.gson.JsonObject;
import com.lotusblight.LotusBlight;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Criterion trigger for {@code lotusblight:the_heart_waits}: fired once a
 * player gets within sight of the world's single {@code lotusblight:lotus_heart}
 * block (grown at the anchor of whichever outbreak first reaches phase 4 and
 * claims the heart — see {@link com.lotusblight.data.OutbreakSavedData#claimHeart()}).
 * This is about encountering the landmark, not picking up the item, so
 * {@code minecraft:inventory_changed} doesn't fit; and because the heart's
 * position isn't known ahead of time (it's wherever an outbreak happened to
 * mature first) a static {@code minecraft:location} predicate can't target
 * it either. Hand-written criterion in the same style as
 * {@link SingleBiomeWorldTrigger}; see {@link TheHeartWaitsListener} for the
 * periodic proximity check.
 *
 * <p>Registered in {@code LotusBlight.java}'s constructor via {@code
 * CriteriaTriggers.register(TheHeartWaitsTrigger.INSTANCE)}.
 */
public class TheHeartWaitsTrigger extends SimpleCriterionTrigger<TheHeartWaitsTrigger.TriggerInstance> {
    public static final ResourceLocation ID = new ResourceLocation(LotusBlight.MODID, "the_heart_waits");
    public static final TheHeartWaitsTrigger INSTANCE = new TheHeartWaitsTrigger();

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate playerPredicate, DeserializationContext context) {
        return new TriggerInstance(playerPredicate);
    }

    /** Fires the trigger for a player who's been confirmed to be near the world's lotus heart. */
    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> true);
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {
        public TriggerInstance(ContextAwarePredicate playerPredicate) {
            super(ID, playerPredicate);
        }

        public static TriggerInstance unlocked() {
            return new TriggerInstance(ContextAwarePredicate.ANY);
        }
    }
}
