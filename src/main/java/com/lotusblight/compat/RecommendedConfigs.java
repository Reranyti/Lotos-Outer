package com.lotusblight.compat;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.lotusblight.LotusConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recommended values we put into other mods' configs on the player's own install - editing our local
 * config folder only ever changed one machine, a player downloading the mods got the authors'
 * defaults. Each recommendation is set once: only if the value is still the mod's own default (a
 * value the player chose is never touched), and then remembered in our config so a later change of
 * mind by the player stays. Players can turn the whole thing off (compatibility.applyRecommendedSettings).
 * Works against whatever version of the mod is installed: a setting that doesn't exist there is skipped.
 */
public final class RecommendedConfigs {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record Recommendation(String hookId, String modId, ModConfig.Type type, String path, String modDefault, String value, String why) {
        String key() {
            return modId + ":" + path;
        }
    }

    public static final List<Recommendation> ALL = List.of(
            new Recommendation("streams_quality", "streamsreflowing", ModConfig.Type.COMMON,
                    "performance.terrainAccuracyLevel", "MEDIUM", "LOW",
                    "~12 s per river region instead of ~19 s - MEDIUM froze weaker PCs whenever new land loaded"));

    private static final Map<String, String> STATUS = new HashMap<>();

    private RecommendedConfigs() {}

    public static String status(String hookId) {
        return STATUS.getOrDefault(hookId, "ещё не проверялось");
    }

    /** Applies whatever is due for configs of the given type (COMMON at common setup, CLIENT at client setup). */
    public static void apply(ModConfig.Type type) {
        List<String> applied = new ArrayList<>(LotusConfig.APPLIED_RECOMMENDATIONS.get());
        boolean changedOurs = false;
        for (Recommendation rec : ALL) {
            if (rec.type() != type) continue;
            if (!ModList.get().isLoaded(rec.modId())) {
                STATUS.put(rec.hookId(), "мод не установлен");
                continue;
            }
            if (!LotusConfig.APPLY_RECOMMENDED_SETTINGS.get()) {
                STATUS.put(rec.hookId(), "выключено в нашем конфиге");
                continue;
            }
            if (applied.contains(rec.key())) {
                STATUS.put(rec.hookId(), "уже применялось раньше");
                continue;
            }
            ModConfig config = find(rec.modId(), type);
            CommentedConfig data = config == null ? null : config.getConfigData();
            if (data == null) {
                STATUS.put(rec.hookId(), "конфиг мода не загружен");
                continue;
            }
            Object current = data.get(rec.path());
            if (current == null) {
                STATUS.put(rec.hookId(), "пропущено: в этой версии мода нет настройки " + rec.path());
                continue;
            }
            if (!rec.modDefault().equalsIgnoreCase(String.valueOf(current))) {
                // The player set it themselves - that's their call, and it counts as settled.
                STATUS.put(rec.hookId(), "у игрока своё значение (" + current + "), не трогаем");
            } else {
                data.set(rec.path(), rec.value());
                config.save();
                if (config.getSpec() instanceof ForgeConfigSpec spec) spec.afterReload();
                STATUS.put(rec.hookId(), "применено: " + rec.path() + " = " + rec.value());
                LOGGER.info("Applied recommended setting {} = {} for {} ({})", rec.path(), rec.value(), rec.modId(), rec.why());
            }
            applied.add(rec.key());
            changedOurs = true;
        }
        if (changedOurs) {
            LotusConfig.APPLIED_RECOMMENDATIONS.set(applied);
            LotusConfig.APPLIED_RECOMMENDATIONS.save();
        }
    }

    private static ModConfig find(String modId, ModConfig.Type type) {
        var configs = ConfigTracker.INSTANCE.configSets().get(type);
        if (configs == null) return null;
        for (ModConfig config : configs) {
            if (config.getModId().equals(modId)) return config;
        }
        return null;
    }
}
