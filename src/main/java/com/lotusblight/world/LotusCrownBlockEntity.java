package com.lotusblight.world;

import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBlockEntities;
import com.lotusblight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Only ever placed on the crown part (PART=3) of a LotusMainBlock stack —
 * see LotusMainBlock#newBlockEntity. Drives the idle "breathing" bloom
 * animation whose speed scales with the nearest outbreak's phase (slow and
 * calm at phase 1, faster by phase 4 — user's explicit design call).
 */
public class LotusCrownBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final RawAnimation BLOOM_IDLE = RawAnimation.begin().thenLoop("bloom_idle");
    private static final int PHASE_REFRESH_INTERVAL_TICKS = 60;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int cachedPhase = 1;
    private int ticksSincePhaseRefresh = Integer.MAX_VALUE;
    private boolean checkedForLegacyStack;

    public LotusCrownBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOTUS_CROWN.get(), pos, state);
    }

    /** Called from a block ticker (see LotusMainBlock#getTicker) — refreshes phase only every PHASE_REFRESH_INTERVAL_TICKS, not every tick. */
    public void serverTick(ServerLevel level) {
        if (!checkedForLegacyStack) {
            checkedForLegacyStack = true;
            if (removeIfLegacyStackSegment(level)) {
                return;
            }
        }
        if (++ticksSincePhaseRefresh < PHASE_REFRESH_INTERVAL_TICKS) {
            return;
        }
        ticksSincePhaseRefresh = 0;
        OutbreakRecord nearest = OutbreakSavedData.get(level).nearestOutbreak(getBlockPos(), 4.0, false);
        if (nearest != null) {
            cachedPhase = nearest.phase();
        }
    }

    /**
     * LotusMainBlock used to be a real 4-tall stack of separate blocks (PART=0..3), each now
     * independently rendering as its own giant lily pad after the "no more столбы" redesign — a
     * world saved before that change still has all 4 old block positions sitting there, so a
     * single old pillar now looks like several overlapping oversized pads instead of one. This
     * position is a leftover stem/crown segment (not the real anchor) if it doesn't match any
     * registered outbreak AND the block directly below it is also part of the same block/heart —
     * i.e. it's sitting on top of another lotus block, which a freshly-placed anchor never does.
     */
    private boolean removeIfLegacyStackSegment(ServerLevel level) {
        BlockPos pos = getBlockPos();
        boolean isRegisteredAnchor = OutbreakSavedData.get(level).allOutbreaks().stream()
                .anyMatch(record -> record.pos().equals(pos));
        if (isRegisteredAnchor) return false;

        var below = level.getBlockState(pos.below());
        if (below.is(ModBlocks.INFECTED_LOTUS.get()) || below.is(ModBlocks.LOTUS_HEART.get())) {
            level.removeBlock(pos, false);
            return true;
        }
        return false;
    }

    /** 1 at phase 1 (slow, calm) up to ~2.2x at phase 4 (fast, agitated) — see InfectionPhases for the phase scale itself. */
    private double animationSpeed() {
        return 0.7 + (cachedPhase - 1) * 0.5;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, state -> {
            state.getController().setAnimationSpeed(animationSpeed());
            return state.setAndContinue(BLOOM_IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
