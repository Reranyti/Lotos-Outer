package com.lotusblight.gametest;

import com.lotusblight.LotusBlight;
import com.lotusblight.entity.ScreechEntity;
import com.lotusblight.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Screech can be spawned (its attributes are registered) and stands, not covering its face, while nobody looks at it. Only for ./gradlew runGameTestServer. */
@GameTestHolder(LotusBlight.MODID)
@PrefixGameTestTemplate(false)
public final class ScreechGameTests {
    private ScreechGameTests() {}

    @GameTest(template = "honcho_arena", timeoutTicks = 100, batch = "screech")
    public static void screechSpawns(GameTestHelper helper) {
        ScreechEntity screech = helper.spawn(ModEntities.SCREECH.get(), 4, 2, 4);
        helper.runAfterDelay(20, () -> {
            if (!screech.isAlive()) helper.fail("it did not stay alive");
            if (screech.isCovering()) helper.fail("it covers its face with nobody looking");
            helper.succeed();
        });
    }
}
