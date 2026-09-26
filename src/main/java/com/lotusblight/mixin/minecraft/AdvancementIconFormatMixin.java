package com.lotusblight.mixin.minecraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Fix for Ex Meteor Shower (a hard dependency): three of its advancements write the display icon in
 * the 1.20.5+ form {@code {"id": ...}} while 1.20.1 only reads {@code {"item": ...}}, so they failed
 * to load ("Unsupported icon type"). Before the advancements are parsed, any icon that has an "id"
 * and no "item" gets the same value as "item". An icon already in the 1.20.1 form is left alone, so
 * a fixed release of the mod (or any other pack using the right format) is untouched.
 *
 * remap = false with both names listed - "apply" in the development environment, its SRG name
 * m_5787_ in the released game - so no refmap is needed; LotusMixinPlugin skips it if neither exists.
 */
@Mixin(value = ServerAdvancementManager.class, remap = false)
public abstract class AdvancementIconFormatMixin {
    @Inject(method = {
            "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            "m_5787_(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V"
    }, at = @At("HEAD"), require = 0)
    private void lotusblight$acceptNewIconFormat(Map<ResourceLocation, JsonElement> advancements, ResourceManager resources,
                                                ProfilerFiller profiler, CallbackInfo ci) {
        for (JsonElement element : advancements.values()) {
            if (!element.isJsonObject()) continue;
            JsonObject root = element.getAsJsonObject();
            if (!root.has("display") || !root.get("display").isJsonObject()) continue;
            JsonObject display = root.getAsJsonObject("display");
            if (!display.has("icon") || !display.get("icon").isJsonObject()) continue;
            JsonObject icon = display.getAsJsonObject("icon");
            if (!icon.has("item") && icon.has("id") && icon.get("id").isJsonPrimitive()) {
                icon.addProperty("item", icon.get("id").getAsString());
            }
        }
    }
}
