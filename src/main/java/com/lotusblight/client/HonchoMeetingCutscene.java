package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client side of the trip-meeting (see HonchoMeetingManager). The whole HUD goes away, the camera
 * drops to the ground with both hands pushing off it, then rises to Honcho standing over the player
 * with his hand out, and the "Принять руку?" window opens. After the last answer the camera stays on
 * him for his reaction and then hands control back.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HonchoMeetingCutscene {
    private static final int FALL_TICKS = 35;
    private static final int LOOK_UP_TICKS = 30;
    private static final int AFTER_ACCEPT_TICKS = 68;
    private static final int AFTER_LEAVE_TICKS = 55;
    private static final float GROUND_PITCH = 72.0f;

    private enum Phase { FALL, LOOK_UP, CHOICE, AFTER }

    private static boolean active;
    private static Phase phase;
    private static int phaseTicks;
    private static int afterTicks;
    private static int honchoEntityId;
    private static float startYaw;
    private static CameraType previousCamera;

    private HonchoMeetingCutscene() {}

    public static void start(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        honchoEntityId = entityId;
        startYaw = mc.player.getYRot();
        if (!active) {
            previousCamera = mc.options.getCameraType();
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        active = true;
        setPhase(Phase.FALL);
    }

    /** Called by HonchoMeetingScreen once the final answer is in. */
    static void finish(boolean accepted) {
        if (!active) return;
        afterTicks = accepted ? AFTER_ACCEPT_TICKS : AFTER_LEAVE_TICKS;
        setPhase(Phase.AFTER);
    }

    public static void reset() {
        if (!active) return;
        active = false;
        Minecraft mc = Minecraft.getInstance();
        if (previousCamera != null) mc.options.setCameraType(previousCamera);
        previousCamera = null;
        if (mc.screen instanceof HonchoMeetingScreen) mc.setScreen(null);
    }

    public static boolean isActive() {
        return active;
    }

    private static void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyShift.setDown(false);

        phaseTicks++;
        if (phase == Phase.FALL && phaseTicks >= FALL_TICKS) {
            setPhase(Phase.LOOK_UP);
        } else if (phase == Phase.LOOK_UP && phaseTicks >= LOOK_UP_TICKS) {
            setPhase(Phase.CHOICE);
            mc.setScreen(new HonchoMeetingScreen(false));
        } else if (phase == Phase.AFTER && phaseTicks >= afterTicks) {
            reset();
        }
    }

    /** Camera per frame rather than per tick, so the look-up is smooth. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        float groundYaw = startYaw;
        float[] target = lookAtHoncho(mc);
        float progress = (phaseTicks + event.renderTickTime) / (float) LOOK_UP_TICKS;
        float yaw;
        float pitch;
        switch (phase) {
            case FALL -> {
                yaw = groundYaw;
                pitch = GROUND_PITCH + Mth.sin((phaseTicks + event.renderTickTime) * 0.35f) * 2.0f;
            }
            case LOOK_UP -> {
                float t = Mth.clamp(progress, 0.0f, 1.0f);
                t = t * t * (3.0f - 2.0f * t);
                yaw = target == null ? groundYaw : Mth.rotLerp(t, groundYaw, target[0]);
                pitch = Mth.lerp(t, GROUND_PITCH, target == null ? -20.0f : target[1]);
            }
            default -> {
                yaw = target == null ? mc.player.getYRot() : target[0];
                pitch = target == null ? -20.0f : target[1];
            }
        }
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw);
    }

    private static float[] lookAtHoncho(Minecraft mc) {
        Entity honcho = mc.level.getEntity(honchoEntityId);
        if (honcho == null) return null;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 target = honcho.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    /** "всё скрывается" - the entire HUD, including the minimap and other mods' overlays. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (active) event.setCanceled(true);
    }

    /** Both hands on the ground pushing up while the player tries to get up. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (!active) return;
        event.setCanceled(true);
        if (phase != Phase.FALL && phase != Phase.LOOK_UP) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof PlayerRenderer renderer)) return;

        // The arms slide out of view while the camera rises.
        float lower = phase == Phase.LOOK_UP ? Mth.clamp((phaseTicks + event.getPartialTick()) / 12.0f, 0.0f, 1.0f) * 0.8f : 0.0f;
        float push = Mth.sin((phaseTicks + event.getPartialTick()) * 0.35f) * 0.06f;
        renderArm(renderer, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), mc.player, true, push - lower);
        renderArm(renderer, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), mc.player, false, push - lower);
    }

    /** Same arm placement vanilla uses for an empty hand, mirrored for the off side. */
    private static void renderArm(PlayerRenderer renderer, PoseStack poseStack, MultiBufferSource buffer, int light,
                                  AbstractClientPlayer player, boolean right, float offsetY) {
        float side = right ? 1.0f : -1.0f;
        poseStack.pushPose();
        poseStack.translate(side * 0.64f, -0.6f + offsetY, -0.72f);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 45.0f));
        poseStack.translate(side * -1.0f, 3.6f, 3.5f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 120.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -135.0f));
        poseStack.translate(side * 5.6f, 0.0f, 0.0f);
        if (right) {
            renderer.renderRightHand(poseStack, buffer, light, player);
        } else {
            renderer.renderLeftHand(poseStack, buffer, light, player);
        }
        poseStack.popPose();
    }
}
