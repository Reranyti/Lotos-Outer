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
 * Criterion trigger for {@code lotusblight:survivor_of_the_bloom}: fired
 * once a player has spent a continuous stretch of time within range of a
 * mature (phase 4) outbreak. There's no vanilla criterion for "near a
 * dynamic position for N ticks", so this is a hand-written criterion in the
 * same style as {@link SingleBiomeWorldTrigger}; the actual duration
 * bookkeeping lives in {@link SurvivorOfTheBloomListener}.
 *
 * <p>Registered in {@code LotusBlight.java}'s constructor via {@code
 * CriteriaTriggers.register(SurvivorOfTheBloomTrigger.INSTANCE)}.
 */
public class SurvivorOfTheBloomTrigger extends SimpleCriterionTrigger<SurvivorOfTheBloomTrigger.TriggerInstance> {
    public static final ResourceLocation ID = new ResourceLocation(LotusBlight.MODID, "survivor_of_the_bloom");
    public static final SurvivorOfTheBloomTrigger INSTANCE = new SurvivorOfTheBloomTrigger();

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate playerPredicate, DeserializationContext context) {
        return new TriggerInstance(playerPredicate);
    }

    /** Fires the trigger for a player who has endured close enough to a phase-4 outbreak for long enough. */
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
