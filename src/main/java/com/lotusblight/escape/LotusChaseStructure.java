package com.lotusblight.escape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
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
import java.util.function.IntFunction;

/**
 * "он не может попасть туда даже если найдёт его до 15 процентов" / "не может пойти назад после" —
 * a PERSISTENT lab, built once at a fixed spot and never torn down. What changes between states is
 * just the entrance door: sealed before the world crosses the infection threshold (and again for the
 * duration of anyone's actual run, so nobody can enter mid-attempt or the runner retreat), open the
 * rest of the time.
 *
 * Layout: a plaza in front of the door (cleared of trees and terrain, on a solid pad), the door
 * (3x3) in a wall, a long hall, then a cross-hall (a T) whose two arms are the two ways on - the real
 * one lit yellow, the decoy (shorter, a dead end) lit red ("свет жёлтый подсказывает красный мешает и
 * показывает неправильные выходы") - and the real arm ends in a big safe room.
 *
 * Every hall is 7 wide and 7 tall inside (9x9 with the walls), lit by ceiling panels and lights in
 * the floor edge. On each run falling shelves, cobweb and glass partitions get in the runner's way
 * (see {@link #dropHazard}); they are swept away before the next one starts.
 */
public final class LotusChaseStructure {
    /** Half of the inside width: the halls are 2*HALF+1 = 7 wide. */
    private static final int HALF = 3;
    /** Inside height (floor is h=0, the inside is h=1..HEIGHT, the ceiling is h=HEIGHT+1). */
    private static final int HEIGHT = 7;
    /** The entrance hall, from the door to the T. */
    private static final int CORRIDOR_LENGTH = 76;
    /** Where the cross-hall's axis sits along the entrance hall (the hall ends in its near wall). */
    private static final int JUNCTION = CORRIDOR_LENGTH + 4;
    // These two stay at least as long as the old lab's arms (60 / 35) so a world that still holds the
    // old structure gets it completely rebuilt over instead of keeping stubs of it.
    private static final int ARM_CORRECT = 64;
    private static final int ARM_DECOY = 38;
    private static final int EXIT_ROOM_LENGTH = 12;
    private static final int EXIT_ROOM_HALF = 5;
    private static final int PLAZA_DEPTH = 14;
    private static final int PLAZA_HALF = 9;
    private static final int PLAZA_CLEAR_HEIGHT = 10;
    private static final int FOUNDATION_DEPTH = 8;
    private static final int START_SLICE = 6;

    private static final Map<GlobalPos, LotusChaseStructure> PROTECTED = new HashMap<>();

    private enum Light { WHITE, YELLOW, RED, SAFE }

    private record Box(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        boolean contains(BlockPos p) {
            return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY
                    && p.getZ() >= minZ && p.getZ() <= maxZ;
        }
    }

    private final ServerLevel level;
    private final BlockPos entrance;
    private final Direction facing;
    private final boolean correctIsLeft;
    private final List<BlockPos> doorCells = new ArrayList<>();
    private Box startBox;
    private Box exitBox;

    public LotusChaseStructure(ServerLevel level, BlockPos entrance, Direction facing, RandomSource random) {
        this(level, entrance, facing, random.nextBoolean());
    }

    /** Deterministic form - used to reconstruct the same structure (same correct/decoy side) from LotusLabSavedData after a server restart. */
    public LotusChaseStructure(ServerLevel level, BlockPos entrance, Direction facing, boolean correctIsLeft) {
        this.level = level;
        this.entrance = entrance;
        this.facing = facing;
        this.correctIsLeft = correctIsLeft;
        computeGeometry();
    }

    public BlockPos entrance() {
        return entrance;
    }

    public boolean correctIsLeft() {
        return correctIsLeft;
    }

    // ------------------------------------------------------------------ geometry

    private Direction correctDir() {
        return correctIsLeft ? facing.getCounterClockWise() : facing.getClockWise();
    }

