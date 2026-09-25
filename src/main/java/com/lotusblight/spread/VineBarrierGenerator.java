package com.lotusblight.spread;

import com.lotusblight.data.BarrierRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Grows a physical vine-wall barrier across a passage near a phase-4
 * outbreak. A 3-wide, 3-tall wall of {@code LianaBarrierBlock} is placed
 * perpendicular to a random horizontal axis, with three
 * {@code LianaWeakPointBlock} cut points at foot level — cutting all three
 * (see BarrierEvents) removes the whole wall.
 */
public final class VineBarrierGenerator {

    private VineBarrierGenerator() {
    }

    public static boolean tryGrow(ServerLevel level, UUID outbreakId, BlockPos origin) {
        Direction.Axis axis = level.random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
        // The wall spans one block either side of origin - either end can sit in an unloaded chunk.
        BlockPos endA = axis == Direction.Axis.X ? origin.offset(-1, 0, 0) : origin.offset(0, 0, -1);
        BlockPos endB = axis == Direction.Axis.X ? origin.offset(1, 0, 0) : origin.offset(0, 0, 1);
        if (!level.hasChunkAt(endA) || !level.hasChunkAt(endB)) {
            return false;
        }
        List<BlockPos> solid = new ArrayList<>();
        List<BlockPos> weakPoints = new ArrayList<>();

        int replaceable = 0;
        int total = 0;
        for (int span = -1; span <= 1; span++) {
            for (int height = 0; height <= 2; height++) {
                BlockPos pos = axis == Direction.Axis.X
                        ? origin.offset(span, height, 0)
                        : origin.offset(0, height, span);
                total++;
                if (isReplaceable(level.getBlockState(pos))) {
                    replaceable++;
                }
            }
        }
        // Only grow where it actually reads as "blocking a passage", not carving through solid terrain.
        if (replaceable < total - 1) {
            return false;
        }
        if (!level.getBlockState(origin.below()).isSolidRender(level, origin.below())) {
            return false;
        }

        for (int span = -1; span <= 1; span++) {
            for (int height = 0; height <= 2; height++) {
                BlockPos pos = axis == Direction.Axis.X
                        ? origin.offset(span, height, 0)
                        : origin.offset(0, height, span);
                // The check above tolerates one solid cell - leave it standing instead of
                // overwriting it (that used to delete chests, furnaces, even the anchor).
                if (!isReplaceable(level.getBlockState(pos))) continue;
                boolean isWeakPoint = height == 0;
                BlockState toPlace = isWeakPoint
                        ? ModBlocks.LIANA_WEAK_POINT.get().defaultBlockState()
                        : ModBlocks.LIANA_BARRIER.get().defaultBlockState();
                level.setBlock(pos, toPlace, 3);
                if (isWeakPoint) {
                    weakPoints.add(pos.immutable());
                } else {
                    solid.add(pos.immutable());
                }
            }
        }

        BarrierRecord barrier = BarrierRecord.create(outbreakId, solid, weakPoints);
        OutbreakSavedData.get(level).registerBarrier(barrier);
        level.playSound(null, origin, SoundEvents.VINE_PLACE, SoundSource.BLOCKS, 0.8f, 0.6f);
        return true;
    }

    private static boolean isReplaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.is(ModBlocks.INFECTED_SOIL.get());
    }
}
