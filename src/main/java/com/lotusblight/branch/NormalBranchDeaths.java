package com.lotusblight.branch;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.spread.GuardianManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Watches for the exact, unbroken run of deaths that opens the Нормальная_ветка, for a player who has
 * not committed to any ordinary branch. The run is: three drownings beside the main lotus, then three
 * deaths to a lotus guardian, then two deaths by suffocation - strictly in that order. Any death that is
 * not the next one expected puts the count back to zero. The player is never told any of this; the only
 * outward sign is that after the three drownings the main lotus stops answering (see LotusPlayerState).
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID)
public final class NormalBranchDeaths {
    private static final int DROWN_RADIUS = 16;   // drownings only count this close to the main lotus

    private NormalBranchDeaths() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (LotusPlayerState.getDialogueBranch(player) != LotusPlayerState.BRANCH_UNDECIDED) return;
        if (LotusPlayerState.isNormalBranchClosed(player) || LotusPlayerState.isNormalBranchEntered(player)) return;

        int step = LotusPlayerState.getNormalBranchStep(player);
        if (matchesExpected(player, event, step)) {
            step++;
            LotusPlayerState.setNormalBranchStep(player, step);
            if (step >= LotusPlayerState.NORMAL_BRANCH_STEPS) {
                NormalBranchEntry.begin(player);
            }
        } else if (step > 0) {
            LotusPlayerState.setNormalBranchStep(player, 0);
        }
    }

    /** Whether this death is the one the run needs next at the given step. */
    private static boolean matchesExpected(ServerPlayer player, LivingDeathEvent event, int step) {
        if (step < 3) {
            return event.getSource().is(DamageTypes.DROWN) && nearMainLotus(player);
        } else if (step < 6) {
            return event.getSource().getEntity() instanceof Wolf wolf
                    && wolf.getTags().contains(GuardianManager.GUARDIAN_TAG);
        } else {
            return event.getSource().is(DamageTypes.IN_WALL);
        }
    }

    /** True if an INFECTED_LOTUS block sits within DROWN_RADIUS of where the player died. */
    private static boolean nearMainLotus(ServerPlayer player) {
        Level level = player.level();
        BlockPos origin = player.blockPosition();
        int r = DROWN_RADIUS;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -r; dy <= r; dy++) {
                    p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(p)) continue;
                    if (level.getBlockState(p).is(ModBlocks.INFECTED_LOTUS.get())) {
                        if (p.distSqr(origin) <= (double) r * r) return true;
                    }
                }
            }
        }
        return false;
    }
}
