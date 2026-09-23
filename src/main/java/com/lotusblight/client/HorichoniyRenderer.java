package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.WanderingTrader;

/** Same technique as HonchoRenderer - WanderingTraderRenderer hardcodes its own vanilla texture, this override swaps in Horichoniy's own. */
public final class HorichoniyRenderer extends WanderingTraderRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(LotusBlight.MODID, "textures/entity/horichoniy.png");

    public HorichoniyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(WanderingTrader entity) {
        return TEXTURE;
    }
}
