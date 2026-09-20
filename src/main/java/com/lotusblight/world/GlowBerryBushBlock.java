package com.lotusblight.world;

import com.lotusblight.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3f;

/**
 * The edible "glowing_berry" item (ModItems#GLOW_BERRY_FOOD) used to have no
 * way to actually reach a player's inventory at all — breaking this bush just
 * dropped itself back (the decorative block-item), and nothing else in the
 * mod ever granted the food item. Right-clicking now harvests berries
 * without breaking the bush, matching vanilla cave-vine glow berries.
 *
 * That first fix had no state at all, though - every right-click granted 1-2
 * more berries forever, an infinite food source with no cooldown or limit.
 * BERRIES (reusing vanilla's own BERRIES boolean, same as cave vines) now
 * gates the harvest: empty until a random tick regrows it, same shape as
 * vanilla's own glow berry vines.
 *
 * Implements CaveVines (a plain interface with static helpers, not a class to
 * extend) instead of reimplementing that logic from scratch - not for its
 * use() (that hardcodes dropping vanilla's own Items.GLOW_BERRIES, no good
 * for our own GLOW_BERRY_FOOD), but for hasGlowBerries()/emission(), so this
 * block gets vanilla's real behavior of only glowing at full brightness while
 * berries are actually present instead of a flat light level regardless of
 * state (see ModBlocks' registration for the emission() wiring).
 */
public class GlowBerryBushBlock extends BushBlock implements CaveVines {
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.78f, 1.0f, 0.35f), 0.45f);
    /** Roughly one regrowth attempt every ~13 in-game minutes on average per bush (matches vanilla cave vine's own pacing order of magnitude). */
    private static final int REGROW_CHANCE = 1;
    private static final int REGROW_ODDS = 5;

    /**
     * True when this vine is pinned to the side of something (see BlessingSandPillarFeature)
     * instead of grown up out of soil - the model needs to know, because a plant "cross" render
     * looks wrong sticking sideways out of a wall the way a real wall-clinging vine wouldn't (see
     * the blockstate: wall_mounted picks a flat wall-panel model instead of the floor cross, using
     * FACING to point it away from whatever it's attached to).
     */
    public static final BooleanProperty WALL_MOUNTED = BooleanProperty.create("wall_mounted");

    public GlowBerryBushBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(BlockStateProperties.BERRIES, true)
                .setValue(WALL_MOUNTED, false)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** Convenience for placement code (e.g. BlessingSandPillarFeature) - facing points AWAY from the surface it clings to. */
    public BlockState wallMountedState(Direction facing) {
        return defaultBlockState().setValue(WALL_MOUNTED, true).setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BlockStateProperties.BERRIES, WALL_MOUNTED, BlockStateProperties.HORIZONTAL_FACING);
    }

    /**
     * BushBlock's own canSurvive (mayPlaceOn) only accepts BlockTags.DIRT or farmland directly
     * below - but this block has NEVER once actually been placed on real dirt anywhere in the mod:
     * Blessing patches grow it on blessing_soil/snow_block/stone, the worldgen feature places it on
     * the same set, and Mossy Glands/the sand-pillar waypoint put it on moss_block/tuff/clay or pin
     * it to a wall - none of those are dirt-tagged. Every single placement site would have failed
     * the inherited check and popped the block on the very next neighbor update. Replaced entirely
     * with "any sturdy support, above or to the side" instead of layering a narrow side-only
     * exception on top of an overly strict ground rule that never actually matched real usage.
     */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockGetter getter = level;
        if (getter.getBlockState(pos.below()).isFaceSturdy(getter, pos.below(), Direction.UP)) return true;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            if (getter.getBlockState(neighborPos).isFaceSturdy(getter, neighborPos, direction.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!state.getValue(BlockStateProperties.BERRIES)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int count = 1 + level.random.nextInt(2);
        ItemStack drop = new ItemStack(ModItems.GLOW_BERRY_FOOD.get(), count);
        if (!player.getInventory().add(drop)) {
            player.drop(drop, false);
        }
        level.setBlock(pos, state.setValue(BlockStateProperties.BERRIES, false), 3);
        level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0f, 0.9f);
        level.addParticle(GLOW, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 0, 0.02, 0);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(BlockStateProperties.BERRIES);
    }

    @Override
    public void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(BlockStateProperties.BERRIES) && random.nextInt(REGROW_ODDS) < REGROW_CHANCE) {
            level.setBlock(pos, state.setValue(BlockStateProperties.BERRIES, true), 3);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(BlockStateProperties.BERRIES) && random.nextInt(4) == 0) {
            level.addParticle(GLOW, pos.getX() + random.nextDouble(), pos.getY() + 0.55, pos.getZ() + random.nextDouble(), 0, 0.005, 0);
        }
    }
}
