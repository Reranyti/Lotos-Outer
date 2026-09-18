package com.lotusblight.world;

import com.lotusblight.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.player.Player;

/** Decorative roots which preserve water through a waterlogged block state. */
public final class LotusRootsBlock extends Block implements SimpleWaterloggedBlock {

    /**
     * Whether the fluid this block is waterlogged with was actually INFECTED_WATER rather than
     * vanilla water. getFluidState() used to hardcode Fluids.WATER regardless of what was really
     * there - InfectionSpreadEngine places this block directly on top of infected water sources,
     * so every such spot visibly reverted to plain blue water the instant the root replaced it,
     * reading as infected water randomly flickering/disappearing as roots kept growing.
     */
    public static final BooleanProperty INFECTED = BooleanProperty.create("infected");

    public LotusRootsBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.WATERLOGGED, false).setValue(INFECTED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        boolean infected = fluid.is(com.lotusblight.registry.ModFluids.INFECTED_WATER.get());
        boolean waterlogged = fluid.getType() == Fluids.WATER || infected;
        return defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, waterlogged).setValue(INFECTED, infected);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.WATERLOGGED, INFECTED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        if (!state.getValue(BlockStateProperties.WATERLOGGED)) return super.getFluidState(state);
        return state.getValue(INFECTED) ? com.lotusblight.registry.ModFluids.INFECTED_WATER.get().getSource(false) : Fluids.WATER.getSource(false);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 0.0f;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.scheduleTick(pos, state.getValue(INFECTED) ? com.lotusblight.registry.ModFluids.INFECTED_WATER.get() : Fluids.WATER,
                    (state.getValue(INFECTED) ? com.lotusblight.registry.ModFluids.INFECTED_WATER.get() : Fluids.WATER).getTickDelay(level));
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // The wiki/lore have always described these roots as spreading spores on contact - this
        // block (the one actually grown by natural water spread) never applied any, unlike its
        // underwater-obstruction cousin TangledRootsBlock.
        if (!level.isClientSide && entity instanceof LivingEntity living && !(entity instanceof Player player && player.getAbilities().invulnerable)) {
            if (level.random.nextInt(20) == 0) {
                living.addEffect(new MobEffectInstance(ModEffects.LOTUS_SPORES.get(), 100, 0));
            }
        }
        super.entityInside(state, level, pos, entity);
    }
}
