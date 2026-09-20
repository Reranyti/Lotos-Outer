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
 * Criterion trigger for {@code lotusblight:guardian_pack_tamed} - fired once a player's total
 * tamed-guardian count (see LotusPlayerState#getTamedGuardianCount) reaches
 * GuardianManager#PACK_ADVANCEMENT_SIZE (13). Vanilla criteria are plain booleans with no built-in
 * counting, so the count check happens in GuardianManager itself and this only ever fires once
 * that threshold is actually crossed - a separate trigger from GuardianTamedTrigger (which fires
 * on every single tame) so the two advancements can't be conflated into one criterion ID.
 */
public class GuardianPackTamedTrigger extends SimpleCriterionTrigger<GuardianPackTamedTrigger.TriggerInstance> {
    public static final ResourceLocation ID = new ResourceLocation(LotusBlight.MODID, "guardian_pack_tamed");
    public static final GuardianPackTamedTrigger INSTANCE = new GuardianPackTamedTrigger();

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate playerPredicate, DeserializationContext context) {
        return new TriggerInstance(playerPredicate);
    }

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
