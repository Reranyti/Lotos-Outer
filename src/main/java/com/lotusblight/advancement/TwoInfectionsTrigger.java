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
 * Criterion trigger for {@code lotusblight:two_infections}: fired once a
 * player has simultaneously been within detection range of BOTH a lotus
 * outbreak anchor and a Blessing-biome suppressor block ({@code
 * lotusblight:blessing_nodule}) — "seeing both sides" of the mod's central
 * conflict at once. Neither vanilla {@code minecraft:location} (single
 * predicate, can't AND two independent proximity checks against dynamic,
 * per-world outbreak positions) nor {@code minecraft:inventory_changed}
 * (this is about seeing, not holding) fit, so this is a hand-written
 * criterion in the same style as {@link SingleBiomeWorldTrigger}.
 *
 * <p>See {@link TwoInfectionsListener} for the periodic proximity check.
 * Registered in {@code LotusBlight.java}'s constructor via {@code
 * CriteriaTriggers.register(TwoInfectionsTrigger.INSTANCE)}.
 */
public class TwoInfectionsTrigger extends SimpleCriterionTrigger<TwoInfectionsTrigger.TriggerInstance> {
    public static final ResourceLocation ID = new ResourceLocation(LotusBlight.MODID, "two_infections");
    public static final TwoInfectionsTrigger INSTANCE = new TwoInfectionsTrigger();

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate playerPredicate, DeserializationContext context) {
        return new TriggerInstance(playerPredicate);
    }

    /** Fires the trigger for a player who's been confirmed to be near both an outbreak and a suppressor block. */
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
