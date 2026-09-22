package com.lotusblight.escape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * "он в лаборатории структуре находится... бежит до выхода... не может сломать структуру" — a
 * sealed lab corridor built fresh for one chase, torn back down when it resolves. Straight
 * entrance corridor -> one junction with two doorways (one real, one a dead-end decoy, per "свет
 * жёлтый подсказывает красный мешает и показывает неправильные выходы") -> exit room. Same
 * build/protect/teardown shape as com.lotusblight.boss.TraitorBossArena, kept as a separate class
 * since the footprint (a linear corridor, not a sealed box) and the decoy-junction logic don't fit
 * that class's shape.
 */
public final class LotusChaseStructure {
    private static final int CORRIDOR_LENGTH = 12;
    private static final int BRANCH_LENGTH = 8;
    private static final int DEAD_END_LENGTH = 5;
    private static final int WIDTH = 3; // interior width, odd so there's a center line
    private static final int HEIGHT = 4;

    private static final Map<GlobalPos, LotusChaseStructure> PROTECTED = new HashMap<>();

    private final ServerLevel level;
    private final BlockPos entrance;
    private final Direction facing;
    private final boolean correctIsLeft;
    private final Map<BlockPos, BlockState> snapshot = new HashMap<>();
    private BlockPos exitTrigger;

    public LotusChaseStructure(ServerLevel level, BlockPos entrance, Direction facing, RandomSource random) {
        this.level = level;
        this.entrance = entrance;
        this.facing = facing;
        this.correctIsLeft = random.nextBoolean();
    }

    /** Where the player should be placed/teleported to start the run. */
    public BlockPos startPos() {
        return entrance;
    }

    public void build() {
        BlockPos junction = carveCorridor(entrance, facing, CORRIDOR_LENGTH);

        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();
        Direction correctDir = correctIsLeft ? left : right;
        Direction decoyDir = correctIsLeft ? right : left;

        BlockPos afterCorrect = carveCorridor(junction, correctDir, BRANCH_LENGTH);
        markDoorway(junction, correctDir, Blocks.YELLOW_STAINED_GLASS.defaultBlockState());
        exitTrigger = carveExitRoom(afterCorrect, correctDir);

        carveCorridor(junction, decoyDir, DEAD_END_LENGTH);
        markDoorway(junction, decoyDir, Blocks.RED_STAINED_GLASS.defaultBlockState());
        // Dead end: the decoy branch is just sealed off at its far end by carveCorridor's own
        // wall - no exit room, so a player who commits to it has to turn back and lose time.
    }

    public BlockPos exitTrigger() {
        return exitTrigger;
    }

    /** Carves a straight WIDTHxHEIGHT tunnel `length` blocks in `dir` from `from`, returns the position just past its far end (the next junction/room's entry point). */
    private BlockPos carveCorridor(BlockPos from, Direction dir, int length) {
        int half = WIDTH / 2;
        Direction side = dir.getClockWise();
        for (int i = 1; i <= length; i++) {
            BlockPos center = from.relative(dir, i);
            for (int w = -half; w <= half; w++) {
                BlockPos rowCenter = center.relative(side, w);
                for (int h = 0; h < HEIGHT; h++) {
                    BlockPos pos = rowCenter.above(h);
                    boolean shell = w == -half || w == half || h == 0 || h == HEIGHT - 1;
                    place(pos, shell ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // Cap the far end so a decoy branch actually reads as a dead end and the real branch's
        // cap gets overwritten by carveExitRoom right after this returns.
        BlockPos far = from.relative(dir, length + 1);
        for (int w = -half; w <= half; w++) {
            BlockPos rowCenter = far.relative(side, w);
            for (int h = 0; h < HEIGHT; h++) {
                place(rowCenter.above(h), Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
        return from.relative(dir, length);
    }

    private void markDoorway(BlockPos junction, Direction dir, BlockState glass) {
        BlockPos doorway = junction.relative(dir, 1);
        place(doorway.above(HEIGHT), Blocks.GLOWSTONE.defaultBlockState());
        place(doorway.above(HEIGHT - 1), glass);
    }

    /** A small room past the correct branch's end, with a floor marker the player has to actually walk onto to win. */
    private BlockPos carveExitRoom(BlockPos afterBranch, Direction dir) {
        int half = WIDTH;
        Direction side = dir.getClockWise();
        BlockPos roomCenter = afterBranch.relative(dir, half + 1);
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos base = roomCenter.relative(dir, dz).relative(side, dx);
                for (int h = 0; h < HEIGHT + 1; h++) {
                    boolean shell = dx == -half || dx == half || dz == -half || dz == half || h == 0 || h == HEIGHT;
                    place(base.above(h), shell ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos trigger = roomCenter.relative(dir, half);
        place(trigger, Blocks.SEA_LANTERN.defaultBlockState());
        return trigger.above();
    }

    /** Temporary "fallen shelf" obstacle - cobweb, not a solid block, so it hinders (slows) instead of fully blocking a corridor that's otherwise unbreakable. */
    public void dropObstacle(BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) return;
        place(pos, Blocks.COBWEB.defaultBlockState());
    }

    public void teardown() {
        for (Map.Entry<BlockPos, BlockState> entry : snapshot.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
            PROTECTED.remove(GlobalPos.of(level.dimension(), entry.getKey()));
        }
        snapshot.clear();
    }

    private void place(BlockPos pos, BlockState state) {
        snapshot.computeIfAbsent(pos, level::getBlockState);
        level.setBlock(pos, state, 3);
        // Cobweb is deliberately NOT protected - it's meant to be pushed/broken through, unlike the
        // corridor shell itself.
        if (!state.is(Blocks.COBWEB)) {
            PROTECTED.put(GlobalPos.of(level.dimension(), pos), this);
        }
    }

    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        return PROTECTED.containsKey(GlobalPos.of(level.dimension(), pos));
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (isProtected(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        event.getAffectedBlocks().removeIf(pos -> isProtected(level, pos));
    }

    @SubscribeEvent
    public static void onFillBucket(FillBucketEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getTarget() instanceof net.minecraft.world.phys.BlockHitResult hit)) return;
        if (isProtected(level, hit.getBlockPos())) {
            event.setCanceled(true);
        }
    }
}
