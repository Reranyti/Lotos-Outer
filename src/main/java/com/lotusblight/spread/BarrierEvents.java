package com.lotusblight.spread;

import com.lotusblight.data.BarrierRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

/** Clears a whole vine barrier once every one of its weak points has been cut. */
public class BarrierEvents {

    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.55f), 1.4f);

    @SubscribeEvent
    public void onWeakPointBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getState().is(ModBlocks.LIANA_WEAK_POINT.get())) return;
        onWeakPointGone(level, event.getPos());
    }

    /**
     * BreakEvent never fires for explosion-destroyed blocks (Forge has its own dedicated event for
     * that). A weak point taken out by a creeper/TNT instead of mined by hand used to leave the
     * barrier's tracker permanently thinking that point was still intact - isFullyBroken() could
     * then never return true for that barrier again even after every weak point was physically
     * gone, soft-locking its remaining solid wall in place forever.
     */
    @SubscribeEvent
    public void onExplosion(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (level.getBlockState(pos).is(ModBlocks.LIANA_WEAK_POINT.get())) {
                onWeakPointGone(level, pos);
            }
        }
    }

    private void onWeakPointGone(ServerLevel level, BlockPos pos) {
        OutbreakSavedData data = OutbreakSavedData.get(level);
        BarrierRecord barrier = data.barrierAt(pos);
        if (barrier == null) return;

        barrier.breakWeakPoint(pos);
        data.markBarrierDirty();

        if (barrier.isFullyBroken()) {
            for (BlockPos solid : barrier.solidPositions()) {
                if (level.getBlockState(solid).is(ModBlocks.LIANA_BARRIER.get())) {
                    level.setBlock(solid, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            for (BlockPos weak : barrier.weakPoints()) {
                if (level.getBlockState(weak).is(ModBlocks.LIANA_WEAK_POINT.get())) {
                    level.setBlock(weak, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            data.removeBarrier(barrier.id());
            level.sendParticles(PINK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 1.5, 1.0, 1.5, 0.05);
            level.playSound(null, pos, SoundEvents.VINE_BREAK, SoundSource.BLOCKS, 1.0f, 0.5f);
        }
    }
}
