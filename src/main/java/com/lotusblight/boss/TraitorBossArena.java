package com.lotusblight.boss;

import com.lotusblight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * A sealed 9x9 stone-brick box built fresh for one traitor-boss fight and torn back down to
 * whatever was there before once it ends. Never persisted (see TraitorBossFight's own "no
 * SavedData" constraint) - the static {@link #PROTECTED} registry only has to survive as long as
 * the server process does, so a server restart mid-fight simply drops the protection along with
 * everything else, per the accepted limitation in the design notes.
 */
public final class TraitorBossArena {

    private static final int FOOTPRINT_HALF = 4; // 9x9 footprint: center +/- 4
    private static final int INTERIOR_HEIGHT = 8;
    private static final int LAMP_INTERVAL = 3;
    private static final int LAMP_ROW_LOW = 2;
    private static final int LAMP_ROW_HIGH = 5;
    private static final int CRACKED_BRICK_CHANCE = 4; // 1-in-4, just enough to read as "variation"
    private static final int SPAWN_SEARCH_ATTEMPTS = 40;

    // GlobalPos, not bare BlockPos - mirrors LotusEvents' own dimension-safe bookkeeping, since two
    // different dimensions can share the same BlockPos.
    private static final Map<GlobalPos, TraitorBossArena> PROTECTED = new HashMap<>();

    private final ServerLevel level;
    private final BlockPos center;
    // Original state of every position this arena has ever touched, captured once on first touch -
    // teardown() replays this exactly, including positions that were air before build().
    private final Map<BlockPos, BlockState> snapshot = new HashMap<>();
    private BlockPos lotusPos;

    public TraitorBossArena(ServerLevel level, BlockPos center) {
        this.level = level;
        this.center = center;
    }

    public void build() {
        int minX = center.getX() - FOOTPRINT_HALF;
        int maxX = center.getX() + FOOTPRINT_HALF;
        int minZ = center.getZ() - FOOTPRINT_HALF;
        int maxZ = center.getZ() + FOOTPRINT_HALF;
        int baseY = center.getY();
        int topY = baseY + INTERIOR_HEIGHT + 1;

        for (int x = minX; x <= maxX; x++) {
            boolean xWall = x == minX || x == maxX;
            for (int z = minZ; z <= maxZ; z++) {
                boolean zWall = z == minZ || z == maxZ;
                for (int y = baseY; y <= topY; y++) {
                    boolean shell = xWall || zWall || y == baseY || y == topY;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!shell) {
                        place(pos, Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    if ((xWall || zWall) && isLampRow(y, baseY) && isLampSpacing(x, z, minX, minZ, xWall)) {
                        place(pos, Blocks.CRYING_OBSIDIAN.defaultBlockState());
                    } else {
                        BlockState brick = level.random.nextInt(CRACKED_BRICK_CHANCE) == 0
                                ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                                : Blocks.STONE_BRICKS.defaultBlockState();
                        place(pos, brick);
                    }
                }
            }
        }

        // Overwrites the floor tiles just placed above - place() only snapshots a position's
        // ORIGINAL state once, so re-placing here is safe and still restores correctly.
        int cx = center.getX();
        int cz = center.getZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                place(new BlockPos(cx + dx, baseY, cz + dz), Blocks.WATER.defaultBlockState());
            }
        }
        // Same "flower rests on the block above its water" shape LotusEvents already uses for the
        // vanilla lotus anchor.
        lotusPos = new BlockPos(cx, baseY + 1, cz);
        place(lotusPos, ModBlocks.LOTUS_HEART.get().defaultBlockState());
    }

    /**
     * Used to setBlock unconditionally for every snapshotted position - teardown() can fire well
     * after build() (victory/abort can happen once the player has died, disconnected, or wandered
     * off), so unlike build() (which runs right where a present player is standing, effectively
     * always loaded), these chunks are not guaranteed to still be loaded. An unguarded setBlock on
     * one that isn't would force a synchronous chunk load on the server thread - the same deadlock
     * class already fixed in LotusChaseStructure (see its own javadoc for the real freeze it caused).
     */
    public void teardown() {
        for (Map.Entry<BlockPos, BlockState> entry : snapshot.entrySet()) {
            if (level.hasChunkAt(entry.getKey())) {
                level.setBlock(entry.getKey(), entry.getValue(), 3);
            }
            PROTECTED.remove(GlobalPos.of(level.dimension(), entry.getKey()));
        }
        snapshot.clear();
    }

    /** Position of the central ModBlocks.LOTUS_HEART block - the buff-beam's origin point. */
    public BlockPos lotusPos() {
        return lotusPos;
    }

    /** A random valid floor-level position inside the sealed footprint, avoiding the center water/lotus zone and the walls/lamps. Called once per mob spawn. */
    public BlockPos randomSpawnPos(RandomSource random) {
        int minX = center.getX() - FOOTPRINT_HALF + 1;
        int maxX = center.getX() + FOOTPRINT_HALF - 1;
        int minZ = center.getZ() - FOOTPRINT_HALF + 1;
        int maxZ = center.getZ() + FOOTPRINT_HALF - 1;
        int floorY = center.getY() + 1;

        for (int i = 0; i < SPAWN_SEARCH_ATTEMPTS; i++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);
            if (Math.abs(x - center.getX()) <= 1 && Math.abs(z - center.getZ()) <= 1) continue;
            return new BlockPos(x, floorY, z);
        }
        // The 7x7-minus-center-3x3 interior always has valid cells well within the attempt budget
        // above - this is a deterministic fallback only, never expected to actually run.
        return new BlockPos(minX, floorY, minZ);
    }

    private void place(BlockPos pos, BlockState state) {
        if (!level.hasChunkAt(pos)) return;
        snapshot.computeIfAbsent(pos, level::getBlockState);
        level.setBlock(pos, state, 3);
        PROTECTED.put(GlobalPos.of(level.dimension(), pos), this);
    }

    private static boolean isLampRow(int y, int baseY) {
        return y - baseY == LAMP_ROW_LOW || y - baseY == LAMP_ROW_HIGH;
    }

    private static boolean isLampSpacing(int x, int z, int minX, int minZ, boolean xWall) {
        return xWall ? (z - minZ) % LAMP_INTERVAL == 0 : (x - minX) % LAMP_INTERVAL == 0;
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

    /**
     * Excises only the protected positions from the explosion's own effect list instead of
     * cancelling the whole event - mirrors BarrierEvents.onExplosion's own getAffectedBlocks()
     * shape, just removing rather than reacting to each entry.
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        event.getAffectedBlocks().removeIf(pos -> isProtected(level, pos));
    }

    @SubscribeEvent
    public static void onFillBucket(FillBucketEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getTarget() instanceof BlockHitResult hit)) return;
        if (isProtected(level, hit.getBlockPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getState().is(Blocks.SNOW)) return; // the snow LAYER block, not the solid snow block
        if (isProtected(level, event.getPos())) {
            event.setCanceled(true);
        }
    }
}
