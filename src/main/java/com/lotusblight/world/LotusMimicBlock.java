package com.lotusblight.world;

import com.lotusblight.registry.ModEffects;
import com.lotusblight.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public class LotusMimicBlock extends Block {
    private static final DustParticleOptions MIMIC_DUST = new DustParticleOptions(new Vector3f(0.25f, 0.95f, 0.35f), 1.0f);
    // Matches the small pad+flower model's actual footprint instead of the default full cube.
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = Block.box(1, 0, 1, 15, 11, 15);

    public LotusMimicBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof LivingEntity living) || level.isClientSide) return;
        if (living.getPersistentData().getLong("LotusMimicCooldown") > level.getGameTime()) return;

        living.getPersistentData().putLong("LotusMimicCooldown", level.getGameTime() + 80L);
        living.addEffect(new MobEffectInstance(ModEffects.LOTONIRIYA.get(), 20 * 18, 0, false, true, true));
        // Server side here - addParticle would do nothing, the burst has to be sent to clients.
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(MIMIC_DUST, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 24, 0.35, 0.22, 0.35, 0.03);
        }
        level.playSound(null, pos, SoundEvents.SLIME_ATTACK, SoundSource.BLOCKS, 0.65f, 0.75f);
        // Was a one-shot "trap": apply an effect, then turn into a harmless decorative
        // ModBlocks.LOTUS_SHOOT and never act again (bug #22 - "это не ловушка а реальное
        // существо", the mimic is lore-established as the lotus's own creature, disguised as an
        // ordinary block). A follow-up attempt at this replaced the block with a spawned Silverfish
        // entity - explicitly rejected too, the mimic itself must stay put and keep triggering
        // (it does not vanish/despawn when it catches something).
        //
        // Now also shared with the mossy-caves variant of this same creature ("лотосовый мимик и
        // мшистый мимик это одно и то же" - same lineage, same block, just placed in a different
        // biome) - real bite/scare payoff instead of only the status effect: a hit of damage,
        // Blindness so the reveal actually disorients, and a scrambled inventory (for players only
        // - nothing else has slots worth shuffling) so getting caught costs something concrete. The
        // cleansing powder drop is the same "danger needs a payoff" reasoning GuardianManager uses
        // for guardian kills - this creature already spent its one surprise on you either way.
        living.hurt(level.damageSources().magic(), 3.0f);
        living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 5, 0, false, true, true));
        if (living instanceof Player player) {
            shuffle(player.getInventory().items, level);
            player.getInventory().add(new ItemStack(ModItems.CLEANSING_POWDER.get(), 1));
        }
    }

    /** Fisher-Yates over a live NonNullList - Collections.shuffle wants a java.util.Random, not a RandomSource. */
    private static void shuffle(java.util.List<ItemStack> items, Level level) {
        for (int i = items.size() - 1; i > 0; i--) {
            int j = level.random.nextInt(i + 1);
            ItemStack tmp = items.get(i);
            items.set(i, items.get(j));
            items.set(j, tmp);
        }
    }
}

