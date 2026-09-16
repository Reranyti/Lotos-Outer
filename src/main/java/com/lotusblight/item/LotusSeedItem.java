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
                // The vanilla water remains intact; the flower grows on its surface.
                level.setBlock(pos.above(), ModBlocks.INFECTED_LOTUS.get().defaultBlockState(), 3);
                // The heart is placed on the bank only when there is solid ground beside the water.
                BlockPos bank = pos.relative(context.getHorizontalDirection());
                if (level.getBlockState(bank).isFaceSturdy(level, bank, context.getHorizontalDirection().getOpposite())
                        && level.getBlockState(bank.above()).isAir()) {
                    level.setBlock(bank.above(), ModBlocks.LOTUS_HEART.get().defaultBlockState(), 3);
                    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        LotusEvents.rememberAnchor(serverLevel, bank.above());
                    }
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
