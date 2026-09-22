package com.lotusblight.dialogue;

import java.util.List;

/**
 * "StarFall" — Star Light's one-time appearance, two variations per branch (war: aggressive,
 * helpless; alliance: friendly, lore-heavy). Lines are the project owner's own writing - this file
 * only holds the data shape. "лоровые моменты что надо запомнить будут фиксировать камеру сами" -
 * mark a line `important = true` to camera-lock the player while it's shown (see StarFallOverlay).
 */
public final class StarFallLibrary {
    private StarFallLibrary() {}

    /**
     * @param important camera-locks the player while shown (see StarFallOverlay)
     * @param dealsDamage server deals real damage to the player the moment this line is reached
     *                    (see StarFallLineReachedPacket) - the war script's "(урон)" beats
     */
    public record StarLine(String text, boolean important, boolean dealsDamage) {}

    /** Index of the last war line - reaching it also removes the grafting rod and ends the scene with a meteor impact (see StarFallLineReachedPacket). */
    public static final int WAR_FINAL_LINE_INDEX = 3;

    public static List<StarLine> warLines() {
        return List.of(
                new StarLine("Слушай... Я знаю, что ты пытаешься сделать.", false, false),
                // "(при попытке застроится или зайти в дом)" - the real trigger condition (player
                // fortifying/hiding) isn't wired up yet; this always shows as the scene's 2nd line
                // for now, see StarFallEvent's own TODO.
                new StarLine("Ты не сможешь вечно прятаться от меня.", false, true),
                new StarLine("Прекрати распространять, ЛОТОС.", false, false),
                // Also the war branch's permanent "golden items -> black hearts, no more berries"
                // rule (see LotusEvents/GlowingBerryItem) - not just this line's flavor text.
                new StarLine("При попытке съесть золотое яблоко... Оно не поможет.", false, true)
        );
    }

    public static List<StarLine> allianceLines() {
        return List.of(
                new StarLine("Привет, игрок... Знаю, я не часто с тобой общаюсь — я хочу это исправить.", false, false),
                new StarLine("Лотос уже сокрушается перед нашей силой...", false, false),
                new StarLine("Сейчас... звёздопад... Правда здорово?", false, false),
                new StarLine("Игрок... на самом деле меня зовут Звёздный Свет.", true, false),
                new StarLine("Я знаю, почему ты сюда пришёл... Тебе пообещали выловить Красного Архитектора.", true, false),
                new StarLine("Я думаю, что тебе не нужно этого делать... Есть шанс, что ты можешь убить и лотос, и Красного...", false, false),
                new StarLine("Слушай... Я знаю, что это моя вина — лотос захватил Красного. Я был очень невнимателен к Мировому Лотосу...", true, false),
                new StarLine("Но я обещаю, что всё исправлю... Я начну распространять свой биом... Слушай... Ты не мог бы мне помочь?", false, false),
                // "(кометы начинают падать)" - stage direction for the meteor-shower visual, not a
                // spoken line. Hook a particle burst here once that effect exists.
                new StarLine("Я бы мог дать тебе кое-какой ВАЖНЫЙ ресурс, игрок...", false, false),
                new StarLine("Только не считай меня богом... Хончо уже пытался связаться со мной.", false, false)
        );
    }
}
