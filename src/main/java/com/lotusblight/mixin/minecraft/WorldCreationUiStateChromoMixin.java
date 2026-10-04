package com.lotusblight.mixin.minecraft;

import com.lotusblight.client.ChromoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What the world-creation screen does with the difficulty it is given. Chromo is never stored - a new world is made on
 * Hard, with a note that it is to be switched over to Chromo once it exists - and picking it first shows the warning.
 * Any other pick takes the note back.
 */
@Mixin(value = WorldCreationUiState.class, remap = false)
public abstract class WorldCreationUiStateChromoMixin {
    @Inject(method = {
            "setDifficulty(Lnet/minecraft/world/Difficulty;)V",
            "m_267754_(Lnet/minecraft/world/Difficulty;)V"
    }, at = @At("HEAD"), cancellable = true, require = 0)
    private void lotusblight$chromo(Difficulty difficulty, CallbackInfo ci) {
        try {
            if (ChromoClient.isChromo(difficulty)) {
                ci.cancel();
                Minecraft mc = Minecraft.getInstance();
                Screen here = mc.screen;
                WorldCreationUiState self = (WorldCreationUiState) (Object) this;
                ChromoClient.warn(here, accepted -> {
                    self.setDifficulty(Difficulty.HARD);               // (this clears the note)
                    ChromoClient.setPendingNewWorld(accepted);
                });
            } else if (ChromoClient.isPendingNewWorld()) {
                ChromoClient.setPendingNewWorld(false);
            }
        } catch (Throwable ignored) {
            // the screen goes on as Minecraft has it
        }
    }
}
