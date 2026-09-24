package com.lotusblight.client;

import com.lotusblight.client.gecko.HonchoModel;
import com.lotusblight.entity.HonchoEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Replaces HonchoRenderer (which just reused ZombieRenderer's vanilla model) - Honcho now has his own GeckoLib geometry, see HonchoModel. */
public class HonchoGeoRenderer extends GeoEntityRenderer<HonchoEntity> {
    public HonchoGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new HonchoModel());
    }
}
