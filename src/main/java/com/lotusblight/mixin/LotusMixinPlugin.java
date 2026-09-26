package com.lotusblight.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides, per installed version of the other mod, whether each of our fixes to it goes in (see
 * com.lotusblight.compat.CompatHooks for the list the game shows). A fix is applied when its mod is
 * installed AND the exact method it patches is actually there - checked in the real bytecode, not
 * against version numbers, so the same jar works with the version we tested, older ones and newer
 * ones: if a newer release renamed or removed the method, that one fix is skipped instead of the
 * game crashing on start. Each decision is left in a system property ("lotusblight.hook.<id>"),
 * which the game side reads back for the startup report and /lotus compat (the plugin and the game
 * live in different class loaders, a static field wouldn't be shared).
 */
public class LotusMixinPlugin implements IMixinConfigPlugin {
    public static final String STATUS_PREFIX = "lotusblight.hook.";

    /** One patch: which hook it belongs to, which mod must be present, which method it needs (any of the names - dev and production). */
    private record Patch(String hookId, String modId, String descriptor, String... methodNames) {}

    private static final Map<String, Patch> PATCHES = Map.of(
            "com.lotusblight.mixin.worldedit.ForgeWorldBiomeFixMixin",
            new Patch("worldedit_setbiome", "worldedit",
                    "(Lcom/sk89q/worldedit/math/BlockVector3;Lcom/sk89q/worldedit/world/biome/BiomeType;)Z", "setBiome"),
            "com.lotusblight.mixin.chatoverhaul.CustomChatComponentSpeakerIconMixin",
            new Patch("chatoverhaul_icons", "chatoverhaul",
                    "(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/String;IIF)V", "renderHead"),
            "com.lotusblight.mixin.minecraft.AdvancementIconFormatMixin",
            new Patch("meteor_shower_advancements", "meteor_shower",
                    "(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
                    "apply", "m_5787_"));

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        Patch patch = PATCHES.get(mixinClassName);
        if (patch == null) return true;
        if (LoadingModList.get().getModFileById(patch.modId()) == null) {
            record(patch, "мод не установлен");
            return false;
        }
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
            for (MethodNode method : node.methods) {
                if (!method.desc.equals(patch.descriptor())) continue;
                for (String name : patch.methodNames()) {
                    if (method.name.equals(name)) {
                        record(patch, "применён");
                        return true;
                    }
                }
            }
            record(patch, "пропущен: в этой версии мода нет метода " + patch.methodNames()[0]);
        } catch (Exception e) {
            record(patch, "пропущен: класс " + targetClassName + " не найден");
        }
        return false;
    }

    private static void record(Patch patch, String status) {
        System.setProperty(STATUS_PREFIX + patch.hookId(), status);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
