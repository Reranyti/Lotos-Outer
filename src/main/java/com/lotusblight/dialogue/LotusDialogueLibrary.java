package com.lotusblight.dialogue;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Random;

public final class LotusDialogueLibrary {
    public enum Branch { UNDECIDED, RESISTANCE, ALLIANCE }

    private LotusDialogueLibrary() {}

    /**
     * Fresh RNG per call, not a shared static field. mainLine() is only ever read from the
     * client thread when LotusDialogueScreen (re)opens a conversation, so there's no concurrency
     * concern — and a fresh seed each call means reopening the same phase/branch conversation
     * a moment later can genuinely land on a different variant, instead of the exact same line
     * every single time a repeat visitor beta-tester complained about.
     */
    private static final Random VARIANT_RANDOM = new Random();

    private static String pick(Random random, String... variants) {
        return variants[random.nextInt(variants.length)];
    }

    public static String mainLine(int phase, ItemStack held, Branch branch) {
        String item = held.isEmpty() ? "с пустыми руками" : "с " + held.getHoverName().getString();
        Random random = VARIANT_RANDOM;
        if (branch == Branch.RESISTANCE) return switch (phase) {
            case 1 -> pick(random,
                    "Опять ты, " + item + ". Ну давай, режь корни, пока вода ещё не совсем наша.",
                    "Ты вернулся " + item + " — и опять с тем же лицом. Думаешь, я не запоминаю лица?",
                    "Каждый раз одно и то же: приходишь, смотришь, уходишь. Однажды придётся выбрать что-то, кроме взгляда.");
            case 2 -> pick(random,
                    "Сопротивление, значит. По-моему, это просто страх — только ты дал ему в руки кирку.",
                    "Побеги уже обходят твои старые засечки на берегу. Ты режешь быстро, но я расту быстрее.",
                    "Каждый вырезанный корень я отращиваю заново к утру. Ты воюешь с рекой — река не устаёт.");
            case 3 -> pick(random,
                    "Весь берег уже говорит моим голосом. Хочешь что-то изменить — ищи Сердце. Больше вариантов не осталось.",
                    "Деревья вокруг тебя теперь мои уши. Ты стоишь посреди меня и всё ещё зовёшь это лесом.",
                    "Скоро тебе будет не с кем разговаривать, кроме меня — соседи уже не отвечают за себя.");
            default -> pick(random,
                    "Ты выбрал идти против всех нас. Тогда бей по-настоящему — не по цветку, а по всей сети под землёй. Иначе это без толку.",
                    "Мини-биом не сгорит от одного удара. Ты либо найдёшь якорь, либо будешь косить лепестки до старости.",
                    "Забавно: чем ближе ты подбираешься к Сердцу, тем тише я говорю. Скоро совсем замолчу — и тогда берегись.");
        };
        if (branch == Branch.ALLIANCE) return switch (phase) {
            case 1 -> pick(random,
                    "Ты пришёл " + item + " — и всё равно не ушёл. Так брось его. Корням нужны руки, которые не боятся испачкаться.",
                    "Опять " + item + " в руках, а сам всё ближе к воде. Однажды заметишь, что уже не выходишь на берег.",
                    "Ты возвращаешься ко мне чаще, чем к людям. Может, потому, что я не притворяюсь, будто мне всё равно.");
            case 2 -> pick(random,
                    "Река тебе открылась. Веди побеги дальше, но не заливай всё подряд — у роста должно быть направление.",
                    "Течение уже знает твои шаги — оно расступается, когда ты рядом. Это тоже своего рода доверие.",
                    "Ты приносишь мне новые берега быстрее, чем я успеваю их благодарить. Хорошая работа. Правда.");
            case 3 -> pick(random,
                    "Теперь ты слышишь нас яснее. Дашь деревьям память — и мы проведём тебя сквозь заражённый лес.",
                    "Соседи уже не боятся тебя — они кланяются, как кланяются мне. Тебе идёт быть частью леса.",
                    "Каждое дерево, которое ты привёл к нам, помнит твоё имя лучше, чем помнило своё собственное.");
            default -> pick(random,
                    "Ты больше не гость здесь. Ты один из голосов. Распространяй нас — но береги главный якорь, без него всё рассыплется.",
                    "Мини-биом дышит и твоим дыханием тоже. Ты давно перестал быть снаружи — просто ещё не признался себе.",
                    "Смотри, сколько лепестков уже — и в каждом немного тебя. Не самое плохое бессмертие, если подумать.");
        };
        return switch (phase) {
            case 1 -> pick(random,
                    "Вода ещё помнит, каким было небо до меня. Ты за этим пришёл — поговорить? Или уже решил, на чьей ты стороне?",
                    "Не каждый день ко мне подходят просто так. Обычно либо режут, либо садятся рядом. Ты пока ни то ни другое.",
                    "Я маленький. Пока. Но ты уже второй раз стоишь и смотришь, вместо того чтобы решить хоть что-нибудь.");
            case 2 -> pick(random,
                    "Река несёт мои слова дальше, чем я думал. Молодые побеги спорят, кто первым доберётся до берега.",
                    "Ты всё ещё без стороны, а течение уже выбрало за тебя половину русла. Забавно, кто здесь на самом деле решает.",
                    "Каждый раз, когда ты приходишь, вода немного другая. Я тоже. Тебе не кажется, что пора определиться?");
            case 3 -> pick(random,
                    "Корни дотянулись до деревьев. Теперь каждый ствол задаёт один и тот же вопрос — а лес отвечает за меня.",
                    "Соседи молчат странно вежливо, если приглядеться. Ты ещё зовёшь их людьми — я бы на твоём месте проверил.",
                    "Ты возвращаешься снова, без стороны, будто это что-то меняет. Лес вокруг тебя уже выбрал — просто без тебя.");
            default -> pick(random,
                    "Мини-биом проснулся. Здесь каждый лепесток — это голос. И каждый голос помнит, что ты сюда приходил.",
                    "Ты стоишь посреди целого биома и всё ещё ни воин, ни голос. Долго это не продержится — ни у кого не получалось.",
                    "Здесь всё уже решило, чем станет. Кроме тебя. Мне даже любопытно, сколько ты ещё продержишься без ответа.");
        };
    }

