package com.lotusblight.mixin.minecraft;

import com.lotusblight.client.ChromoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The difficulty selector of the options of a world being played gets a fifth entry, Chromo (see {@link ChromoClient}).
 * Both names of the method are listed (development and production) and the injection is not required, so a game where it
 * moved only loses the entry.
 */
@Mixin(value = OptionsScreen.class, remap = false)
public abstract class OptionsScreenChromoMixin {
    @Inject(method = {
            "createDifficultyButton(IILjava/lang/String;Lnet/minecraft/client/Minecraft;)Lnet/minecraft/client/gui/components/CycleButton;",
            "m_260961_(IILjava/lang/String;Lnet/minecraft/client/Minecraft;)Lnet/minecraft/client/gui/components/CycleButton;"
    }, at = @At("RETURN"), cancellable = true, require = 0)
    private static void lotusblight$chromo(int x, int y, String key, Minecraft minecraft, CallbackInfoReturnable<CycleButton<Difficulty>> cir) {
        try {
            if (ChromoClient.chromo() != null) cir.setReturnValue(ChromoClient.optionsButton(x, y, key, minecraft));
        } catch (Throwable ignored) {
            // whatever goes wrong, the selector stays as Minecraft built it
        }
    }
}
