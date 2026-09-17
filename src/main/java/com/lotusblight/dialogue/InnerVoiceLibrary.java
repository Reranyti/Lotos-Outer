package com.lotusblight.dialogue;

import java.util.List;

/**
 * A second, much smaller "dialogue system" — not a screen with choices like
 * LotusDialogueLibrary, but a short scene (2-3 lines, advanced with Enter)
 * that surfaces when the player eats a glowing_berry (see GlowingBerryItem).
 * Where the Lotus's voice comes from outside and invites, this one is the
 * scientist-protagonist's own voice, pushing back.
 */
public final class InnerVoiceLibrary {
    private InnerVoiceLibrary() {}

    private static final List<List<String>> SCENES = List.of(
            List.of(
                    "...это ты?",
                    "Это ты. Просто ты — без лепестков в голове.",
                    "Вспомни, зачем ты сюда пришёл. Не то, что тебе сейчас шепчут."
            ),
            List.of(
                    "Голова снова твоя. Ненадолго.",
                    "Используй это время, пока оно есть."
            ),
            List.of(
                    "Ты ещё можешь сказать «нет».",
                    "Пока ещё можешь."
            ),
            List.of(
                    "Свет режет туман.",
                    "Смотри внимательно, пока видно ясно — это ненадолго."
            ),
            List.of(
                    "Это не Объект Ноль тебя предупреждал.",
                    "Это ты сам. Помни это."
            )
    );

    public static List<String> randomScene(net.minecraft.util.RandomSource random) {
        return SCENES.get(random.nextInt(SCENES.size()));
    }
}
