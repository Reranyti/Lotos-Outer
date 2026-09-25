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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "он не может попасть туда даже если найдёт его до 15 процентов" / "не может пойти назад после" —
 * a PERSISTENT lab, built once at a fixed spot and never torn down. What changes between states is
 * just the entrance door: sealed solid before the world crosses the infection threshold (and again
 * for the duration of anyone's actual run, so nobody can enter mid-attempt or the runner retreat),
 * open the rest of the time. Straight entrance corridor -> a start-trigger a few blocks in -> one
 * junction with two doorways (one real, one a dead-end decoy, per "свет жёлтый подсказывает
 * красный мешает и показывает неправильные выходы") -> exit room.
 */
public final class LotusChaseStructure {
    private static final int START_TRIGGER_DISTANCE = 3;
    // Was 12/8/5 - a ~20-block straight-line path from entrance to exit, covered in 4-5 seconds at
    // a sprint. The chase's own DURATION_TICKS gives a player 110 seconds against a full 2:14
    // theme track ("где тут вообще место для побега?") - there was no actual room to run, just a
    // closet-sized box. Long enough now that even sprinting flat out without a single obstacle or
    // wrong turn takes a meaningful chunk of the real time budget, so obstacles/the decoy branch
    // matter instead of being irrelevant next to a corridor you could clear in a few strides.
    private static final int CORRIDOR_LENGTH = 80;
    private static final int BRANCH_LENGTH = 60;
    private static final int DEAD_END_LENGTH = 35;
    private static final int WIDTH = 3; // interior width, odd so there's a center line
    private static final int HEIGHT = 4;

    private static final Map<GlobalPos, LotusChaseStructure> PROTECTED = new HashMap<>();

    private final ServerLevel level;
    private final BlockPos entrance;
    private final Direction facing;
    private final boolean correctIsLeft;
    private final List<BlockPos> doorCells = new ArrayList<>();
    private BlockPos startTrigger;
    private BlockPos exitTrigger;

    public LotusChaseStructure(ServerLevel level, BlockPos entrance, Direction facing, RandomSource random) {
        this(level, entrance, facing, random.nextBoolean());
    }

    /** Deterministic form - used to reconstruct the same structure (same correct/decoy side) from LotusLabSavedData after a server restart. */
    public LotusChaseStructure(ServerLevel level, BlockPos entrance, Direction facing, boolean correctIsLeft) {
        this.level = level;
        this.entrance = entrance;
        this.facing = facing;
        this.correctIsLeft = correctIsLeft;
    }

    public BlockPos entrance() {
        return entrance;
    }

    public boolean correctIsLeft() {
        return correctIsLeft;
    }

    public BlockPos startTrigger() {
        return startTrigger;
    }

    public BlockPos exitTrigger() {
        return exitTrigger;
    }

    /**
     * Exact BlockPos equality against a single center column never reliably fires for how a player
     * actually walks - the corridor is WIDTH blocks wide, and nothing forces anyone to hug dead
     * center. Accepts any position within the trigger's own footprint (all WIDTH columns, and a
     * couple of Y levels of headroom) instead of one exact block.
     */
    public boolean isAtStartTrigger(BlockPos playerPos) {
        return isAtTrigger(playerPos, startTrigger);
    }

    public boolean isAtExitTrigger(BlockPos playerPos) {
        return isAtTrigger(playerPos, exitTrigger);
    }

    private boolean isAtTrigger(BlockPos playerPos, BlockPos triggerCenter) {
        int half = WIDTH / 2;
        Direction side = facing.getClockWise();
        int dx = playerPos.getX() - triggerCenter.getX();
        int dz = playerPos.getZ() - triggerCenter.getZ();
        int alongFacing = dx * facing.getStepX() + dz * facing.getStepZ();
        int alongSide = dx * side.getStepX() + dz * side.getStepZ();
        int dy = playerPos.getY() - triggerCenter.getY();
        return Math.abs(alongFacing) <= 1 && Math.abs(alongSide) <= half && dy >= 0 && dy <= 1;
    }

    /**
     * place() quietly skips unloaded chunks, so building while part of the footprint is unloaded left
     * a lab with holes - possibly no exit room at all, making the run unwinnable - while the lab was
     * already recorded as built. Samples every chunk the corridors pass through.
     */
    public boolean isFootprintLoaded() {
        BlockPos junction = entrance.relative(facing, CORRIDOR_LENGTH);
        int branchReach = BRANCH_LENGTH + WIDTH * 2 + 2;
        return isLineLoaded(entrance, facing, CORRIDOR_LENGTH + 1)
                && isLineLoaded(junction, facing.getCounterClockWise(), Math.max(branchReach, DEAD_END_LENGTH + 1))
                && isLineLoaded(junction, facing.getClockWise(), Math.max(branchReach, DEAD_END_LENGTH + 1));
    }

    private boolean isLineLoaded(BlockPos from, Direction dir, int length) {
        for (int i = 0; i <= length; i += 8) {
            if (!level.hasChunkAt(from.relative(dir, i))) return false;
        }
        return level.hasChunkAt(from.relative(dir, length));
    }

    /** Carves the whole permanent structure once. Starts sealed - call unsealEntrance() separately once the world has actually crossed the threshold. */
    public void build() {
        int half = WIDTH / 2;
        Direction side = facing.getClockWise();
        BlockPos doorSlice = entrance.relative(facing, 1);
        for (int w = -half; w <= half; w++) {
            doorCells.add(doorSlice.relative(side, w).above(1));
            doorCells.add(doorSlice.relative(side, w).above(2));
        }

        BlockPos junction = carveCorridor(entrance, facing, CORRIDOR_LENGTH);
        // Missing .above() here meant this pointed at the corridor's FLOOR block (h=0, solid shell)
        // instead of the walkable space a standing player's own blockPosition() actually reports
        // (floor + 1). The exact-position check in LotusChaseEvent#checkForNewRunner could then
        // never match under normal walking - no cutscene, no music, no run ever started
        // ("побега вообще нет"). exitTrigger already did this correctly (see carveExitRoom's own
        // `return trigger.above();`) - this was the one asymmetric spot that didn't.
        startTrigger = entrance.relative(facing, START_TRIGGER_DISTANCE).above();

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

        sealEntrance();
    }

    public void sealEntrance() {
        for (BlockPos pos : doorCells) {
            if (!level.hasChunkAt(pos)) continue;
            level.setBlock(pos, Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
        }
    }

    public void unsealEntrance() {
        for (BlockPos pos : doorCells) {
            if (!level.hasChunkAt(pos)) continue;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /**
     * Clears any obstacle debris left in the corridor from a previous attempt - the structure
     * itself never gets rebuilt. Used to sweep all three directions (entrance corridor + both
     * branches) from `entrance` using `facing`'s own side axis - correct for the entrance corridor
     * only. The two branches actually start at `junction` (entrance.relative(facing,
     * CORRIDOR_LENGTH)), not entrance itself, and each branch's real width axis is ITS OWN
     * direction's clockwise (see carveCorridor's own `side = dir.getClockWise()`), not facing's.
     * With the old origin+axis, this never touched a single block inside either real branch -
     * cobwebs there just accumulated across every run instead of clearing between attempts.
     */
    public void resetForNextRun() {
        BlockPos junction = entrance.relative(facing, CORRIDOR_LENGTH);
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();
        clearCobwebs(entrance, facing, CORRIDOR_LENGTH);
        clearCobwebs(junction, left, Math.max(BRANCH_LENGTH, DEAD_END_LENGTH));
        clearCobwebs(junction, right, Math.max(BRANCH_LENGTH, DEAD_END_LENGTH));
    }

    // Obstacles are the only thing dropObstacle() ever places that isn't part of the fixed shell,
    // and cobweb is the only block type it uses - safe to blanket-clear by type without tracking
    // individual obstacle positions.
    private void clearCobwebs(BlockPos origin, Direction dir, int length) {
        int half = WIDTH / 2;
        Direction side = dir.getClockWise();
        for (int i = 1; i <= length; i++) {
            BlockPos center = origin.relative(dir, i);
            for (int w = -half; w <= half; w++) {
                for (int h = 1; h < HEIGHT - 1; h++) {
                    BlockPos pos = center.relative(side, w).above(h);
                    if (!level.hasChunkAt(pos)) continue;
                    if (level.getBlockState(pos).is(Blocks.COBWEB)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
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
        if (!level.hasChunkAt(pos)) return;
        if (!level.getBlockState(pos).isAir()) return;
        level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
        // Deliberately NOT added to PROTECTED - cobweb is meant to be pushed/broken through, unlike
        // the corridor shell itself.
    }

    /** Skips silently on an unloaded chunk - "он не может попасть туда даже если найдёт его до 15 процентов" only needs this lab to exist somewhere players actually reach; forcing a synchronous chunk load from a setBlock call here would deadlock the server tick that's driving that very load (see LotusChaseEvent#ensureLabExists's own hasChunkAt gate). */
    private void place(BlockPos pos, BlockState state) {
        if (!level.hasChunkAt(pos)) return;
        level.setBlock(pos, state, 3);
        PROTECTED.put(GlobalPos.of(level.dimension(), pos), this);
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
