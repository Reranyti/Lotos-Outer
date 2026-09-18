package com.lotusblight.client.config;

import com.lotusblight.LotusConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * In-game config screen for LotusConfig's spread/map tuning, built with YACL.
 * Only ever constructed after the caller has confirmed YACL is actually
 * loaded (see LotusBlight's ModList.isLoaded("yet_another_config_lib_v3")
 * guard) — this class references YACL types directly and would throw
 * NoClassDefFoundError if loaded without it present.
 */
public final class LotusConfigScreen {

    private LotusConfigScreen() {
    }

    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Lotus Blight"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Заражение и карта"))
                        .option(intOption(
                                "Интервал спреда (тики)",
                                "Как часто движок заражения пытается распространиться. Меньше значение — быстрее заражение.",
                                LotusConfig.SPREAD_INTERVAL_TICKS, 20, 2400, 140))
                        .option(intOption(
                                "Радиус сканирования карты",
                                "Радиус (блоки), в котором карта/лупа ищет известные очаги.",
                                LotusConfig.SCAN_RADIUS, 32, 512, 192))
                        .option(intOption(
                                "Перезарядка карты (тики)",
                                "Задержка между использованиями карты заражения.",
                                LotusConfig.MAP_COOLDOWN_TICKS, 20, 1200, 100))
                        .option(intOption(
                                "Радиус активности чанков",
                                "Максимальный радиус (в чанках) вокруг игроков, где идёт активное заражение/сканирование.",
                                LotusConfig.ACTIVE_CHUNK_RADIUS, 4, 32, 32))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Совместимость со Streams Reflowing"))
                                .description(OptionDescription.of(Component.literal(
                                        "Заражение сильнее следует реальному течению воды, если Streams Reflowing установлен.")))
                                .binding(true, LotusConfig.STREAMS_COMPATIBILITY::get, LotusConfig.STREAMS_COMPATIBILITY::set)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .build())
                .build()
                .generateScreen(parent);
    }

    /**
     * YACL's binding(def, getter, setter) first argument is the value "reset to default"
     * restores, not the initial displayed value (the getter already supplies that). Passing
     * backing.get() there made reset a no-op - it "reset" the field to whatever was already
     * showing. defaultValue must be the actual ForgeConfigSpec shipped default (see LotusConfig).
     */
    private static Option<Integer> intOption(String name, String description, net.minecraftforge.common.ForgeConfigSpec.IntValue backing, int min, int max, int defaultValue) {
        return Option.<Integer>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(description)))
                .binding(defaultValue, backing::get, backing::set)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(min, max))
                .build();
    }
}
