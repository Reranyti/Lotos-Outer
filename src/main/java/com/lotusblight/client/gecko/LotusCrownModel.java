package com.lotusblight.client.gecko;

import com.lotusblight.LotusBlight;
import com.lotusblight.world.LotusCrownBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

/**
 * Resolves to assets/lotusblight/{geo,animations,textures}/block/infected_lotus_crown.{geo,animation,png}.json
 * via GeckoLib's DefaultedBlockGeoModel convention.
 */
public class LotusCrownModel extends DefaultedBlockGeoModel<LotusCrownBlockEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(LotusBlight.MODID, "textures/block/infected_lotus.png");

    public LotusCrownModel() {
        super(new ResourceLocation(LotusBlight.MODID, "infected_lotus_crown"));
    }

    @Override
    public ResourceLocation getTextureResource(LotusCrownBlockEntity animatable) {
        // Reuse the existing flower texture (infected_lotus.png) instead of requiring a
        // separately-named infected_lotus_crown.png — same art, no duplicate asset to maintain.
        return TEXTURE;
    }
}
