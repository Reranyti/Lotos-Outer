package com.lotusblight.client;

import java.util.List;
import java.util.Map;

/**
 * The way to each ending, step by step, as the mod has it now: every event, dialogue and choice that belongs to the line, taken from
 * the code and from what the project owner has told (steps that are decided but not built yet are marked planned). Shown on the
 * ending's map; for now everything is open. Texts of the story are not copied here, only what happens.
 */
final class EndingPaths {
    enum Kind {
        EVENT("событие", 0xFFE6C36A),
        CHOICE("выбор", 0xFF6AD0E6),
        DIALOGUE("диалог", 0xFFE4E7D8),
        BOSS("босс", 0xFFE66A6A),
        SCENE("сцена", 0xFFB98AE6);

        final String label;
        final int color;

        Kind(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    /** One square of the chain. {@code options} are the answers of a choice (shown under the square); {@code planned} = decided but not built. */
    record Step(String title, Kind kind, boolean planned, List<String> options) {}

    private EndingPaths() {}

    private static Step ev(String t) { return new Step(t, Kind.EVENT, false, List.of()); }
    private static Step dl(String t) { return new Step(t, Kind.DIALOGUE, false, List.of()); }
    private static Step sc(String t) { return new Step(t, Kind.SCENE, false, List.of()); }
    private static Step bs(String t) { return new Step(t, Kind.BOSS, false, List.of()); }
    private static Step ch(String t, String... o) { return new Step(t, Kind.CHOICE, false, List.of(o)); }
    private static Step plan(Kind k, String t) { return new Step(t, k, true, List.of()); }

    /** Everyone starts the same way. */
    private static final List<Step> START = List.of(
            ev("Спавн"),
            ev("Стартовый набор: 3 семени лотоса и вики"),
            ev("Главный лотос в воде: заражение растёт по фазам 1-4"),
            ch("Первый разговор с лотосом",
                    "«Что ты вообще такое?»", "«Зачем тебе заражать воду?»", "«Я на твоей стороне.» - Альянс", "«Я тебя уничтожу.» - Война"));

    private static List<Step> join(List<Step> a, Step... b) {
        List<Step> all = new java.util.ArrayList<>(a);
        all.addAll(List.of(b));
        return all;
    }

    private static final Map<String, List<Step>> PATHS = Map.ofEntries(
            Map.entry("war", join(START,
                    dl("Лотос говорит с «воином»: реплики по фазам 1-4"),
                    ch("Ответ лотосу", "«Где твой настоящий якорь?»", "«Я очищу этот берег.»", "«Я найду твоё Сердце.»"),
                    ev("Стражи лотоса: он предупреждает не убивать их; их можно приручить"),
                    ev("Сердце ждёт: поиск якоря (достижение)"),
                    sc("StarFall (война): Звёздный Свет, 4 реплики, урон в укрытии, золото чернеет, метеорит"),
                    ev("Редкая встреча с Хончо на прогулке (после StarFall)"),
                    ch("«Принять руку?»", "Да: Хончо рад, становится помощником", "Нет: «Точно?»", "Нет ещё раз: Хончо уходит из мира навсегда"),
                    ev("Хончо-помощник: просьба стать помощником, молитва на коленях"),
                    ev("Побег из лаборатории: пороги заражения"),
                    plan(Kind.BOSS, "Бои с боссами войны: 12 боссов (задел готов, музыка)"),
                    plan(Kind.BOSS, "Игрок убивает Лотос мира"),
                    plan(Kind.SCENE, "Освобождён Красный Архитектор - концовка войны"))),
            Map.entry("alliance", join(START,
                    dl("Лотос говорит с союзником: реплики по фазам 1-4"),
                    ch("Ответ лотосу", "«Что мне с этим делать?»", "«Как помочь корням расти?»", "«Хорошо. Я слушаю.»"),
                    ch("Расспросы", "(Заглянуть в свой журнал)", "«А что там с деревней ниже по реке?»", "(!) Стражи?", "(!) Лечение?"),
                    ev("Водный кризис деревни: стена стеблей перекрыла реку"),
                    ev("Приручение стражей; предупреждение, если лечить порошком свои блоки"),
                    sc("StarFall (альянс): Звёздный Свет называет имя, говорит про Красного Архитектора и биом"),
                    ev("Измена: порошок или убийство стражей - лотос читает «Нудную лекцию» (4 реплики)"),
                    bs("Бой с Предателем (арена, волны)"),
                    ev("Развилка: помог лотосу выжить / не смог"),
                    plan(Kind.SCENE, "Ветка 1: Лотос выжил (что дальше - не рассказано)"),
                    plan(Kind.BOSS, "Ветка 2: Красный Архитектор овладевает Хончо - бой с Хончо и побег"))),
            Map.entry("neutral", join(START,
                    dl("Лотос говорит с тем, кто без стороны: реплики по фазам 1-4"),
                    ev("Журнал учёного (записи по фазам)"),
                    ev("Водный кризис деревни: вопрос про деревню"),
                    ev("Событие «Лейфайлс»: весь инвентарь становится папкой"),
                    ev("Заготовки событий нейтральной линии (пока только сообщение)"),
                    plan(Kind.SCENE, "Нейтральная линия: у тебя есть планы на неё"))),
            Map.entry("guiding", join(START,
                    ev("Заготовка событий Путеводной линии (пока только сообщение)"),
                    plan(Kind.SCENE, "Лунный свет, Путеводная линия: не написана"))),
            Map.entry("starlight", join(START,
                    sc("StarFall: единственное появление Звёздного Света"),
                    plan(Kind.SCENE, "Линия Звёздного Света: не написана"))),
            Map.entry("mischievous", join(START,
                    dl("Диалог Озорного света (лекция Мирового Лотоса)"),
                    plan(Kind.SCENE, "Озорной свет: не написан"))),
            Map.entry("cursed", join(START,
                    ev("Без стороны: ни Альянс, ни Война"),
                    ev("Три утопления у главного лотоса (в радиусе 16)"),
                    ev("Три смерти от стража (подряд)"),
                    ev("Две смерти от удушья (подряд)"),
                    sc("Сцена входа: красное небо, вода, подъём, обвал мира"),
                    sc("Пустое место (мёртвое измерение)"),
                    ev("Рог"),
                    bs("Бой: 3 песни, глитч, урок Хончо между 1 и 2, перерыв, финальная анимация"),
                    ev("Побег с выходом из игры (сцена выхода)"),
                    plan(Kind.SCENE, "Концовка проклятой: единственная без смерти и удаления мира"))),
            Map.entry("true", join(START,
                    plan(Kind.SCENE, "Истинная линия, соседняя с проклятой: не написана"))),
            Map.entry("ash", join(START,
                    plan(Kind.SCENE, "Играем за Хончо (быстрее Раша: 5x, с банкой звёздного света 8x; в воду нельзя, нужна лодка)"),
                    plan(Kind.SCENE, "Директор Лейрбаринг Компани - сам игрок, ключ-карта и его кабинет"),
                    plan(Kind.SCENE, "Хончо видит катсцену и убегает, без «Психически не здоров»"),
                    plan(Kind.SCENE, "На экранах: игрок убивает Хончо Нойзом, потом сам - шея, сожжение"),
                    plan(Kind.SCENE, "Встреча со Старлайтом, сила, побег из лабораторий"),
                    plan(Kind.EVENT, "Шкала преданности: молитва каждые 15 игровых часов"),
                    plan(Kind.SCENE, "Шкала на минимуме: Старлайт забирает вещи; второй раз - пепел, концовка"))),
            Map.entry("dismembered", join(START,
                    sc("StarFall, потом лаборатория"),
                    plan(Kind.SCENE, "Лаборатория становится лестничной клеткой в пустоте"),
                    plan(Kind.SCENE, "Учёный, щупальце, мясо (Мелд)"),
                    plan(Kind.SCENE, "Хайджек вылезает из телевизора и гонится (побег)"),
                    plan(Kind.BOSS, "Крик (Скрип): босс; крест действует на него по-особому"),
                    plan(Kind.SCENE, "«Расчленён.»: смерть и удаление мира"))),
            Map.entry("chromo", join(START,
                    ev("Сложность «Хромо»: пятая в выборе сложности, необратима, мобы агрессивнее вдвое"),
                    sc("Проклятая ветка с Хромо: карты песен 1 и 2 (щадящие по HP), события первой песни"),
                    plan(Kind.BOSS, "Песни 3 и 4 в конце ветки"),
                    plan(Kind.SCENE, "Глитчер забирает компьютер: игры, уровни из редакторов; четыре сцены в стиле игр"),
                    plan(Kind.SCENE, "Фаза 4 Хромо-Глитчера"))));

    static List<Step> of(String endingId) {
        List<Step> steps = endingId == null ? null : PATHS.get(endingId);
        return steps == null ? START : steps;
    }
}
