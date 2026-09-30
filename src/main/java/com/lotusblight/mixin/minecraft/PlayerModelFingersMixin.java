package com.lotusblight.mixin.minecraft;

import com.lotusblight.client.PlayerFingers;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fingers on the player model (see {@link PlayerFingers}): the arm meshes get a palm and five fingers a side when the
 * model is built, and the fingers are posed each time the model is. Both names of each method are listed (development
 * and production) and neither injection is required, so a game where they moved only loses the fingers.
 */
@Mixin(value = PlayerModel.class, remap = false)
public abstract class PlayerModelFingersMixin {
    @Inject(method = {
            "createMesh(Lnet/minecraft/client/model/geom/builders/CubeDeformation;Z)Lnet/minecraft/client/model/geom/builders/MeshDefinition;",
            "m_170825_(Lnet/minecraft/client/model/geom/builders/CubeDeformation;Z)Lnet/minecraft/client/model/geom/builders/MeshDefinition;"
    }, at = @At("RETURN"), require = 0)
    private static void lotusblight$fingers(CubeDeformation deformation, boolean slim, CallbackInfoReturnable<MeshDefinition> cir) {
        PlayerFingers.addHands(cir.getReturnValue(), deformation, slim);
    }

    @Inject(method = {
            "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"
    }, at = @At("TAIL"), require = 0)
    private void lotusblight$poseFingers(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                        float netHeadYaw, float headPitch, CallbackInfo ci) {
        PlayerFingers.animate((PlayerModel<?>) (Object) this, entity, ageInTicks);
    }
}
