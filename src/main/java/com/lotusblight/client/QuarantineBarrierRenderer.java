package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * "Обьект запечатали на карантин" - the story's quarantine wall, self-written rather than reusing
 * vanilla's own hardcoded (and uncolorable without mixins, which this mod doesn't use) world-border
 * render. Reuses vanilla's own forcefield.png texture for the familiar "rippling force wall" look
 * ("посмотри как выглядит барьер мира майнкрафта"), but tinted an aggressive purple with a vertical
 * gradient fading to near-black toward the ground instead of vanilla's flat color, drawn as our own
 * quad strip along whichever border edge is nearest the camera. Purely visual - QuarantineBarrier
 * (server side) is what actually stops/bounces the player; this never needs to line up pixel-exact
 * with where the real push happens.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuarantineBarrierRenderer {
    private static final ResourceLocation FORCEFIELD_TEXTURE = new ResourceLocation("textures/misc/forcefield.png");

    /** How far along the border edge (each side of the camera's own projected position) to draw. */
    private static final double STRIP_HALF_LENGTH = 96.0;
    /** Only bother drawing when the camera is within this many blocks of the edge. */
    private static final double VISIBLE_DISTANCE = 160.0;
    private static final double BOTTOM_Y = -64.0;
    private static final double TOP_Y = 320.0;
    private static final int TILE_HEIGHT = 32;

    private static final float TOP_R = 0.60f, TOP_G = 0.05f, TOP_B = 0.85f;
    private static final float BOTTOM_R = 0.04f, BOTTOM_G = 0.01f, BOTTOM_B = 0.06f;
    private static final float ALPHA = 0.55f;
    private static final int FULL_BRIGHT = 0xF000F0;

    private QuarantineBarrierRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        WorldBorder border = mc.level.getWorldBorder();
        double camX = event.getCamera().getPosition().x;
        double camY = event.getCamera().getPosition().y;
        double camZ = event.getCamera().getPosition().z;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.text(FORCEFIELD_TEXTURE));
        PoseStack poseStack = event.getPoseStack();

        drawEdgeIfNear(consumer, poseStack, camX, camY, camZ, border.getMinX(), true);
        drawEdgeIfNear(consumer, poseStack, camX, camY, camZ, border.getMaxX(), true);
        drawEdgeIfNear(consumer, poseStack, camX, camY, camZ, border.getMinZ(), false);
        drawEdgeIfNear(consumer, poseStack, camX, camY, camZ, border.getMaxZ(), false);

        bufferSource.endBatch(RenderType.text(FORCEFIELD_TEXTURE));
    }

    /** xEdge=true means `value` is a fixed X plane (running along Z); false means a fixed Z plane (running along X). */
    private static void drawEdgeIfNear(VertexConsumer consumer, PoseStack poseStack, double camX, double camY, double camZ, double value, boolean xEdge) {
        double distance = xEdge ? Math.abs(camX - value) : Math.abs(camZ - value);
        if (distance > VISIBLE_DISTANCE) return;

        double along = xEdge ? camZ : camX;
        double from = along - STRIP_HALF_LENGTH;
        double to = along + STRIP_HALF_LENGTH;

        int tiles = (int) Math.ceil((TOP_Y - BOTTOM_Y) / TILE_HEIGHT);
        for (int i = 0; i < tiles; i++) {
            double y0 = BOTTOM_Y + i * TILE_HEIGHT;
            double y1 = Math.min(TOP_Y, y0 + TILE_HEIGHT);
            float[] colorLow = gradientColor(y0);
            float[] colorHigh = gradientColor(y1);

            double x0 = xEdge ? value - camX : from - camX;
            double z0 = xEdge ? from - camZ : value - camZ;
            double x1 = xEdge ? value - camX : to - camX;
            double z1 = xEdge ? to - camZ : value - camZ;

            float v0 = 0f;
            float v1 = (float) ((y1 - y0) / TILE_HEIGHT);
            float uLen = (float) (2.0 * STRIP_HALF_LENGTH / TILE_HEIGHT);

            quad(consumer, poseStack,
                    x0, y0 - camY, z0, 0f, v1, colorLow,
                    x1, y0 - camY, z1, uLen, v1, colorLow,
                    x1, y1 - camY, z1, uLen, v0, colorHigh,
                    x0, y1 - camY, z0, 0f, v0, colorHigh);
        }
    }

    private static float[] gradientColor(double y) {
        float t = (float) Math.max(0.0, Math.min(1.0, (y - BOTTOM_Y) / (TOP_Y - BOTTOM_Y)));
        return new float[]{
                BOTTOM_R + (TOP_R - BOTTOM_R) * t,
                BOTTOM_G + (TOP_G - BOTTOM_G) * t,
                BOTTOM_B + (TOP_B - BOTTOM_B) * t
        };
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack,
                              double x0, double y0, double z0, float u0, float v0, float[] c0,
                              double x1, double y1, double z1, float u1, float v1, float[] c1,
                              double x2, double y2, double z2, float u2, float v2, float[] c2,
                              double x3, double y3, double z3, float u3, float v3, float[] c3) {
        var pose = poseStack.last().pose();
        consumer.vertex(pose, (float) x0, (float) y0, (float) z0).color(c0[0], c0[1], c0[2], ALPHA).uv(u0, v0).uv2(FULL_BRIGHT).endVertex();
        consumer.vertex(pose, (float) x1, (float) y1, (float) z1).color(c1[0], c1[1], c1[2], ALPHA).uv(u1, v1).uv2(FULL_BRIGHT).endVertex();
        consumer.vertex(pose, (float) x2, (float) y2, (float) z2).color(c2[0], c2[1], c2[2], ALPHA).uv(u2, v2).uv2(FULL_BRIGHT).endVertex();
        consumer.vertex(pose, (float) x3, (float) y3, (float) z3).color(c3[0], c3[1], c3[2], ALPHA).uv(u3, v3).uv2(FULL_BRIGHT).endVertex();
    }
}
