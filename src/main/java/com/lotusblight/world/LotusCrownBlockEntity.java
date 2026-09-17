package com.lotusblight.world;

import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBlockEntities;
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

    public LotusCrownBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOTUS_CROWN.get(), pos, state);
    }

    /** Called from a block ticker (see LotusMainBlock#getTicker) — refreshes phase only every PHASE_REFRESH_INTERVAL_TICKS, not every tick. */
    public void serverTick(ServerLevel level) {
        if (++ticksSincePhaseRefresh < PHASE_REFRESH_INTERVAL_TICKS) {
            return;
        }
        ticksSincePhaseRefresh = 0;
        OutbreakRecord nearest = OutbreakSavedData.get(level).nearestOutbreak(getBlockPos(), 4.0, false);
        if (nearest != null) {
            cachedPhase = nearest.phase();
        }
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