    private BlockPos junction() {
        return entrance.relative(facing, JUNCTION);
    }

    /** The floor block at slice {@code i} along {@code dir} from {@code origin}, {@code w} to the clockwise side. */
    private static BlockPos slice(BlockPos origin, Direction dir, int i, int w) {
        return origin.relative(dir, i).relative(dir.getClockWise(), w);
    }

    private void computeGeometry() {
        int floorY = entrance.getY() + 1;
        // The start: a couple of slices of the entrance hall, a few steps in.
        startBox = box(entrance, facing, START_SLICE, START_SLICE + 1, HALF, floorY);
        // The exit: the far half of the safe room.
        int from = ARM_CORRECT + EXIT_ROOM_LENGTH / 2 + 1;
        exitBox = box(junction(), correctDir(), from, ARM_CORRECT + EXIT_ROOM_LENGTH, EXIT_ROOM_HALF, floorY);
    }

    private Box box(BlockPos origin, Direction dir, int fromI, int toI, int half, int floorY) {
        BlockPos a = slice(origin, dir, fromI, -half), b = slice(origin, dir, toI, half);
        return new Box(Math.min(a.getX(), b.getX()), Math.max(a.getX(), b.getX()), floorY, floorY + 2,
                Math.min(a.getZ(), b.getZ()), Math.max(a.getZ(), b.getZ()));
    }

    public boolean isAtStartTrigger(BlockPos playerPos) {
        return startBox.contains(playerPos);
    }

    public boolean isAtExitTrigger(BlockPos playerPos) {
        return exitBox.contains(playerPos);
    }

    /**
     * place() quietly skips unloaded chunks, so building while part of the footprint is unloaded left
     * a lab with holes - possibly no exit room at all, making the run unwinnable - while the lab was
     * already recorded as built. Samples every chunk the whole footprint (plaza, halls, both arms and
     * the safe room) touches.
     */
    public boolean isFootprintLoaded() {
        int leftLen = correctIsLeft ? ARM_CORRECT + EXIT_ROOM_LENGTH + 2 : ARM_DECOY + 2;
        int rightLen = correctIsLeft ? ARM_DECOY + 2 : ARM_CORRECT + EXIT_ROOM_LENGTH + 2;
        return rectLoaded(-PLAZA_DEPTH - 1, JUNCTION + HALF + 2, -PLAZA_HALF, PLAZA_HALF)
                && rectLoaded(JUNCTION - HALF - 2, JUNCTION + HALF + 2, -leftLen - EXIT_ROOM_HALF, rightLen + EXIT_ROOM_HALF);
    }

    /** Samples a rectangle of (along-facing, across) offsets on a grid a chunk apart. */
    private boolean rectLoaded(int f0, int f1, int s0, int s1) {
        Direction side = facing.getClockWise();
        for (int f = f0; ; f += 8) {
            int ff = Math.min(f, f1);
            for (int s = s0; ; s += 8) {
                int ss = Math.min(s, s1);
                if (!level.hasChunkAt(entrance.relative(facing, ff).relative(side, ss))) return false;
                if (ss == s1) break;
            }
            if (ff == f1) break;
        }
        return true;
    }

    // ------------------------------------------------------------------ building

