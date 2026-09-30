package com.lotusblight.gametest;

import com.lotusblight.LotusBlight;
import com.lotusblight.escape.LotusChaseStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** The chase lab, built for real in the test world: the way from the door to the safe room has to be open, on both sides of the T. Only for ./gradlew runGameTestServer. */
@GameTestHolder(LotusBlight.MODID)
@PrefixGameTestTemplate(false)
public final class LabGameTests {
    private LabGameTests() {}

    @GameTest(template = "honcho_arena", timeoutTicks = 400, batch = "lab")
    public static void labWalkableLeft(GameTestHelper helper) {
        check(helper, true);
    }

    @GameTest(template = "honcho_arena", timeoutTicks = 400, batch = "lab")
    public static void labWalkableRight(GameTestHelper helper) {
        check(helper, false);
    }

    private static void check(GameTestHelper helper, boolean correctIsLeft) {
        ServerLevel level = helper.getLevel();
        BlockPos entrance = helper.absolutePos(new BlockPos(0, 1, 0));
        for (int x = entrance.getX() - 32; x <= entrance.getX() + 128; x += 16) {
            for (int z = entrance.getZ() - 160; z <= entrance.getZ() + 160; z += 16) level.getChunk(x >> 4, z >> 4);
        }
        LotusChaseStructure lab = new LotusChaseStructure(level, entrance, Direction.EAST, correctIsLeft);
        if (!lab.isFootprintLoaded()) helper.fail("footprint not loaded");
        lab.build();

        // sealed: from the plaza the door holds
        BlockPos outside = entrance.relative(Direction.WEST, 2).above(1);
        if (reach(level, entrance, outside, lab, entrance.getX() - 14) != null) helper.fail("the sealed door let the runner through");
        lab.unsealEntrance();
        Integer cells = reach(level, entrance, outside, lab, entrance.getX() - 14);
        if (cells == null) helper.fail("no way from the door to the safe room (correctIsLeft=" + correctIsLeft + ")");
        helper.succeed();
    }

    /** Flood fill through cells with no collision, inside the lab's own box; returns how many it reached, or null if the exit was not among them. */
    private static Integer reach(ServerLevel level, BlockPos entrance, BlockPos start, LotusChaseStructure lab, int minX) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        boolean exit = false;
        while (!queue.isEmpty() && seen.size() < 400_000) {
            BlockPos p = queue.poll();
            if (lab.isAtExitTrigger(p)) exit = true;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.getX() < minX || n.getY() < entrance.getY() + 1 || n.getY() > entrance.getY() + 8) continue;
                if (seen.contains(n)) continue;
                if (!level.getBlockState(n).getCollisionShape(level, n).isEmpty()) continue;
                seen.add(n);
                queue.add(n);
            }
        }
        return exit ? seen.size() : null;
    }
}
