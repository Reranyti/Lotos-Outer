package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.entity.ScreechEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws Screech from its mesh ({@link ScreechModel}): every body part is a rigid piece of stone hung on a bone, the bones are posed here
 * - a stalking walk, the hands coming up to cover the face when it is looked at - and the triangles are handed to the ordinary
 * entity buffers, so shader packs and the light of the place treat it like any other mob. The hollows and the eye are drawn once more
 * with the glowing texture. Far away a coarser mesh is used.
 */
public class ScreechRenderer extends EntityRenderer<ScreechEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(LotusBlight.MODID, "textures/entity/screech.png");
    private static final ResourceLocation GLOW = new ResourceLocation(LotusBlight.MODID, "textures/entity/screech_glowing.png");
    private static final double FAR_DISTANCE_SQ = 28.0 * 28.0;

    /** The pose that puts both hands in front of the face: shoulder forward and in, elbow up, fingers up and curled towards it. */
    private static final String[][] COVER_BONES = {
            {"left_arm", "-92", "-44", "0"}, {"left_forearm", "-66", "-10", "0"}, {"left_hand", "-8", "0", "0"},
            {"right_arm", "-92", "44", "0"}, {"right_forearm", "-66", "10", "0"}, {"right_hand", "-8", "0", "0"},
    };

    public ScreechRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5f;
    }

    @Override
    public ResourceLocation getTextureLocation(ScreechEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ScreechEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        ScreechModel model = ScreechModel.get();
        if (model == null) return;
        int n = model.boneNames.length;
        float[][] angles = pose(entity, model, partialTick);

        poseStack.pushPose();
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));        // the mesh's front is +z, as the world's south is
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        Matrix4f base = new Matrix4f(poseStack.last().pose());
        Matrix3f baseNormal = new Matrix3f(poseStack.last().normal());

        Matrix4f[] world = new Matrix4f[n];
        for (int i = 0; i < n; i++) {
            float[] pv = model.bonePivot[i];
            float[] a = angles[i];
            Matrix4f local = new Matrix4f().translation(pv[0], pv[1], pv[2])
                    .rotateZ((float) Math.toRadians(a[2])).rotateY((float) Math.toRadians(a[1])).rotateX((float) Math.toRadians(a[0]))
                    .translate(-pv[0], -pv[1], -pv[2]);
            world[i] = model.boneParent[i] >= 0 ? new Matrix4f(world[model.boneParent[i]]).mul(local) : local;
        }

        double dist = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.getX(), entity.getY(), entity.getZ());
        ScreechModel.Part[] parts = dist > FAR_DISTANCE_SQ ? model.far : model.near;
        VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        for (ScreechModel.Part part : parts) {
            Matrix4f m = new Matrix4f(base).mul(world[part.bone]);
            Matrix3f nm = new Matrix3f(baseNormal).mul(new Matrix3f(world[part.bone]));
            submit(body, part, m, nm, light, false);
            submit(glow, part, m, nm, LightTexture.FULL_BRIGHT, true);
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
    }

    /** The angles (degrees about x, y, z) of every bone for this moment. */
    private static float[][] pose(ScreechEntity e, ScreechModel model, float pt) {
        float[][] a = new float[model.boneNames.length][3];
        float age = e.tickCount + pt;
        float cover = Mth.lerp(pt, e.coverO, e.cover);
        float ease = cover * cover * (3 - 2 * cover);

        // standing: a slow sway, and the stalker's forward lean once it walks
        float speed = Math.min(1.0f, e.walkAnimation.speed(pt) * 2.0f);
        float walkPos = e.walkAnimation.position(pt);
        float swing = Mth.cos(walkPos * 0.6662f);
        float sway = Mth.sin(age * 0.045f);
        set(model, a, "body", 3.0f + 14.0f * speed + sway * 1.2f, 0, 0);
        set(model, a, "waist", 0, 0, sway * 0.8f);
        set(model, a, "right_leg", -34.0f * swing * speed, 0, 0);
        set(model, a, "left_leg", 34.0f * swing * speed, 0, 0);
        set(model, a, "right_shin", 28.0f * speed * Math.max(0.0f, swing), 0, 0);
        set(model, a, "left_shin", 28.0f * speed * Math.max(0.0f, -swing), 0, 0);
        float armSwing = (1.0f - ease);
        set(model, a, "right_arm", 18.0f * swing * speed * armSwing, 0, 0);
        set(model, a, "left_arm", -18.0f * swing * speed * armSwing, 0, 0);
        set(model, a, "right_forearm", -(10.0f + 6.0f * Mth.sin(age * 0.07f)) * armSwing, 0, 0);
        set(model, a, "left_forearm", -(10.0f + 6.0f * Mth.sin(age * 0.07f + 1.7f)) * armSwing, 0, 0);

        // the head follows where it looks
        float headYaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot) - Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot)), -45.0f, 45.0f);
        float headPitch = Mth.clamp(Mth.lerp(pt, e.xRotO, e.getXRot()), -30.0f, 30.0f);
        set(model, a, "head", headPitch * (1.0f - ease), -headYaw * (1.0f - ease), 0);

        // the hands come up to the face
        if (ease > 0.001f) {
            for (String[] c : COVER_BONES) {
                int i = model.boneIndex.get(c[0]);
                a[i][0] = a[i][0] * (1 - ease) + Float.parseFloat(c[1]) * ease;
                a[i][1] = a[i][1] * (1 - ease) + Float.parseFloat(c[2]) * ease;
                a[i][2] = a[i][2] * (1 - ease) + Float.parseFloat(c[3]) * ease;
            }
            int head = model.boneIndex.get("head");
            a[head][0] += 6.0f * ease;
        }
        return a;
    }

    private static void set(ScreechModel model, float[][] a, String bone, float x, float y, float z) {
        Integer i = model.boneIndex.get(bone);
        if (i == null) return;
        a[i][0] = x;
        a[i][1] = y;
        a[i][2] = z;
    }

    /** Hands a part's triangles to the buffer, moved by the bone matrices; {@code glowOnly} sends just the ones on the glowing tiles. */
    private static void submit(VertexConsumer vc, ScreechModel.Part part, Matrix4f m, Matrix3f nm, int light, boolean glowOnly) {
        float[] mm = new float[16];
        m.get(mm);
        float[] n = new float[9];
        nm.get(n);
        float[] v = part.verts;
        int tris = v.length / 24;
        for (int t = 0; t < tris; t++) {
            if (glowOnly && !part.glow[t]) continue;
            for (int k = 0; k < 3; k++) {
                int o = (t * 3 + k) * 8;
                float x = v[o], y = v[o + 1], z = v[o + 2];
                float nx = v[o + 3], ny = v[o + 4], nz = v[o + 5];
                vc.vertex(mm[0] * x + mm[4] * y + mm[8] * z + mm[12], mm[1] * x + mm[5] * y + mm[9] * z + mm[13], mm[2] * x + mm[6] * y + mm[10] * z + mm[14])
                        .color(255, 255, 255, 255)
                        .uv(v[o + 6], v[o + 7])
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(light)
                        .normal(n[0] * nx + n[3] * ny + n[6] * nz, n[1] * nx + n[4] * ny + n[7] * nz, n[2] * nx + n[5] * ny + n[8] * nz)
                        .endVertex();
            }
        }
    }
}
