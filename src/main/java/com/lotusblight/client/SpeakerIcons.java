package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Chat icons for the story's characters (see ChatDialogue). Chat Overhaul draws every sender's head
 * from a player skin looked up by name, so for these names it only ever found the default Steve -
 * Star Light had no icon of his own. The renderHead mixin asks here first.
 */
public final class SpeakerIcons {
    private static final ResourceLocation STAR_LIGHT = new ResourceLocation(LotusBlight.MODID, "textures/misc/star_light.png");
    private static final ResourceLocation WORLD_LOTUS = new ResourceLocation(LotusBlight.MODID, "textures/mob_effect/lotoniriya.png");
    private static final ResourceLocation HONCHO = new ResourceLocation(LotusBlight.MODID, "textures/entity/honcho.png");

    private SpeakerIcons() {}

    /**
     * Draws this speaker's icon into the size x size square at (x, y) and returns true, or returns
     * false for anyone else so Chat Overhaul draws its usual skin head.
     */
    public static boolean draw(GuiGraphics g, String speaker, int x, int y, int size, float alpha) {
        ResourceLocation texture;
        switch (speaker) {
            case ChatDialogue.STAR_LIGHT -> texture = STAR_LIGHT;
            case ChatDialogue.WORLD_LOTUS -> texture = WORLD_LOTUS;
            case ChatDialogue.HONCHO -> texture = HONCHO;
            default -> {
                return false;
            }
        }
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        if (texture == HONCHO) {
            // Honcho's skin-layout texture: face (8,8) plus the hat layer (40,8), like a player head.
            g.blit(texture, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64);
            g.blit(texture, x, y, size, size, 40.0f, 8.0f, 8, 8, 64, 64);
        } else if (texture == STAR_LIGHT) {
            g.blit(texture, x, y, size, size, 0.0f, 0.0f, 128, 128, 128, 128);
        } else {
            g.blit(texture, x, y, size, size, 0.0f, 0.0f, 18, 18, 18, 18);
        }
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        return true;
    }
}
