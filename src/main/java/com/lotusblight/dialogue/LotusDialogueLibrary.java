package com.lotusblight.dialogue;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class LotusDialogueLibrary {
    public enum Branch { UNDECIDED, RESISTANCE, ALLIANCE }

    private LotusDialogueLibrary() {}

    public static String mainLine(int phase, ItemStack held, Branch branch) {
        String item = held.isEmpty() ? "с пустыми руками" : "с " + held.getHoverName().getString();
        if (branch == Branch.RESISTANCE) return switch (phase) {
            case 1 -> "Опять ты, " + item + ". Ну давай, режь корни, пока вода ещё не совсем наша.";
            case 2 -> "Сопротивление, значит. По-моему, это просто страх — только ты дал ему в руки кирку.";
            case 3 -> "Весь берег уже говорит моим голосом. Хочешь что-то изменить — ищи Сердце. Больше вариантов не осталось.";
            default -> "Ты выбрал идти против всех нас. Тогда бей по-настоящему — не по цветку, а по всей сети под землёй. Иначе это без толку.";
        };
        if (branch == Branch.ALLIANCE) return switch (phase) {
            case 1 -> "Ты пришёл " + item + " — и всё равно не ушёл. Так брось его. Корням нужны руки, которые не боятся испачкаться.";
            case 2 -> "Река тебе открылась. Веди побеги дальше, но не заливай всё подряд — у роста должно быть направление.";
            case 3 -> "Теперь ты слышишь нас яснее. Дашь деревьям память — и мы проведём тебя сквозь заражённый лес.";
            default -> "Ты больше не гость здесь. Ты один из голосов. Распространяй нас — но береги главный якорь, без него всё рассыплется.";
        };
        return switch (phase) {
            case 1 -> "Вода ещё помнит, каким было небо до меня. Ты за этим пришёл — поговорить? Или уже решил, на чьей ты стороне?";
            case 2 -> "Река несёт мои слова дальше, чем я думал. Молодые побеги спорят, кто первым доберётся до берега.";
            case 3 -> "Корни дотянулись до деревьев. Теперь каждый ствол задаёт один и тот же вопрос — а лес отвечает за меня.";
            default -> "Мини-биом проснулся. Здесь каждый лепесток — это голос. И каждый голос помнит, что ты сюда приходил.";
        };
    }

    public static List<String> shootLines(int phase) {
        return switch (phase) {
            case 1 -> List.of("Тс-с... вода только что сказала твоё имя.", "Мы маленькие, но корень у нас один на всех.", "Не наступай, ладно? Мы ещё учимся быть цветком.");
            case 2 -> List.of("Туда! Нет, сюда! Главный нас всё равно услышит, куда ни расти.", "Мы нашли берег и спрятали там искру — совсем маленькую, зелёную.", "Передай главному: вода больше не пустая.");
            case 3 -> List.of("Дерево нас уже помнит. Странное чувство.", "Не ломай ветки — они теперь как наши руки.", "Большой лотос обещал тебе дорогу. Он не врёт, честно.");
            default -> List.of("Мы просто маленькие голоса при большом.", "Весь берег теперь шепчется — слышишь?", "Присоединишься — поделимся корнями. По-настоящему.");
        };
    }

    /**
     * Branch-independent asides, always offered alongside the branch-specific answers:
     * checking the scientist's own journal, and asking the Lotus about the crisis
     * downstream. Neither advances the conversation line — {@link com.lotusblight.client.LotusDialogueScreen}
     * shows the returned text and re-offers the same answer set.
     */
    public static List<String> asideAnswers() {
        return List.of("(Заглянуть в свой журнал)", "А что там с деревней ниже по реке?");
    }

    public static List<String> playerAnswers(Branch branch) {
        if (branch == Branch.RESISTANCE) return List.of("Где твой настоящий якорь?", "Я очищу этот берег.", "Я найду твоё Сердце.");
        if (branch == Branch.ALLIANCE) return List.of("Что мне с этим делать?", "Как помочь корням расти?", "Хорошо. Я слушаю.");
        return List.of("Что ты вообще такое?", "Зачем тебе заражать воду?", "Я на твоей стороне.", "Я тебя уничтожу.");
    }

    /**
     * Field-journal lines — the scientist-protagonist framing from Объект Ноль.
     * Branch-independent, written like notes to self; the final phase ends with a vague
     * forward-hint at the two possible endings (destroy the Heart, or become part of it).
     */
    public static String scientistNote(int phase) {
        return switch (phase) {
            case 1 -> "Запись: приехал сюда как исследователь, не как солдат. Но образцы под микроскопом ведут себя так, будто уже знают, кто на них смотрит. Спишу пока на усталость.";
            case 2 -> "Запись: в воде не только споры. В ней ещё и решение, которое мне рано или поздно придётся принять самому — река тут ни при чём, это я тяну время.";
            case 3 -> "Запись: Сердце отвечает на вопросы, которые я ещё вслух не задавал. Учёный во мне пишет «аномалия». Всё остальное во мне — не пишет ничего, просто слушает.";
            default -> "Запись: похоже, честных выходов у меня всего два — сжечь Сердце дотла или признать, что я уже его часть. Про третий вариант меня на Объекте Ноль не предупреждали.";
        };
    }

    /**
     * The real, human-scale crisis behind the botanical horror: a village downstream
     * that the Lotus has cut off from its own water with a living wall of stems.
     */
    public static List<String> villageWaterCrisisLines() {
        return List.of(
            "Ниже по течению есть деревня. Мои стебли выросли поперёк русла сплошной стеной — колодцы у них целы, вот только вода до колодцев больше не доходит.",
            "Не надо делать вид, что это метафора. Это живые люди, которые считают вёдра, пока моя стена держит их реку в заложниках.",
            "Их староста приходил сюда не спрашивать про споры и не про Сердце. Он спрашивал одно — когда вода снова дойдёт до берега. Я ему так и не ответил."
        );
    }
}
