package com.lotusblight.world;

import com.lotusblight.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 */
public class GlowBerryBushBlock extends BushBlock {
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.78f, 1.0f, 0.35f), 0.45f);
    /** Roughly one regrowth attempt every ~13 in-game minutes on average per bush (matches vanilla cave vine's own pacing order of magnitude). */
    private static final int REGROW_CHANCE = 1;
    private static final int REGROW_ODDS = 5;

    public GlowBerryBushBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.BERRIES, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BlockStateProperties.BERRIES);
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
