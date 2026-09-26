package com.lotusblight.compat;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Every change this mod makes to, or through, another mod - one list, so it's clear what depends on
 * which mod and what state each piece is in on this install. Kinds:
 * <ul>
 *   <li>PATCH - a mixin fixing a bug in the other mod; LotusMixinPlugin only applies it when the
 *       method it patches exists in the installed version, so any version of the mod is safe;</li>
 *   <li>FEATURE - our code using the other mod's public side when it's installed;</li>
 *   <li>CONFIG - a recommended value in its config (RecommendedConfigs).</li>
 * </ul>
 * Logged once at server start and shown by /lotus compat.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.lotusblight.LotusBlight.MODID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class CompatHooks {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Kind { PATCH, FEATURE, CONFIG }

    /** Same prefix LotusMixinPlugin writes under - kept here as its own literal, game code never touches the plugin class. */
    private static final String PATCH_STATUS_PREFIX = "lotusblight.hook.";

    public record Hook(String id, String modId, String modName, Kind kind, boolean clientOnly, String description) {}

    public static final List<Hook> ALL = List.of(
            new Hook("worldedit_setbiome", "worldedit", "WorldEdit", Kind.PATCH, false,
                    "setBiome писал биом не в ту клетку 4x4x4 - смена биома на фазе 4 работает через исправленный WorldEdit"),
            new Hook("meteor_shower_advancements", "meteor_shower", "Ex Meteor Shower", Kind.PATCH, false,
                    "три его достижения не загружались из-за формата иконки 1.20.5+"),
            new Hook("chatoverhaul_icons", "chatoverhaul", "Chat Overhaul", Kind.PATCH, true,
                    "свои иконки персонажей в чате вместо головы Стива"),
            new Hook("chatoverhaul_dialogue", "chatoverhaul", "Chat Overhaul", Kind.FEATURE, true,
                    "реплики персонажей идут в его чат, наши окна диалогов не рисуются"),
            new Hook("cinematic_meeting", "cinematic", "Cinematic", Kind.FEATURE, true,
                    "кадр с камерой во встрече с Хончо"),
            new Hook("streams_prebuild", "streamsreflowing", "Streams Reflowing", Kind.FEATURE, false,
                    "реки вокруг точки появления строятся заранее при первом открытии мира"),
            new Hook("streams_quality", "streamsreflowing", "Streams Reflowing", Kind.CONFIG, false,
                    "рекомендуемое качество рек LOW"),
            new Hook("streams_flow", "streamsreflowing", "Streams Reflowing", Kind.FEATURE, false,
                    "заражение идёт по течению его рек"),
            new Hook("journeymap_overlay", "journeymap", "JourneyMap", Kind.FEATURE, true,
                    "очаги и зона заражения на карте JourneyMap"));

    private CompatHooks() {}

    public static String status(Hook hook) {
        if (!ModList.get().isLoaded(hook.modId())) return "мод не установлен";
        return switch (hook.kind()) {
            case PATCH -> System.getProperty(PATCH_STATUS_PREFIX + hook.id(),
                    hook.clientOnly() ? "только в клиенте" : "не проверялся");
            case CONFIG -> RecommendedConfigs.status(hook.id());
            case FEATURE -> hook.clientOnly() ? "включён (в клиенте)" : "включён";
        };
    }

    public static List<String> report() {
        List<String> lines = new ArrayList<>();
        for (Hook hook : ALL) {
            String version = ModList.get().getModContainerById(hook.modId())
                    .map(c -> " " + c.getModInfo().getVersion()).orElse("");
            lines.add(hook.modName() + version + " / " + hook.id() + ": " + status(hook) + " - " + hook.description());
        }
        return lines;
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        logReport();
    }

    public static void logReport() {
        LOGGER.info("Lotus Blight compatibility hooks:");
        for (String line : report()) LOGGER.info("  {}", line);
    }
}
