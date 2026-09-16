package com.lotusblight.item;

import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.spread.SpreadTables;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * A tool for players who have committed to the "join the lotus" ending
 * branch (see {@link LotusPlayerState#hasJoinedLotus(Player)}). Right-click
 * to actively force-convert nearby clean blocks into their infected
 * analogues via {@link SpreadTables}, exactly like the passive spread engine
 * does, but on demand and centered on the player.
 *
 * Does nothing for a player who hasn't joined — this item does not itself
 * decide who has joined; that trigger flow is separate, future work.
 */
public final class LotusGraftingRodItem extends Item {

    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.2f, 0.95f, 0.35f), 1.0f);
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.55f), 1.0f);
    private static final int RADIUS = 4;
    private static final int ATTEMPTS = 14;
    private static final int COOLDOWN_TICKS = 20;

    public LotusGraftingRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!LotusPlayerState.hasJoinedLotus(player)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("item.lotusblight.lotus_grafting_rod.not_joined"), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        int converted = graft(serverLevel, player.blockPosition());
        if (converted > 0) {
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.GRASS_PLACE, SoundSource.PLAYERS, 0.6f, 0.8f);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        stack.hurtAndBreak(1, player, living -> living.broadcastBreakEvent(hand));
        return InteractionResultHolder.success(stack);
    }

    private int graft(ServerLevel level, BlockPos center) {
        OutbreakSavedData data = OutbreakSavedData.get(level);
        int converted = 0;
        for (int i = 0; i < ATTEMPTS; i++) {
            BlockPos target = randomNearby(level, center);
            if (!level.hasChunkAt(target)) continue;
            if (tryConvert(level, data, target)) {
                converted++;
            }
        }
        return converted;
    }

    private boolean tryConvert(ServerLevel level, OutbreakSavedData data, BlockPos target) {
        BlockState state = level.getBlockState(target);

        if (SpreadTables.isCleanGrass(state)) {
            BlockState below = level.getBlockState(target.below());
            if (SpreadTables.isInfectedGround(below)) {
                BlockState infected = SpreadTables.infectedGrass(state);
                if (infected != null) {
                    level.setBlock(target, infected, 3);
                    data.incrementChunkCount(new ChunkPos(target), 1);
                    bloom(level, target, GREEN);
                    return true;
                }
            }
            return false;
        }

        if (SpreadTables.isCleanLog(state) || SpreadTables.isCleanLeaves(state)) {
            level.setBlock(target, SpreadTables.isCleanLog(state) ? SpreadTables.infectedLog() : SpreadTables.infectedLeaves(), 3);
            data.incrementChunkCount(new ChunkPos(target), 1);
            bloom(level, target, GREEN);
            return true;
        }

        if (SpreadTables.isCleanGround(state)) {
            BlockState replacement = SpreadTables.infectedGroundReplacement(state);
            if (replacement != null) {
                level.setBlock(target, replacement, 3);
                data.incrementChunkCount(new ChunkPos(target), 1);
                bloom(level, target, PINK);
                return true;
            }
        }

        return false;
    }

    private BlockPos randomNearby(ServerLevel level, BlockPos center) {
        int dx = level.random.nextInt(RADIUS * 2 + 1) - RADIUS;
        int dy = level.random.nextInt(3) - 1;
        int dz = level.random.nextInt(RADIUS * 2 + 1) - RADIUS;
        return center.offset(dx, dy, dz);
    }

    private void bloom(ServerLevel level, BlockPos pos, DustParticleOptions color) {
        level.sendParticles(color, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 6, 0.3, 0.25, 0.3, 0.01);
        level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.3f, 1.5f);
    }
}
