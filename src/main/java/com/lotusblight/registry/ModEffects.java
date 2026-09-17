package com.lotusblight.registry;

import com.lotusblight.LotusBlight;
import com.lotusblight.effect.LotusSporeEffect;
import com.lotusblight.effect.ColdBlightEffect;
import com.lotusblight.effect.LotoniriyaEffect;
import com.lotusblight.effect.TrueLightEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, LotusBlight.MODID);
    public static final RegistryObject<MobEffect> LOTUS_SPORES = EFFECTS.register("lotus_spores", LotusSporeEffect::new);
    public static final RegistryObject<MobEffect> COLD_BLIGHT = EFFECTS.register("cold_blight", ColdBlightEffect::new);
    public static final RegistryObject<MobEffect> LOTONIRIYA = EFFECTS.register("lotoniriya", LotoniriyaEffect::new);
    public static final RegistryObject<MobEffect> TRUE_LIGHT = EFFECTS.register("true_light", TrueLightEffect::new);
    private ModEffects() {}
}
