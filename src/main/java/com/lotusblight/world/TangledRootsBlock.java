package com.lotusblight.world;

import com.lotusblight.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Underwater obstruction variant of {@link LotusRootsBlock}. The regular
 * roots block is purely cosmetic (no collision, doesn't interrupt movement);
 * this one is grown by the roots-and-mini-lotuses system
 * ({@code com.lotusblight.spread.roots}) specifically to visibly slow and
 * obstruct a player swimming through an infected waterway, so unlike its
 * cousin it exposes real collision and briefly slows anything that pushes
 * through it.
 */
public final class TangledRootsBlock extends Block implements SimpleWaterloggedBlock {

    // Was 10/16 tall - close enough to a full block that it effectively walled off the waterway
    // instead of just slowing a swim through it. Lowered so there's real clearance above to swim
    // through, matching the "obstruction, not a wall" intent in the class doc below.
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 6.0, 16.0);

    /**
     * Whether the fluid this block is waterlogged with was actually INFECTED_WATER rather than
     * vanilla water. getFluidState() used to hardcode Fluids.WATER regardless, so every tangled
     * root grown on top of an infected water source visibly turned that tile's rendered fluid
     * back into plain blue water right where the root sat - as roots kept growing across an
     * infected waterway, patches of "infected water" appeared to flicker back to normal water one
     * by one. This tracks which fluid was really there so the block reports it correctly.
     */
    public static final BooleanProperty INFECTED = BooleanProperty.create("infected");

    public TangledRootsBlock(BlockBehaviour.Properties properties) {
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
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.scheduleTick(pos, state.getValue(INFECTED) ? com.lotusblight.registry.ModFluids.INFECTED_WATER.get() : Fluids.WATER,
                    (state.getValue(INFECTED) ? com.lotusblight.registry.ModFluids.INFECTED_WATER.get() : Fluids.WATER).getTickDelay(level));
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // Used to only slow the entity down - the wiki/lore have always talked about infected
        // roots/water spreading spores through contact, but tangled_roots (the one root variant
        // you actually have to push through) never applied any.
        if (!level.isClientSide && entity instanceof LivingEntity living && !(entity instanceof Player player && player.getAbilities().invulnerable)) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false, true));
            if (level.random.nextInt(20) == 0) {
                living.addEffect(new MobEffectInstance(ModEffects.LOTUS_SPORES.get(), 100, 0));
            }
        }
        super.entityInside(state, level, pos, entity);
    }
}
