package com.lotusblight.item;

import com.lotusblight.registry.ModBlocks;
import com.lotusblight.world.LotusEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class LotusSeedItem extends Item {
    public LotusSeedItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos).isSource()) {
            if (!level.isClientSide) {
                // The vanilla water remains intact; the flower grows on its surface. This used to
                // also drop a LOTUS_HEART on the bank immediately — a leftover from before phase
                // progression existed, so a fully-formed heart could appear the instant a seed was
                // planted, completely bypassing the phase system. The heart is now something an
                // outbreak earns by reaching phase 4 naturally (see
                // InfectionSpreadEngine#maybeSpawnHeart); planting a seed just starts a normal
                // outbreak like GuaranteedSpawnManager's natural ones do.
                BlockPos flowerPos = pos.above();
                level.setBlock(flowerPos, ModBlocks.INFECTED_LOTUS.get().defaultBlockState(), 3);
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    LotusEvents.rememberAnchor(serverLevel, flowerPos);
                }
                if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                    context.getItemInHand().shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
