package com.lotusblight.client.gecko;

import com.lotusblight.world.LotusCrownBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class LotusCrownBlockRenderer extends GeoBlockRenderer<LotusCrownBlockEntity> {
    public LotusCrownBlockRenderer() {
        super(new LotusCrownModel());
    }
}
