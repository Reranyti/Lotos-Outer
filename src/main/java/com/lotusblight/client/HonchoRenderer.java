package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.entity.HonchoEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;

/** ZombieRenderer hardcodes vanilla zombie.png - this override is the one line that actually gives Honcho his own original look. */
public final class HonchoRenderer extends ZombieRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(LotusBlight.MODID, "textures/entity/honcho.png");

    public HonchoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Zombie entity) {
        return TEXTURE;
    }
}
