package com.lotusblight.mixin.minecraft;

import com.lotusblight.client.ChromoClient;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The "Game" tab of the world-creation screen: its difficulty selector lists Chromo as a fifth entry, and shows it as the
 * chosen one while the screen holds the note that the world is to be switched over (see {@link WorldCreationUiStateChromoMixin}).
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab", remap = false)
public abstract class GameTabChromoMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Difficulty;values()[Lnet/minecraft/world/Difficulty;"), require = 0)
    private Difficulty[] lotusblight$values() {
        try {
            return ChromoClient.selectorValues();
        } catch (Throwable t) {
            return Difficulty.values();
        }
    }

    // The selector is brought up to date with the screen's state by a listener (a lambda of the constructor), not in the
    // constructor itself. The call it makes is listed under both names (development and production); only one matches.
    @Redirect(method = {"lambda$new$5(Lnet/minecraft/client/gui/components/CycleButton;Lnet/minecraft/client/gui/screens/worldselection/WorldCreationUiState;)V", "m_279851_(Lnet/minecraft/client/gui/components/CycleButton;Lnet/minecraft/client/gui/screens/worldselection/WorldCreationUiState;)V"},
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/worldselection/WorldCreationUiState;getDifficulty()Lnet/minecraft/world/Difficulty;"), require = 0)
    private Difficulty lotusblight$initial(WorldCreationUiState state) {
        return shown(state);
    }

    @Redirect(method = {"lambda$new$5(Lnet/minecraft/client/gui/components/CycleButton;Lnet/minecraft/client/gui/screens/worldselection/WorldCreationUiState;)V", "m_279851_(Lnet/minecraft/client/gui/components/CycleButton;Lnet/minecraft/client/gui/screens/worldselection/WorldCreationUiState;)V"},
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/worldselection/WorldCreationUiState;m_267816_()Lnet/minecraft/world/Difficulty;"), require = 0)
    private Difficulty lotusblight$initialProduction(WorldCreationUiState state) {
        return shown(state);
    }

    private static Difficulty shown(WorldCreationUiState state) {
        try {
            if (ChromoClient.isPendingNewWorld() && ChromoClient.chromo() != null) return ChromoClient.chromo();
        } catch (Throwable ignored) {
            // the stored difficulty it is
        }
        return state.getDifficulty();
    }
}
