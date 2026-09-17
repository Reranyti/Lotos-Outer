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
    // infected_lotus.png used to double as both this crown's box-UV atlas (each cube in
    // infected_lotus_crown.geo.json samples a specific pixel region of it, texture_width/height
    // 32x32) AND a plain square texture for the cross-model flower block/item. Those are
    // incompatible: replacing infected_lotus.png with ordinary "centered icon" art (as part of
    // the sprite-sheet art pass) would scramble this model's petals/stem, since each cube would
    // sample a slice of a flat icon instead of its own dedicated atlas region. Split into its own
    // file so the two textures can evolve independently.
    private static final ResourceLocation TEXTURE = new ResourceLocation(LotusBlight.MODID, "textures/block/infected_lotus_crown_atlas.png");

    public LotusCrownModel() {
        super(new ResourceLocation(LotusBlight.MODID, "infected_lotus_crown"));
    }

    @Override
    public ResourceLocation getTextureResource(LotusCrownBlockEntity animatable) {
        return TEXTURE;
    }
}
