package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.entity.HorichoniyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.resources.ResourceLocation;

/** WanderingTraderRenderer hardcodes vanilla's own trader skin - this override swaps in Horichoniy's own texture. */
public final class HorichoniyRenderer extends WanderingTraderRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(LotusBlight.MODID, "textures/entity/horichoniy.png");

    public HorichoniyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(net.minecraft.world.entity.npc.WanderingTrader entity) {
        return TEXTURE;
    }
}
