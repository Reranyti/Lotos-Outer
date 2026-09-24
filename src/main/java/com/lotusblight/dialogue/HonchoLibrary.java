package com.lotusblight.dialogue;

import java.util.List;

/**
 * "Позволь мне стать твоим помощником" - Honcho's own war-branch-only scene (see HonchoEntity),
 * shown the first time a RESISTANCE player interacts with him. Text is the project owner's own
 * writing, same "data shape only" split as StarFallLibrary/LotusDialogueLibrary.
 */
public final class HonchoLibrary {
    private HonchoLibrary() {}

    public static List<String> assistantLines() {
        return List.of(
                "Привет, я Хончо... Должно быть, ты в замешательстве... Ты любишь работать и организовывать? Я — да.",
                "Раньше я помню, я молился свету... Божественному, жёлтой звезде... Я думал, он меня спасёт... Я видел, как «ДВЕРЕВОЙ ОХОТНИК» ушёл в рамку жёлтого... Я помню, что он давал мне бутылёк... Я выпил, молился, и когда Дверевой охотник ушёл, я отключился, и свет погас... И я оказался здесь...",
                "Я не устал работать, но я хотел выбраться... Я получил свободу... Но кто меня будет направлять..."
        );
    }

    public static String assistantQuestion() {
        return "ПОЗВОЛЬ МНЕ СТАТЬ, ПРОШУ, ТВОИМ ПОМОЩНИКОМ, Я ЗНАЮ, ТЫ СВЯЗЫВАЛСЯ С БОЖЕСТВОМ И ГОВОРИЛ С НИМ, ПРОШУ!";
    }
}
