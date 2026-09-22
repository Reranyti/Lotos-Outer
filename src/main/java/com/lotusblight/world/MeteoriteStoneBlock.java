package com.lotusblight.world;

import com.lotusblight.data.LotusPlayerState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "метеоритный камень убивает при прикосновении любом на войне, на нейтрале не даёт добыть" - left
 * behind at the exact center of a StarFall meteor impact. Branch-gated: kills a RESISTANCE player
 * on contact, refuses to be mined at all on UNDECIDED, and only actually behaves like an ordinary
 * minable block on ALLIANCE.
 */
public class MeteoriteStoneBlock extends Block {
    private static final float LETHAL_DAMAGE = 1000.0f;

    public MeteoriteStoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        if (LotusPlayerState.getDialogueBranch(player) != LotusPlayerState.BRANCH_RESISTANCE) return;
        if (level instanceof ServerLevel serverLevel) {
            player.hurt(serverLevel.damageSources().magic(), LETHAL_DAMAGE);
        }
    }

    /** UNDECIDED (neutral) can't harvest it at all - matches getDestroyProgress(0) precedent elsewhere in this mod for "looks solid, isn't actually minable". */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (LotusPlayerState.getDialogueBranch(player) == LotusPlayerState.BRANCH_UNDECIDED) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }
}
