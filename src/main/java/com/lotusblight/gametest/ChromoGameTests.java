package com.lotusblight.gametest;

import com.lotusblight.LotusBlight;
import com.lotusblight.world.ChromoDifficulty;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The Chromo difficulty: the world is switched over, held on Hard whatever is set, and its hostile mobs are hardened. Only for ./gradlew runGameTestServer. */
@GameTestHolder(LotusBlight.MODID)
@PrefixGameTestTemplate(false)
public final class ChromoGameTests {
    private ChromoGameTests() {}

    @GameTest(template = "honcho_arena", timeoutTicks = 200, batch = "zz_chromo")
    public static void chromoHoldsAndHardens(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        // The flag is saved with the world and has no way back, so a test world that has been through this test before is already on Chromo.
        boolean was = ChromoDifficulty.isActive(server);
        server.setDifficulty(Difficulty.EASY, true);
        if (!was && !ChromoDifficulty.enable(server)) helper.fail("enable() said it was already on");
        if (!ChromoDifficulty.isActive(server)) helper.fail("not active after enable()");
        if (!was && server.getWorldData().getDifficulty() != Difficulty.HARD) helper.fail("the world is not on Hard underneath");
        if (ChromoDifficulty.enable(server)) helper.fail("enable() twice switched it twice");

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
        double plain = new Zombie(EntityType.ZOMBIE, helper.getLevel()).getAttributeValue(Attributes.ATTACK_DAMAGE);
        double now = zombie.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (now < plain * 1.9) helper.fail("a zombie of the Chromo world hits for " + now + ", a plain one for " + plain);

        // Whatever is typed afterwards, it goes back to Hard.
        server.setDifficulty(Difficulty.PEACEFUL, true);
        helper.runAfterDelay(30, () -> {
            if (server.getWorldData().getDifficulty() != Difficulty.HARD) helper.fail("the difficulty stayed on " + server.getWorldData().getDifficulty());
            helper.succeed();
        });
    }
}
