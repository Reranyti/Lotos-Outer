package com.lotusblight.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The way to each ending, step by step, as the mod has it now: every event, dialogue and choice that belongs to the line, taken from
 * the code and from what the project owner has told (steps that are decided but not built yet are marked planned). A choice forks
 * into its answers; an answer that leads to another line says which. Shown on the ending's map; for now everything is open.
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

    /** One answer of a choice; {@code to} is the line it leads to (null: the answer keeps you where you are). */
    record Opt(String text, String to) {}

    /** One square of the chain. A choice has {@code options}; the chain goes on through {@code main}. */
    record Step(String title, Kind kind, boolean planned, List<Opt> options, int main) {}

    private EndingPaths() {}

    private static Step ev(String t) { return new Step(t, Kind.EVENT, false, List.of(), 0); }
    private static Step dl(String t) { return new Step(t, Kind.DIALOGUE, false, List.of(), 0); }
    private static Step sc(String t) { return new Step(t, Kind.SCENE, false, List.of(), 0); }
    private static Step bs(String t) { return new Step(t, Kind.BOSS, false, List.of(), 0); }
    private static Step plan(Kind k, String t) { return new Step(t, k, true, List.of(), 0); }
    private static Step ch(String t, int main, Opt... o) { return new Step(t, Kind.CHOICE, false, List.of(o), main); }
    private static Opt o(String text) { return new Opt(text, null); }
    private static Opt o(String text, String to) { return new Opt(text, to); }

    /** The first conversation is where Альянс and Война split off; {@code main} is the answer this line took. */
    private static List<Step> start(int main) {
        return List.of(
                ev("Спавн"),
                ev("Стартовый набор: 3 семени лотоса и вики"),
                ev("Главный лотос в воде, заражение растёт: фазы 1-4"),
                ch("Первый разговор с лотосом", main,
                        o("«Что ты вообще такое?»", "neutral"),
                        o("«Зачем тебе заражать воду?»", "neutral"),
                        o("«Я на твоей стороне.»", "alliance"),
                        o("«Я тебя уничтожу.»", "war")));
    }

    private static List<Step> join(List<Step> a, Step... b) {
        List<Step> all = new ArrayList<>(a);
        all.addAll(List.of(b));
        return all;
    }

    private static final Map<String, List<Step>> PATHS = Map.ofEntries(
            Map.entry("war", join(start(3),
                    dl("Лотос говорит с воином: четыре фазы реплик"),
                    ch("Ответ лотосу", 0, o("«Где твой настоящий якорь?»"), o("«Я очищу этот берег.»"), o("«Я найду твоё Сердце.»")),
                    ev("Стражи лотоса: он просит не убивать их; их можно приручить"),
                    ev("Достижение «Сердце ещё ждёт»: поиск якоря"),
                    ev("Золотые предметы чернеют, ягод больше нет: постоянное правило войны"),
                    sc("StarFall: заражение выросло или прошло 6 игровых дней; метеоритный дождь волнами; Звёздный Свет говорит по-дружески: назвал имя, про Красного Архитектора, просит помощи, обещает важный ресурс, упоминает Хончо"),
                    ev("После StarFall в мире появляется Хончо: один на мир"),
                    ev("Редкая встреча с Хончо на прогулке (после StarFall)"),
                    ch("Хончо: «Принять руку?»", 0, o("Да: он рад и становится помощником"), o("Нет: он спрашивает «Точно?»"), o("Нет ещё раз: он уходит из мира навсегда")),
                    ev("Хончо-помощник: молитва на коленях"),
                    ev("Побег из лаборатории: пороги заражения"),
                    plan(Kind.BOSS, "Бои войны: 12 боссов (музыка готова)"),
                    plan(Kind.BOSS, "Игрок убивает Лотос мира"),
                    plan(Kind.SCENE, "Освобождён Красный Архитектор: концовка войны"))),
            Map.entry("alliance", join(start(2),
                    dl("Лотос говорит с союзником: четыре фазы реплик"),
                    ch("Ответ лотосу", 0, o("«Что мне с этим делать?»"), o("«Как помочь корням расти?»"), o("«Хорошо. Я слушаю.»")),
                    ch("Расспросы", 0, o("(Заглянуть в свой журнал)"), o("«А что там с деревней ниже по реке?»"), o("(!) Стражи?"), o("(!) Лечение?")),
                    ev("Водный кризис деревни: стеблевая стена перекрыла реку"),
                    ev("Приручение стражей"),
                    sc("StarFall: Звёздный Свет враждебен к союзнику лотоса и просто бьёт метеоритами: 4 реплики, урон, если прятаться или есть золотое яблоко; прививочный жезл отбирается; метеорит падает в радиусе 24 блоков"),
                    ev("После StarFall в мире появляется Хончо: один на мир"),
                    ch("Судьба союзника", 0,
                            o("Остаться союзником"),
                            o("Убить 20 стражей или лечить порошком 30 раз", "traitor")),
                    plan(Kind.SCENE, "Лотос выжил: что дальше, не рассказано"),
                    plan(Kind.BOSS, "Красный Архитектор овладевает Хончо: бой и побег"))),
            Map.entry("traitor", join(start(2),
                    dl("Лотос говорит с союзником: четыре фазы реплик"),
                    ev("Приручение стражей"),
                    ev("Предупреждения: «(!) Стражи?» и «(!) Лечение?»; с 18 лечений слова лотоса резкие, с 20 и 24 эффекты, с 30 измена"),
                    ev("Измена: 20 убитых стражей или 30 лечений порошком; ветка меняется на Войну, флаг предателя"),
                    sc("«Нудная лекция» Мирового Лотоса: 4 реплики, тряска экрана; ослепление и поджог на 8 секунд (8 урона)"),
                    bs("Бой с Предателем: арена, волны (стартует после поджога)"),
                    ev("Достижения: «Часть лотоса ненавидит, когда собаки ЛЮБЯТ», «Нудная лекция», «БИТВА МИРОВ»"),
                    plan(Kind.SCENE, "Предатель дальше идёт как Война: что с ним в финале, не рассказано"))),
            Map.entry("neutral", join(start(0),
                    dl("Лотос говорит с тем, кто без стороны: четыре фазы реплик"),
                    ev("Журнал учёного: записи по фазам"),
                    ev("Вопрос о деревне"),
                    ev("Событие «Лейфайлс»: весь инвентарь становится папкой"),
                    ev("Заготовка событий нейтральной линии"),
                    plan(Kind.SCENE, "Нейтральная линия: планы есть, пока не рассказаны"))),
            Map.entry("guiding", join(start(0),
                    ev("Заготовка событий Путеводной линии"),
                    plan(Kind.SCENE, "Лунный свет: линия не написана"))),
            Map.entry("starlight", join(start(0),
                    sc("StarFall: единственное появление Звёздного Света"),
                    plan(Kind.SCENE, "Линия Звёздного Света: не написана"))),
            Map.entry("mischievous", join(start(0),
                    dl("Диалог Озорного света: лекция Мирового Лотоса"),
                    plan(Kind.SCENE, "Озорной свет: линия не написана"))),
            Map.entry("cursed", join(start(0),
                    ev("Не вставать ни на одну сторону"),
                    ev("Три утопления у главного лотоса (в радиусе 16 блоков)"),
                    ev("Три смерти от стража подряд"),
                    ev("Две смерти от удушья подряд"),
                    sc("Сцена входа: красное небо, вода, подъём, обвал мира"),
                    sc("Пустое место: мёртвое измерение"),
                    ev("Рог"),
                    bs("Бой: три песни, глитч, урок Хончо между первой и второй, перерыв, финальная анимация"),
                    ev("Побег: сцена выхода"),
                    plan(Kind.SCENE, "Концовка проклятой: единственная без смерти и удаления мира"))),
            Map.entry("true", join(start(0),
                    plan(Kind.SCENE, "Истинная линия рядом с проклятой: не написана"))),
            Map.entry("ash", join(start(0),
                    plan(Kind.SCENE, "Игра за Хончо: бег в 5 раз быстрее, с банкой звёздного света в 8; воды нельзя, нужна лодка"),
                    plan(Kind.SCENE, "Директор Лейрбаринг Компани: сам игрок; ключ-карта и его кабинет"),
                    plan(Kind.SCENE, "Хончо видит катсцену и убегает, эффект «Психически не здоров» не получает"),
                    plan(Kind.SCENE, "На экранах: игрок убивает Хончо Нойзом, потом сам: шея, сожжение"),
                    plan(Kind.SCENE, "Встреча со Старлайтом, сила, выход из лабораторий"),
                    plan(Kind.EVENT, "Шкала преданности: молитва каждые 15 игровых часов"),
                    plan(Kind.SCENE, "Шкала на нуле: Старлайт забирает вещи; второй раз: пепел и концовка"))),
            Map.entry("dismembered", join(start(0),
                    sc("StarFall, затем лаборатория"),
                    plan(Kind.SCENE, "Лаборатория превращается в лестничную клетку в пустоте"),
                    plan(Kind.SCENE, "Учёный, щупальце и мясо (Мелд)"),
                    plan(Kind.SCENE, "Хайджек вылезает из телевизора и гонится за игроком"),
                    plan(Kind.BOSS, "Крик (Скрип): босс; крест действует на него особо"),
                    plan(Kind.SCENE, "«Расчленён.»: смерть и удаление мира"))),
            Map.entry("truth", join(start(0),
                    plan(Kind.SCENE, "Раскрывает полностью сущность игрока"),
                    plan(Kind.SCENE, "Длинная катсцена на 2:56: игрок раз за разом тычет себя ножом, пытаясь сломать телевизор"),
                    plan(Kind.SCENE, "Галлюцинации преследуют игрока всю катсцену"),
                    plan(Kind.SCENE, "Тёмный подвал, пустой стул, наш телевизор из видеомагнитофона; вид от первого лица, почти не похоже на Майнкрафт"),
                    plan(Kind.SCENE, "Воспоминание 1: игрок хватает кого-то и выкручивает шею, как Крик (важно для сюжета позже)"),
                    plan(Kind.SCENE, "Воспоминание 2: номер объекта «QW-178», из него вылезает жуткое существо"),
                    plan(Kind.SCENE, "Воспоминание 3: игрок в кабинете, приходит усталый пациент, из него вылезают фразы и ломают экран"),
                    plan(Kind.SCENE, "Воспоминание 4: номер объекта, в камере заточён озорной архитектор; игрок заходит и душит его; текст на экране"),
                    plan(Kind.SCENE, "Воспоминание 5: стрёмный лес, ему что-то говорят, а сзади ждёт ОНО"),
                    plan(Kind.SCENE, "Воспоминание 6, финал: все архитекторы: «ТВОЯ ВИНА» везде"),
                    plan(Kind.SCENE, "Вены, потом он режет себя, заканчивает сердцем"),
                    plan(Kind.SCENE, "Запускается нажатием на этот круг в меню концовок"),
                    plan(Kind.SCENE, "Игрок не умирает: он попадает в лимб"),
                    plan(Kind.BOSS, "Боссфайт в лимбе: босс и правила пока не рассказаны"))),
            Map.entry("chromo", join(start(0),
                    ev("Сложность «Хромо»: пятая в выборе сложности, необратима, мобы вдвое агрессивнее"),
                    sc("Проклятая ветка на Хромо: карты песен 1 и 2, щадящие по HP; события первой песни"),
                    plan(Kind.BOSS, "Песни 3 и 4 в конце ветки"),
                    plan(Kind.SCENE, "Глитчер забирает компьютер: игры, уровни из редакторов; четыре сцены в духе игр"),
                    plan(Kind.SCENE, "Четвёртая фаза Хромо-Глитчера"))));

    static List<Step> of(String endingId) {
        List<Step> steps = endingId == null ? null : PATHS.get(endingId);
        return steps == null ? start(0) : steps;
    }
}
