package com.lotusblight.item;

import com.lotusblight.registry.ModBlocks;
import com.lotusblight.world.LotusEvents;
import com.lotusblight.worldgen.WaterClearance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
        if (!(level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos).isSource())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        // The anchor is a big lily pad now — planting it right at a shore would clip into land.
        // Only ordinary decorative shoots are fine at the water's edge.
        if (!WaterClearance.hasClearWaterAround(serverLevel, pos, WaterClearance.REQUIRED_RADIUS)) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.literal(
                        "Тут слишком мелко и близко к берегу — лотосу нужна открытая вода вокруг."), true);
            }
            return InteractionResult.FAIL;
        }

        // The vanilla water remains intact; the flower grows on its surface. This used to also
        // drop a LOTUS_HEART on the bank immediately — a leftover from before phase progression
        // existed, so a fully-formed heart could appear the instant a seed was planted. The heart
        // is now something an outbreak earns by reaching phase 4 naturally (see
        // InfectionSpreadEngine#maybeSpawnHeart); planting a seed just starts a normal outbreak
        // like GuaranteedSpawnManager's natural ones do.
        BlockPos flowerPos = pos.above();
        // Neither this check nor WaterClearance.hasClearWaterAround inspects what's directly above
        // the targeted water source - a roofed cistern, a flooded low-ceiling cave pocket, or a pond
        // under an overhang could have a solid block there, which setBlock below would silently
        // destroy with no drop the moment a seed was planted.
        if (!serverLevel.getBlockState(flowerPos).isAir()) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.literal(
                        "Тут не хватает места над водой для ростка."), true);
            }
            return InteractionResult.FAIL;
        }
        serverLevel.setBlock(flowerPos, ModBlocks.INFECTED_LOTUS.get().defaultBlockState(), 3);
        LotusEvents.rememberAnchor(serverLevel, flowerPos);
        if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