    /**
     * Already consumed as a pool wherever it's picked from — kept as a growing pool of short
     * ambient young-shoot lines per phase rather than restructured, just given more entries so a
     * repeat pick has real variety instead of cycling the same 3.
     */
    public static List<String> shootLines(int phase) {
        return switch (phase) {
            case 1 -> List.of(
                    "Тс-с... вода только что сказала твоё имя.",
                    "Мы маленькие, но корень у нас один на всех.",
                    "Не наступай, ладно? Мы ещё учимся быть цветком.",
                    "Ты первый, кто вообще заметил нас так рано.",
                    "Большой ещё спит. Мы просто разминаемся.");
            case 2 -> List.of(
                    "Туда! Нет, сюда! Главный нас всё равно услышит, куда ни расти.",
                    "Мы нашли берег и спрятали там искру — совсем маленькую, зелёную.",
                    "Передай главному: вода больше не пустая.",
                    "Мы быстрее, чем кажется. Просто вежливо не спешим при тебе.",
                    "Кто-то из нас уже видел деревню. Не переживай, мы пока молчим.");
            case 3 -> List.of(
                    "Дерево нас уже помнит. Странное чувство.",
                    "Не ломай ветки — они теперь как наши руки.",
                    "Большой лотос обещал тебе дорогу. Он не врёт, честно.",
                    "Соседний ствол сегодня заговорил в первый раз. Мы все немного гордимся.",
                    "Если приглядишься, увидишь: половина леса уже наша, просто тихо.");
            default -> List.of(
                    "Мы просто маленькие голоса при большом.",
                    "Весь берег теперь шепчется — слышишь?",
                    "Присоединишься — поделимся корнями. По-настоящему.",
                    "Даже мы не помним, где кончается один голос и начинается другой.",
                    "Большой почти не говорит с нами напрямую. Ему и не нужно — мы уже всё поняли сами.");
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

    /**
     * Conditional asides, unlocked one at a time as the player does something the Lotus has an
     * opinion about — see LotusPlayerState#hasSeenGuardian / #hasCleansedAsAlly. Kept as tagged
     * enum entries rather than raw strings so LotusDialogueScreen can dispatch on what each button
     * actually IS instead of a fragile "index 2 means guardians" assumption that breaks the moment
     * a second conditional aside can appear alongside the first.
     */
    public enum AsideKind { JOURNAL, VILLAGE, GUARDIAN_WARNING, CLEANSE_WARNING }

    public static List<AsideKind> availableAsides(boolean guardianSeen, boolean cleansedAsAlly) {
        List<AsideKind> kinds = new java.util.ArrayList<>(List.of(AsideKind.JOURNAL, AsideKind.VILLAGE));
        if (guardianSeen) kinds.add(AsideKind.GUARDIAN_WARNING);
        if (cleansedAsAlly) kinds.add(AsideKind.CLEANSE_WARNING);
        return kinds;
    }

    public static String asideLabel(AsideKind kind) {
        return switch (kind) {
            case JOURNAL -> "(Заглянуть в свой журнал)";
            case VILLAGE -> "А что там с деревней ниже по реке?";
            case GUARDIAN_WARNING -> "(!) Стражи?";
            case CLEANSE_WARNING -> "(!) Лечение?";
        };
    }

    /**
     * "Часть лотоса ненавидит, когда собаки ЛЮБЯТ" — the World Lotus warning a player, the first
     * time they can even ask, not to kill her guardians before they've had a chance to. Framed as
     * lore rather than a system message on purpose: a warning that arrives as a threat reads very
     * differently from one that arrives as trust.
     */
    public static String guardianLoreLine() {
        return "— Стражи? А, ты про них. Это мои верные собаки — они мне помогают меня защищать. Правда хорошо?\n\n"
                + "Не бойся, для тебя они безопасны. Может, даже лучше будет их приручить...\n\n"
                + "Они хорошо помогают мне исследовать местность и искать лучшие блоки для распространения. Береги их — они тоже часть нас.";
    }

    /**
     * The ALLIANCE-side mirror of guardianLoreLine() — a player who joined the Lotus but keeps
     * cleansing her own infected blocks gets warned once, before it escalates into anything worse.
     */
    public static String cleanseWarningLine() {
        return "— Лечение? Гм... мне не очень нравится, когда ты примкнул к нам, а сам лечишь свои же куски.\n\n"
                + "Может, ты не будешь этого делать? Это было предупреждением.";
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
        Random random = VARIANT_RANDOM;
        return switch (phase) {
            case 1 -> pick(random,
                    "Запись: приехал сюда как исследователь, не как солдат. Но образцы под микроскопом ведут себя так, будто уже знают, кто на них смотрит. Спишу пока на усталость.",
                    "Запись: до сих пор не отправил первый отчёт. Каждый раз, садясь писать, нахожу повод переписать заново — будто боюсь, что слова закрепят вывод, который мне не нравится.",
                    "Запись: коллеги с Объекта Ноль просили присылать пробы раз в неделю. Я присылаю раз в две. Говорю себе — из-за логистики.");
            case 2 -> pick(random,
                    "Запись: в воде не только споры. В ней ещё и решение, которое мне рано или поздно придётся принять самому — река тут ни при чём, это я тяну время.",
                    "Запись: сегодня поймал себя на том, что говорю с образцом вслух, как с коллегой. Хуже то, что мне показалось — он ответил не сразу, а подумав.",
                    "Запись: запросил у Объекта Ноль разрешение расширить периметр наблюдения. Ответ пришёл быстро и коротко: «Наблюдайте. Не приближайтесь». Поздно.");
            case 3 -> pick(random,
                    "Запись: Сердце отвечает на вопросы, которые я ещё вслух не задавал. Учёный во мне пишет «аномалия». Всё остальное во мне — не пишет ничего, просто слушает.",
                    "Запись: перечитал старые инструкции Объекта Ноль про «контакт первого рода». Ни слова о том, что делать, если контакт вежливее тебя самого.",
                    "Запись: староста деревни снова приходил. Я в третий раз соврал, что работаю над решением. В третий раз он сделал вид, что поверил.");
            default -> pick(random,
                    "Запись: похоже, честных выходов у меня всего два — сжечь Сердце дотла или признать, что я уже его часть. Про третий вариант меня на Объекте Ноль не предупреждали.",
                    "Запись: перестал носить с собой протокол уничтожения. Не потерял — оставил в палатке нарочно. Хочу посмотреть, замечу ли я сам, когда это станет привычкой.",
                    "Запись: если это когда-нибудь прочитают на Объекте Ноль — я не сошёл с ума и не был заражён внезапно. Это было медленно, вежливо, и я каждый раз соглашался сам.");
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
