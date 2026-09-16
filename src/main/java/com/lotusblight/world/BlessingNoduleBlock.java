package com.lotusblight.world;

import com.lotusblight.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class BlessingNoduleBlock extends Block {
    public BlessingNoduleBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (entity instanceof LivingEntity living && !level.isClientSide) {
            living.addEffect(new MobEffectInstance(ModEffects.COLD_BLIGHT.get(), 100, 0));
        }
        super.stepOn(level, pos, state, entity);
    }
}
