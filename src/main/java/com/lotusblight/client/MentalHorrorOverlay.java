package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.registry.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The look of "Психически не здоров.": the whole picture is half-grey and tense, and from the right and the left walls of meat
 * watch from the edges, slowly breathing in and out. Drawn as a layer of the interface (not as a post-processing shader), so it
 * works the same with any shader pack. It fades in and out with the effect.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MentalHorrorOverlay {
    private static final ResourceLocation LEFT = new ResourceLocation(LotusBlight.MODID, "textures/misc/meat_wall_left.png");
    private static final ResourceLocation RIGHT = new ResourceLocation(LotusBlight.MODID, "textures/misc/meat_wall_right.png");
    private static float strength;                  // 0..1, eased towards whether the effect is on

    private MentalHorrorOverlay() {}

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.VIGNETTE.type()) return;          // once a frame, under the rest of the interface
        Minecraft mc = Minecraft.getInstance();
        boolean on = mc.player != null && mc.player.hasEffect(ModEffects.MENTALLY_UNWELL.get());
        strength += ((on ? 1.0f : 0.0f) - strength) * 0.06f;
        if (strength < 0.01f) {
            strength = 0.0f;
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float t = (mc.player != null ? mc.player.tickCount : 0) + event.getPartialTick();

        // the picture goes half-grey and a little dark
        g.fill(0, 0, w, h, ((int) (strength * 112) << 24) | 0x707074);
        g.fill(0, 0, w, h, ((int) (strength * 46) << 24));
        // and closes in from the top and the bottom
        int band = h / 5;
        g.fillGradient(0, 0, w, band, ((int) (strength * 150) << 24), 0x00000000);
        g.fillGradient(0, h - band, w, h, 0x00000000, ((int) (strength * 150) << 24));

        // the walls of meat, breathing: they come in and go back, and creep a little up and down
        float breath = 0.5f + 0.5f * (float) Math.sin(t * 0.045f);
        int wallW = (int) (w * (0.13f + 0.05f * breath) * (0.55f + 0.45f * strength));
        int drift = (int) (Math.sin(t * 0.021f) * 24);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0f, 1.0f, 1.0f, strength);
        g.blit(LEFT, 0, drift - 24, wallW, h + 48, 0.0f, 0.0f, 128, 512, 128, 512);
        g.blit(RIGHT, w - wallW, -drift - 24, wallW, h + 48, 0.0f, 0.0f, 128, 512, 128, 512);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
