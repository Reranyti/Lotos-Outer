package com.lotusblight.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fingers for the player model: each hand gets a palm and five fingers of two bones, cut from the lowest three
 * pixel rows of the arm (so the arm itself is three pixels shorter and the palm and fingers take its place, in the
 * same colours of the skin). The fingers follow what the hand is doing - empty and loose, closed round an item,
 * a fist while it swings - each one eased on its own so the hand closes from the knuckles and never holds quite still.
 * Plugged in from {@code PlayerModelFingersMixin}.
 */
public final class PlayerFingers {
    private static final String[] NAMES = {"thumb", "index", "middle", "ring", "pinky"};
    private static final float[][] LEN = {{0.95f, 0.85f}, {1.05f, 0.95f}, {1.15f, 1.0f}, {1.05f, 0.9f}, {0.85f, 0.75f}};
    private static final float[] Z = {1.75f, 1.5f, 0.5f, -0.5f, -1.5f};        // in the hand's own space the index finger is at the front
    private static final float[][] AMOUNT = {{42, 58}, {82, 92}, {88, 95}, {92, 98}, {94, 100}};
    private static final float[] LOOSE = {0.10f, 0.20f, 0.28f, 0.36f, 0.44f};
    private static final float[] FIST = {0.8f, 1, 1, 1, 1};
    private static final float[] GRIP = {0.55f, 0.78f, 0.82f, 0.85f, 0.88f};

    private PlayerFingers() {}

    // ------------------------------------------------------------ the mesh

    /** Shortens the arms and sleeves by the three rows of the hand and gives each a palm with fingers. */
    public static void addHands(MeshDefinition mesh, CubeDeformation deformation, boolean slim) {
        PartDefinition root = mesh.getRoot();
        int w = slim ? 3 : 4;
        // right arm: box x from -3 (wide) / -2 (slim); left arm: -1 to +3 / +2
        float rightX = slim ? -2.0f : -3.0f, leftX = -1.0f;
        float y = slim ? 2.5f : 2.0f;
        arm(root, "right_arm", "right_sleeve", 40, 16, 40, 32, rightX, -5.0f, y, w, deformation, true);
        arm(root, "left_arm", "left_sleeve", 32, 48, 48, 48, leftX, 5.0f, y, w, deformation, false);
    }

    private static void arm(PartDefinition root, String armName, String sleeveName, int u, int v, int su, int sv,
                            float boxX, float offsetX, float offsetY, int w, CubeDeformation deformation, boolean right) {
        PartDefinition arm = root.addOrReplaceChild(armName,
                CubeListBuilder.create().texOffs(u, v).addBox(boxX, -2.0f, -2.0f, w, 9.0f, 4.0f, deformation), PartPose.offset(offsetX, offsetY, 0.0f));
        PartDefinition sleeve = root.addOrReplaceChild(sleeveName,
                CubeListBuilder.create().texOffs(su, sv).addBox(boxX, -2.0f, -2.0f, w, 9.0f, 4.0f, deformation.extend(0.25f)), PartPose.offset(offsetX, offsetY, 0.0f));
        // The palm: the row above the fingers, its sides taken from the hand rows of the arm's own texture (rows 29, and 45 for the sleeve).
        arm.addOrReplaceChild(right ? "right_hand" : "left_hand", CubeListBuilder.create().texOffs(u, v + 9).addBox(boxX, 0.0f, -2.0f, w, 1.0f, 4.0f, deformation),
                PartPose.offset(0.0f, 7.0f, 0.0f));
        sleeve.addOrReplaceChild(right ? "right_palm" : "left_palm", CubeListBuilder.create().texOffs(su, sv + 9).addBox(boxX, 0.0f, -2.0f, w, 1.0f, 4.0f, deformation.extend(0.25f)),
                PartPose.offset(0.0f, 7.0f, 0.0f));
        PartDefinition hand = arm.getChild(right ? "right_hand" : "left_hand");
        float center = boxX + w / 2.0f;
        float medial = right ? 1 : -1;
        String side = right ? "right" : "left";
        for (int f = 0; f < 5; f++) {
            float x = center + medial * (f == 0 ? (w / 2.0f + 0.35f) : 0.1f);
            float top = f == 0 ? 0.4f : 1.0f;                      // where the finger hangs from, below the wrist
            float thick = f == 0 ? 1.0f : 1.3f, depth = f == 0 ? 0.85f : 0.7f;
            float[] rest = rest(right, f, 1);
            PartDefinition first = hand.addOrReplaceChild(side + "_" + NAMES[f] + "_1",
                    CubeListBuilder.create().texOffs(u + 1, v + 13).addBox(-thick / 2, 0, -depth / 2, thick, LEN[f][0], depth),
                    PartPose.offsetAndRotation(x - center + center, top, -Z[f], rest[0], rest[1], rest[2]));
            float[] rest2 = rest(right, f, 2);
            first.addOrReplaceChild(side + "_" + NAMES[f] + "_2",
                    CubeListBuilder.create().texOffs(u + 1, v + 14).addBox(-thick / 2, 0, -depth / 2, thick, LEN[f][1], depth),
                    PartPose.offsetAndRotation(0, LEN[f][0], 0, rest2[0], rest2[1], rest2[2]));
        }
    }