    /** Carves the whole permanent structure once. Starts sealed - call unsealEntrance() separately once the world has actually crossed the threshold. */
    public void build() {
        buildPlaza();

        Direction right = facing.getClockWise();
        int leftLen = correctIsLeft ? ARM_CORRECT : ARM_DECOY;
        int rightLen = correctIsLeft ? ARM_DECOY : ARM_CORRECT;
        boolean leftIsCorrect = correctIsLeft;
        // The cross-hall first, as one tunnel; the entrance hall then opens into its near wall.
        carveTunnel(junction(), right, -leftLen, rightLen, HALF, true, true,
                i -> Math.abs(i) <= HALF + 1 ? Light.WHITE : ((i < 0) == leftIsCorrect ? Light.YELLOW : Light.RED));
        carveTunnel(entrance, facing, 1, JUNCTION - HALF - 1, HALF, false, false, i -> Light.WHITE);

        // The safe room past the real arm: bigger than the halls, brighter, a lime floor in its far half.
        carveTunnel(junction(), correctDir(), ARM_CORRECT + 1, ARM_CORRECT + EXIT_ROOM_LENGTH, EXIT_ROOM_HALF, false, true,
                i -> Light.SAFE);
        markSafeFloor();

        // The door: the first slice is a wall with a 3x3 doorway in it.
        doorCells.clear();
        for (int w = -HALF; w <= HALF; w++) {
            for (int h = 1; h <= HEIGHT; h++) {
                BlockPos pos = slice(entrance, facing, 1, w).above(h);
                if (Math.abs(w) <= 1 && h <= 3) doorCells.add(pos);
                else place(pos, Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        sealEntrance();
    }

    /** A cleared, level apron in front of the door so the way in is never buried by trees or hillside. */
    private void buildPlaza() {
        Direction side = facing.getClockWise();
        for (int f = -PLAZA_DEPTH; f <= 0; f++) {
            for (int s = -PLAZA_HALF; s <= PLAZA_HALF; s++) {
                BlockPos floor = entrance.relative(facing, f).relative(side, s);
                if (!level.hasChunkAt(floor)) continue;
                for (int h = 1; h <= PLAZA_CLEAR_HEIGHT; h++) {
                    BlockPos p = floor.above(h);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
                boolean edge = Math.abs(s) == PLAZA_HALF || f == -PLAZA_DEPTH;
                setIfDifferent(floor, (f + s) % 2 == 0 && !edge ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                foundation(floor.below());
            }
        }
    }

    /** Fills the gap (air, water, plants) below a floor block so nothing is left hanging over a ravine or a lake. */
    private void foundation(BlockPos below) {
        for (int d = 0; d < FOUNDATION_DEPTH; d++) {
            BlockPos p = below.below(d);
            if (!level.hasChunkAt(p)) return;
            BlockState state = level.getBlockState(p);
            if (!(state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty())) return;
            place(p, Blocks.STONE_BRICKS.defaultBlockState());
        }
    }

    private void setIfDifferent(BlockPos pos, BlockState state) {
        if (!level.hasChunkAt(pos)) return;
        if (level.getBlockState(pos) != state) level.setBlock(pos, state, 3);
    }

    /**
     * Carves a tunnel {@code 2*half+1} wide and {@value #HEIGHT} tall inside, slices {@code from..to} along
     * {@code dir} from {@code origin} (negative slices run the other way), with walls, a floor, a ceiling,
     * light panels and - when {@code capStart} - a wall across the start and/or the far end.
     */
    private void carveTunnel(BlockPos origin, Direction dir, int from, int to, int half, boolean capStart, boolean capEnd, IntFunction<Light> lights) {
        Direction side = dir.getClockWise();
        for (int i = from; i <= to; i++) {
            Light kind = lights.apply(i);
            BlockPos c = origin.relative(dir, i);
            for (int w = -half - 1; w <= half + 1; w++) {
                BlockPos col = c.relative(side, w);
                foundation(col.below());
                for (int h = 0; h <= HEIGHT + 1; h++) {
                    place(col.above(h), material(i, w, h, half));
                }
            }
            decorate(c, side, i, half, kind);
        }
        if (capStart) cap(origin, dir, from - 1, half);
        if (capEnd) cap(origin, dir, to + 1, half);
    }

    private void cap(BlockPos origin, Direction dir, int i, int half) {
        Direction side = dir.getClockWise();
        BlockPos c = origin.relative(dir, i);
        for (int w = -half - 1; w <= half + 1; w++) {
            for (int h = 0; h <= HEIGHT + 1; h++) {
                place(c.relative(side, w).above(h), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
    }

    private static BlockState material(int i, int w, int h, int half) {
        boolean wall = Math.abs(w) == half + 1;
        if (h == 0) {
            return (Math.floorMod(i + w, 2) == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES).defaultBlockState();
        }
        if (h == HEIGHT + 1) return Blocks.SMOOTH_STONE.defaultBlockState();
        if (!wall) return Blocks.AIR.defaultBlockState();
        if (Math.floorMod(i, 8) == 0) return Blocks.IRON_BLOCK.defaultBlockState();
        return (h <= 2 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS).defaultBlockState();
    }

    /** Ceiling panels every four slices and lights let into the floor along both walls, offset from them. */
    private void decorate(BlockPos c, Direction side, int i, int half, Light kind) {
        int step = kind == Light.SAFE ? 3 : 4;
        if (Math.floorMod(i, step) == 0) {
            int span = kind == Light.SAFE ? 3 : 1;
            for (int w = -span; w <= span; w += kind == Light.SAFE ? 2 : 1) {
                panel(c.relative(side, w).above(HEIGHT + 1), kind, true);
            }
        }
        if (Math.floorMod(i + 2, step) == 0) {
            panel(c.relative(side, -half), kind, false);
            panel(c.relative(side, half), kind, false);
        }
    }

    /** One light: a sea lantern (white / safe), an ochre froglight (yellow) or a glowstone seen through red glass. */
    private void panel(BlockPos pos, Light kind, boolean ceiling) {
        switch (kind) {
            case WHITE, SAFE -> place(pos, Blocks.SEA_LANTERN.defaultBlockState());
            case YELLOW -> place(pos, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
            case RED -> {
                place(pos, Blocks.RED_STAINED_GLASS.defaultBlockState());
                place(ceiling ? pos.above() : pos.below(), Blocks.GLOWSTONE.defaultBlockState());
            }
        }
    }

    private void markSafeFloor() {
        Direction dir = correctDir();
        Direction side = dir.getClockWise();
        BlockPos j = junction();
        for (int i = ARM_CORRECT + EXIT_ROOM_LENGTH / 2 + 1; i <= ARM_CORRECT + EXIT_ROOM_LENGTH; i++) {
            for (int w = -EXIT_ROOM_HALF; w <= EXIT_ROOM_HALF; w++) {
                BlockPos floor = j.relative(dir, i).relative(side, w);
                BlockState now = level.hasChunkAt(floor) ? level.getBlockState(floor) : null;
                if (now != null && now.is(Blocks.SEA_LANTERN)) continue;
                place(floor, Blocks.LIME_CONCRETE.defaultBlockState());
            }
        }
    }

    // ------------------------------------------------------------------ door

    public void sealEntrance() {
        for (BlockPos pos : doorCells) place(pos, Blocks.IRON_BLOCK.defaultBlockState());
    }

    public void unsealEntrance() {
        for (BlockPos pos : doorCells) {
            if (!level.hasChunkAt(pos)) continue;
            PROTECTED.remove(GlobalPos.of(level.dimension(), pos));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------ hazards

    /**
     * Things that get in the way of the runner, always on the hall they are running through and a few
     * steps ahead of them: a shelf that falls from the ceiling, a patch of cobweb, in the second phase
     * also a pane partition across the whole hall that has to be broken to get through.
     */
    public void dropHazard(ServerPlayer player, boolean phase2, RandomSource random) {
        Direction dir = player.getDirection();
        BlockPos feet = new BlockPos(player.getBlockX(), entrance.getY() + 1, player.getBlockZ());
        double roll = random.nextDouble();
        if (phase2 && roll < 0.30) {
            partition(feet.relative(dir, 7), dir);
        } else if (roll < (phase2 ? 0.75 : 0.55)) {
            shelf(feet.relative(dir, 4 + random.nextInt(4)).relative(dir.getClockWise(), random.nextInt(5) - 2), random);
            if (phase2) shelf(feet.relative(dir, 5 + random.nextInt(4)).relative(dir.getClockWise(), random.nextInt(7) - 3), random);
        } else {
            cobweb(feet.relative(dir, 4 + random.nextInt(3)), dir);
        }
    }

    private boolean insideHall(BlockPos floorAbove) {
        return level.hasChunkAt(floorAbove) && isProtected(level, floorAbove.below());
    }

    /** A shelf falls out of the ceiling onto {@code target} with a puff of dust; it lands as a block to go around or over. */
    private void shelf(BlockPos target, RandomSource random) {
        if (!insideHall(target) || !level.getBlockState(target).isAir()) return;
        BlockPos from = target.above(HEIGHT - 1);
        if (!level.getBlockState(from).isAir()) return;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                from.getX() + 0.5, from.getY() + 1.0, from.getZ() + 0.5, 24, 0.8, 0.1, 0.8, 0.05);
        level.playSound(null, from, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.4f, 0.6f + random.nextFloat() * 0.3f);
        FallingBlockEntity shelf = FallingBlockEntity.fall(level, from, Blocks.BOOKSHELF.defaultBlockState());
        shelf.dropItem = false;
        shelf.setHurtsEntities(2.0f, 40);
    }

    private void cobweb(BlockPos center, Direction dir) {
        Direction side = dir.getClockWise();
        for (int w = -1; w <= 1; w++) {
            for (int h = 0; h <= 1; h++) {
                BlockPos pos = center.relative(side, w).above(h);
                if (insideHall(pos) && level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
            }
        }
    }

    /** A wall of glass across the whole hall: nothing to go round, only through (a couple of blows). */
    private void partition(BlockPos center, Direction dir) {
        Direction side = dir.getClockWise();
        for (int w = -HALF; w <= HALF; w++) {
            for (int h = 0; h < HEIGHT; h++) {
                BlockPos pos = center.relative(side, w).above(h);
                if (insideHall(pos) && level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 3);
            }
        }
        level.playSound(null, center, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.2f, 0.7f);
    }

    /** Clears whatever a previous run left in the halls - the structure itself never gets rebuilt, and none of it is part of the shell. */
    public void resetForNextRun() {
        Direction right = facing.getClockWise();
        int leftLen = correctIsLeft ? ARM_CORRECT : ARM_DECOY;
        int rightLen = correctIsLeft ? ARM_DECOY : ARM_CORRECT;
        sweep(entrance, facing, 1, JUNCTION - HALF - 1);
        sweep(junction(), right, -leftLen, rightLen);
    }

    private void sweep(BlockPos origin, Direction dir, int from, int to) {
        Direction side = dir.getClockWise();
        for (int i = from; i <= to; i++) {
            for (int w = -HALF; w <= HALF; w++) {
                for (int h = 1; h <= HEIGHT; h++) {
                    BlockPos pos = origin.relative(dir, i).relative(side, w).above(h);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.COBWEB) || state.is(Blocks.BOOKSHELF) || state.is(Blocks.GLASS)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ protection

    /** Skips silently on an unloaded chunk - "он не может попасть туда даже если найдёт его до 15 процентов" only needs this lab to exist somewhere players actually reach; forcing a synchronous chunk load from a setBlock call here would deadlock the server tick that's driving that very load (see LotusChaseEvent#ensureLabExists's own hasChunkAt gate). */
    private void place(BlockPos pos, BlockState state) {
        if (!level.hasChunkAt(pos)) return;
        if (level.getBlockState(pos) != state) level.setBlock(pos, state, 3);
        // Only real blocks are the shell. Air inside must stay breakable-into: a pane partition or a
        // cobweb put there later is meant to be broken through.
        GlobalPos key = GlobalPos.of(level.dimension(), pos);
        if (state.isAir()) PROTECTED.remove(key);
        else PROTECTED.put(key, this);
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
