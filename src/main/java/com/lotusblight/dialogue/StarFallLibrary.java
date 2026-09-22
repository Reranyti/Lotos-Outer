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

    public record StarLine(String text, boolean important) {}

    // TODO(lore): replace with the real script once written.
    public static List<StarLine> warLines() {
        return List.of(
                new StarLine("...", true)
        );
    }

    public static List<StarLine> allianceLines() {
        return List.of(
                new StarLine("Привет, игрок... Знаю, я не часто с тобой общаюсь — я хочу это исправить.", false),
                new StarLine("Лотос уже сокрушается перед нашей силой...", false),
                new StarLine("Сейчас... звёздопад... Правда здорово?", false),
                new StarLine("Игрок... на самом деле меня зовут Звёздный Свет.", true),
                new StarLine("Я знаю, почему ты сюда пришёл... Тебе пообещали выловить Красного Архитектора.", true),
                new StarLine("Я думаю, что тебе не нужно этого делать... Есть шанс, что ты можешь убить и лотос, и Красного...", false),
                new StarLine("Слушай... Я знаю, что это моя вина — лотос захватил Красного. Я был очень невнимателен к Мировому Лотосу...", true),
                new StarLine("Но я обещаю, что всё исправлю... Я начну распространять свой биом... Слушай... Ты не мог бы мне помочь?", false),
                // "(кометы начинают падать)" - stage direction for the meteor-shower visual, not a
                // spoken line. Hook a particle burst here once that effect exists.
                new StarLine("Я бы мог дать тебе кое-какой ВАЖНЫЙ ресурс, игрок...", false),
                new StarLine("Только не считай меня богом... Хончо уже пытался связаться со мной.", false)
        );
    }
}