    /** Rotation (x, y, z radians) of a finger bone at a curl of 0..1. Right hand: curls towards +x. */
    private static float[] rotation(boolean right, int f, int seg, float c, float spread) {
        float sign = right ? 1 : -1;
        c = Math.max(0, Math.min(1, c));
        float a = AMOUNT[f][0] * c, b = AMOUNT[f][1] * c;
        if (seg == 2) return new float[]{0, 0, (float) Math.toRadians(-sign * b)};
        if (f == 0) {
            float z = sign * (a - 16 * (1 - c));
            return new float[]{0, (float) Math.toRadians(sign * (8 + 22 * c)), (float) Math.toRadians(-z)};
        }
        float fan = (f - 2.5f) * 8 * spread * (1 - c);
        return new float[]{(float) Math.toRadians(fan), 0, (float) Math.toRadians(-sign * a)};
    }

    private static float[] rest(boolean right, int f, int seg) {
        return rotation(right, f, seg, LOOSE[f], 0.3f);
    }

    // ------------------------------------------------------------ the motion

    private static final class Hand {
        final ModelPart[][] bones = new ModelPart[5][2];
    }

    private static final class Parts {
        Hand right, left;
    }

    private static final Map<PlayerModel<?>, Parts> PARTS = new WeakHashMap<>();
    private static final Map<LivingEntity, float[][]> STATE = new WeakHashMap<>();

    private static Parts partsOf(PlayerModel<?> model) {
        Parts parts = PARTS.get(model);
        if (parts != null) return parts;
        parts = new Parts();
        try {
            parts.right = hand(model.rightArm.getChild("right_hand"), "right");
            parts.left = hand(model.leftArm.getChild("left_hand"), "left");
        } catch (RuntimeException e) {
            parts = new Parts();                // another mod changed the model: no fingers, nothing else breaks
        }
        PARTS.put(model, parts);
        return parts;
    }

    private static Hand hand(ModelPart part, String side) {
        Hand h = new Hand();
        for (int f = 0; f < 5; f++) {
            h.bones[f][0] = part.getChild(side + "_" + NAMES[f] + "_1");
            h.bones[f][1] = h.bones[f][0].getChild(side + "_" + NAMES[f] + "_2");
        }
        return h;
    }

    /** What the hand is holding or doing decides how closed it is; called after the model has posed the arms. */
    public static void animate(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        Parts parts = partsOf(model);
        if (parts.right == null || parts.left == null) return;
        // The model is shared by every player drawn with it, so each one's finger curls are kept on their own.
        float[][] state = STATE.computeIfAbsent(entity, e -> {
            float[][] s = new float[2][5];
            for (float[] row : s) java.util.Arrays.fill(row, Float.NaN);
            return s;
        });
        for (int side = 0; side < 2; side++) {
            boolean right = side == 0;
            Hand hand = right ? parts.right : parts.left;
            HumanoidArm arm = right ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
            InteractionHand hh = entity.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = entity.getItemInHand(hh);
            boolean swinging = entity.swinging && (entity.swingingArm == hh) && model.attackTime > 0;
            boolean using = entity.isUsingItem() && entity.getUsedItemHand() == hh;
            float[] target;
            float fist = 0;
            if (swinging) {
                target = FIST;
                fist = 1;
            } else if (using || !stack.isEmpty()) {
                target = GRIP;
            } else {
                target = LOOSE;
            }
            float tense = entity.isSprinting() ? 0.12f : entity.isCrouching() ? 0.06f : 0;
            for (int f = 0; f < 5; f++) {
                float flutter = target == LOOSE ? 0.06f * (float) Math.sin(ageInTicks * 0.11 + f * 1.15 + (right ? 0 : 2.1)) * (1 - target[f]) : 0;
                float want = Math.min(1, target[f] + flutter + tense * (1 - target[f]));
                float k = fist > 0 ? 0.6f : 0.32f;                        // a blow closes the hand at once, the rest eases
                float[] curl = state[side];
                curl[f] = Float.isNaN(curl[f]) ? want : curl[f] + (want - curl[f]) * k;
                float[] r1 = rotation(right, f, 1, curl[f], 0.3f);
                float[] r2 = rotation(right, f, 2, curl[f], 0.3f);
                hand.bones[f][0].xRot = r1[0];
                hand.bones[f][0].yRot = r1[1];
                hand.bones[f][0].zRot = r1[2];
                hand.bones[f][1].xRot = r2[0];
                hand.bones[f][1].yRot = r2[1];
                hand.bones[f][1].zRot = r2[2];
            }
        }
    }
}
