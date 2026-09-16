package com.lotusblight.dialogue;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class LotusDialogueLibrary {
    public enum Branch { UNDECIDED, RESISTANCE, ALLIANCE }

    private LotusDialogueLibrary() {}

    public static String mainLine(int phase, ItemStack held, Branch branch) {
        String item = held.isEmpty() ? "пустыми руками" : held.getHoverName().getString();
        if (branch == Branch.RESISTANCE) return switch (phase) {
            case 1 -> "Ты снова пришёл с " + item + ". Значит, решил резать корни, пока они ещё помнят воду.";
            case 2 -> "Ты называешь это сопротивлением. Мы называем это страхом, которому дали кирку.";
            case 3 -> "Берег уже говорит нашим голосом. Но если найдёшь Сердце, попробуй отнять у него память.";
            default -> "Ты выбрал одиночество. Тогда свергни нас — не цветок, а всю сеть под землёй.";
        };
        if (branch == Branch.ALLIANCE) return switch (phase) {
            case 1 -> "Ты принёс " + item + ". Оставь его: корням нужна рука, которая не боится испачкаться.";
            case 2 -> "Река открылась тебе. Веди побеги дальше, но не заполняй воду вслепую — рост любит направление.";
            case 3 -> "Ты слышишь нас громче. Дай деревьям память, и мы дадим тебе путь сквозь заражённый лес.";
            default -> "Теперь ты не гость. Ты — один из голосов. Распространяй нас, но сохрани главный якорь.";
        };
        return switch (phase) {
            case 1 -> "Вода ещё помнит чистое небо. Ты пришёл говорить или уже выбрал сторону?";
            case 2 -> "Река несёт наши слова. Маленькие побеги спорят, кто первым доберётся до берега.";
            case 3 -> "Корни нашли деревья. Каждый ствол теперь задаёт вопрос, на который отвечает лес.";
            default -> "Мини-биом проснулся. Здесь каждый лепесток — это голос, а каждый голос помнит тебя.";
        };
    }

    public static List<String> shootLines(int phase) {
        return switch (phase) {
            case 1 -> List.of("Псс... вода сказала твоё имя.", "Мы маленькие, но корень у нас общий.", "Не наступай. Мы ещё учимся быть цветком.");
            case 2 -> List.of("Река туда! Нет, сюда! Главный лотос слышит нас всех.", "Мы нашли берег и спрятали там зелёную искру.", "Скажи главному, что вода не пустая.");
            case 3 -> List.of("Дерево уже помнит нас.", "Не ломай ветви — они стали нашими руками.", "Большой лотос обещал тебе новый путь.");
            default -> List.of("Мы — маленькие чебупели большого голоса.", "Весь берег теперь шепчет.", "Если присоединишься, мы поделимся корнями.");
        };
    }

    public static List<String> playerAnswers(Branch branch) {
        if (branch == Branch.RESISTANCE) return List.of("Где твой настоящий якорь?", "Я очищу этот берег.", "Я вернусь за Сердцем.");
        if (branch == Branch.ALLIANCE) return List.of("Что мне распространять?", "Как помочь корням?", "Я слышу вас.");
        return List.of("Что ты такое?", "Почему ты заражаешь воду?", "Я присоединюсь.", "Я свергну тебя.");
    }

    /**
     * Polevoy zhurnal ("field journal") lines — the scientist-protagonist framing from
     * Объект Ноль. Branch-independent, written like notes to self; the final phase ends
     * with a vague forward-hint at the two possible endings (destroy the Heart, or
     * become part of it).
     */
    public static String scientistNote(int phase) {
        return switch (phase) {
            case 1 -> "Полевой журнал: я прибыл как исследователь, не как солдат. Но образцы под микроскопом ведут себя так, будто уже знают моё имя.";
            case 2 -> "Полевой журнал: течение несёт больше, чем споры. Оно несёт решение, которое рано или поздно придётся принять — не реке, а мне.";
            case 3 -> "Полевой журнал: Сердце отвечает на вопросы, которые я ещё не задавал вслух. Учёный во мне записывает это как аномалию. Всё остальное во мне — как приглашение.";
            default -> "Полевой журнал: похоже, у меня будет только два честных выхода — сжечь Сердце дотла или признать, что я давно стал его частью. Объект Ноль не подготовил меня к третьему варианту.";
        };
    }

    /**
     * The real, human-scale crisis behind the botanical horror: a village downstream
     * that the Lotus has cut off from its own water with a living wall of stems.
     */
    public static List<String> villageWaterCrisisLines() {
        return List.of(
            "Ниже по течению стоит деревня. Стебли лотоса выросли поперёк русла плотной стеной — колодцы ещё целы, но река до них больше не доходит.",
            "Это не метафора заражения. Это настоящие люди, считающие вёдра, пока стена стеблей держит их реку в заложниках.",
            "Староста спрашивал не про споры и не про Сердце. Он спрашивал, когда вода снова дойдёт до их берега."
        );
    }
}
