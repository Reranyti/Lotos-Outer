package com.lotusblight.command;

import java.util.List;

/**
 * Full reference list of every /lotus admin/testing command, for LotusCommandBookScreen. Each
 * entry's command string is ready to run as-is (player args default to @s, position args default
 * to the sender's own position, numeric args to a sensible test value) - clicking a row in the book
 * sends it exactly like the player typed it, so permission/argument validation still happens
 * server-side the normal way.
 */
public final class LotusCommandCatalog {
    public record Entry(String command, String description) {}

    public static final List<Entry> ENTRIES = List.of(
            new Entry("/lotus outbreak spawn", "Создать очаг фазы 1 на месте игрока"),
            new Entry("/lotus outbreak spawn ~ ~ ~ 4", "Создать очаг сразу фазы 4 (мини-биом)"),
            new Entry("/lotus outbreak list", "Список всех очагов, ближайшие первыми"),
            new Entry("/lotus outbreak setphase 4", "Перевести ближайший очаг на фазу 4"),
            new Entry("/lotus outbreak heart", "Вырастить Сердце в ближайшем очаге"),
            new Entry("/lotus outbreak remove", "Удалить ближайший очаг из реестра"),

            new Entry("/lotus branch get @s", "Узнать текущую ветку диалога"),
            new Entry("/lotus branch set @s alliance", "Принудительно поставить ветку Альянс"),
            new Entry("/lotus branch set @s resistance", "Принудительно поставить ветку Война"),
            new Entry("/lotus branch set @s undecided", "Сбросить ветку диалога"),

            new Entry("/lotus map reveal @s true", "Включить полную видимость карты"),
            new Entry("/lotus map reveal @s false", "Выключить полную видимость карты"),

            new Entry("/lotus gland spawn", "Посадить мшистую железу на месте игрока"),
            new Entry("/lotus gland list", "Список всех мшистых желёз"),

            new Entry("/lotus timewarp 20", "Прогнать 20 проходов заражения без ускорения тика"),

            new Entry("/lotus chase unlock", "Разблокировать лабораторию Побега (обход 15%)"),
            new Entry("/lotus chase status", "Статус лаборатории Побега"),
            new Entry("/lotus chase tp", "Телепорт ко входу в лабораторию Побега"),

            new Entry("/lotus starfall @s war", "Запустить StarFall (сценарий войны)"),
            new Entry("/lotus starfall @s alliance", "Запустить StarFall (сценарий альянса)"),

            new Entry("/lotus world status", "Общий % заражения мира и пороги Побега/StarFall"),

            new Entry("/lotus meteorite seed", "Посадить очаг метеоритного распространения на месте игрока"),

            new Entry("/lotus blackheart get @s", "Узнать количество чёрных сердец"),
            new Entry("/lotus blackheart set @s 3", "Поставить 3 чёрных сердца (проверка эффекта)"),
            new Entry("/lotus blackheart set @s 0", "Снять чёрные сердца"),

            new Entry("/lotus border status", "Статус карантинной мировой границы"),

            new Entry("/lotus reputation get @s", "Репутация у всех фракций"),

            new Entry("/lotus compat", "Что мод правит и использует в других модах, и в каком состоянии"),

            new Entry("/lotus honcho info", "Где Хончо и что игрок уже прошёл с ним"),
            new Entry("/lotus honcho spawn", "Поставить Хончо рядом (одного на мир)"),
            new Entry("/lotus honcho tp", "Телепорт к Хончо"),
            new Entry("/lotus honcho meeting", "Сразу сыграть встречу-спотыкание (повторяемо)"),
            new Entry("/lotus honcho cancel", "Прервать текущую встречу без последствий"),
            new Entry("/lotus honcho assistant", "Показать сцену «Позволь мне стать помощником»"),
            new Entry("/lotus honcho scene offer", "Анимация: протянутая рука"),
            new Entry("/lotus honcho scene happy", "Анимация: радость"),
            new Entry("/lotus honcho scene pat", "Анимация: гладит по голове"),
            new Entry("/lotus honcho scene pray", "Анимация: молится"),
            new Entry("/lotus honcho scene none", "Анимация: обычная стойка"),
            new Entry("/lotus honcho reset player @s", "Сбросить весь прогресс игрока с Хончо"),
            new Entry("/lotus honcho reset world", "Забыть Хончо в мире (вернуть ушедшего)")
    );

    private LotusCommandCatalog() {}
}
