package com.lotusblight.gametest;

import com.lotusblight.LotusBlight;
import com.lotusblight.entity.CreakEntity;
import com.lotusblight.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Creak can be spawned (its attributes are registered) and stands, not covering its face, while nobody looks at it. Only for ./gradlew runGameTestServer. */
@GameTestHolder(LotusBlight.MODID)
@PrefixGameTestTemplate(false)
public final class CreakGameTests {
    private CreakGameTests() {}

    @GameTest(template = "honcho_arena", timeoutTicks = 100, batch = "creak")
    public static void creakSpawns(GameTestHelper helper) {
        CreakEntity creak = helper.spawn(ModEntities.CREAK.get(), 4, 2, 4);
        helper.runAfterDelay(20, () -> {
            if (!creak.isAlive()) helper.fail("it did not stay alive");
            if (creak.isCovering()) helper.fail("it covers its face with nobody looking");
            helper.succeed();
        });
    }
}
