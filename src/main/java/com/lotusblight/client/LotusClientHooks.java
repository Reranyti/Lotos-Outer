package com.lotusblight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;

public final class LotusClientHooks {
    private LotusClientHooks() {
    }

    public static void openDialogue(BlockHitResult hit) {
        Minecraft.getInstance().setScreen(new LotusDialogueScreen(0, hit));
    }
}
