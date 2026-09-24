package com.lotusblight.client.gecko;

import com.lotusblight.LotusBlight;
import com.lotusblight.entity.HonchoEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * "КАКОЙ НАХУЙ ЗОМБИ" - Honcho used to borrow ZombieRenderer's vanilla HumanoidModel wholesale for
 * its ready-made skeleton. This is his own geometry instead (same standard humanoid proportions,
 * hand-authored to match honcho.png's existing 64x64 UV layout exactly - see honcho.geo.json - so
 * the texture didn't need to change), resolved via GeckoLib's DefaultedEntityGeoModel convention
 * to assets/lotusblight/{geo,animations,textures}/entity/honcho.{geo,animation,png}.json.
 */
public class HonchoModel extends DefaultedEntityGeoModel<HonchoEntity> {
    public HonchoModel() {
        super(new ResourceLocation(LotusBlight.MODID, "honcho"));
    }
}
