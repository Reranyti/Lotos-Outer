package com.lotusblight.mixin.chatoverhaul;

import com.lotusblight.client.SpeakerIcons;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat Overhaul (optional) draws every chat sender's head from a player skin found by name, falling
 * back to the default Steve - so the story's characters (Star Light first of all) showed up with a
 * stranger's face. Their own icons are drawn instead (SpeakerIcons); real players are untouched.
 * {@code @Pseudo} - without Chat Overhaul installed this mixin is simply skipped. Its renderHead
 * draws an 8x8 face at scale 2 from (x, y), so the icon fills the same 16x16 square.
 */
@Pseudo
@Mixin(targets = "com.example.chatoverhaul.client.CustomChatComponent", remap = false)
public abstract class CustomChatComponentSpeakerIconMixin {
    private static final int HEAD_PIXELS = 16;

    @Inject(method = "renderHead(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/String;IIF)V", at = @At("HEAD"), cancellable = true)
    private void lotusblight$drawSpeakerIcon(GuiGraphics g, String sender, int x, int y, float alpha, CallbackInfo ci) {
        if (SpeakerIcons.draw(g, sender, x, y, HEAD_PIXELS, alpha)) ci.cancel();
    }
}
