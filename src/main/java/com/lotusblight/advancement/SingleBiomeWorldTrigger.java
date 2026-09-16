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
 * Easter-egg criterion trigger for the vanilla "Single Biome" world preset:
 * fired once per player when their world's chunk generator turns out to use
 * a {@link net.minecraft.world.level.biome.FixedBiomeSource} locked to
 * {@link com.lotusblight.registry.ModBiomes#LOTUS_BIOME}. See
 * {@link SingleBiomeWorldListener} for the join-time check.
 *
 * <p>This class intentionally does NOT self-register: the task forbids editing
 * {@code LotusBlight.java}. To wire it up, add this line to the mod's
 * constructor (or any place that runs during mod construction), before
 * advancements/datapacks are loaded:
 * <pre>{@code
 * net.minecraft.advancements.CriteriaTriggers.register(SingleBiomeWorldTrigger.INSTANCE);
 * }</pre>
 */
public class SingleBiomeWorldTrigger extends SimpleCriterionTrigger<SingleBiomeWorldTrigger.TriggerInstance> {
    public static final ResourceLocation ID = new ResourceLocation(LotusBlight.MODID, "single_biome_world");
    public static final SingleBiomeWorldTrigger INSTANCE = new SingleBiomeWorldTrigger();

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate playerPredicate, DeserializationContext context) {
        return new TriggerInstance(playerPredicate);
    }

    /** Fires the trigger for a player, once they've been confirmed to be in a single-biome Lotus world. */
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
