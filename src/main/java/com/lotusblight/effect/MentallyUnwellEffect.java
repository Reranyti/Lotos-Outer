package com.lotusblight.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Психически не здоров." - what the player carries after the scientist's shot in the stairwell. It does nothing by itself on the
 * server; the whole of it is what the player sees, drawn by the client's MentalHorrorOverlay: the picture gone half-grey, and walls
 * of meat closing in from the right and the left.
 */
public class MentallyUnwellEffect extends MobEffect {
    public MentallyUnwellEffect() {
        super(MobEffectCategory.HARMFUL, 0x6E6E74);
    }
}
