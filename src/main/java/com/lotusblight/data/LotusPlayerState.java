package com.lotusblight.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player flags that survive death/relog via vanilla persistent NBT
 * (the tag block that already survives respawn, e.g. used for the "keep on
 * death" advancement/recipe data).
 *
 * - dialogueBranch: which ending branch the player has committed to in
 *   conversation with the Lotus (see com.lotusblight.dialogue.LotusDialogueLibrary.Branch).
 *   Stored as an ordinal so this class doesn't need to depend on the client
 *   package. Once set away from UNDECIDED it is meant to be permanent — the
 *   dialogue screen locks the choice in and never re-offers it.
 * - fullMapVisibility: whether the player has obtained the "full visibility"
 *   modifier that reveals every known outbreak on the map/minimap/atlas at
 *   once, instead of only outbreaks they've physically discovered.
 */
public final class LotusPlayerState {

    public static final int BRANCH_UNDECIDED = 0;
    public static final int BRANCH_RESISTANCE = 1;
    public static final int BRANCH_ALLIANCE = 2;

    private static final String ROOT_TAG = "LotusBlightState";
    private static final String DIALOGUE_BRANCH_KEY = "DialogueBranch";
    private static final String FULL_MAP_VISIBILITY_KEY = "FullMapVisibility";
    private static final String INNER_VOICE_USES_KEY = "InnerVoiceUses";
    private static final String HAS_TALKED_KEY = "HasTalkedToLotus";
    private static final String RECEIVED_CLEANSING_POWDER_KEY = "ReceivedCleansingPowderGift";
    private static final String RECEIVED_GRAFTING_ROD_KEY = "ReceivedGraftingRodGift";
    /** First two berries always show a scene, no conditions attached — an introduction, not a reward. */
    public static final int INNER_VOICE_FREE_USES = 2;

    private LotusPlayerState() {
    }

    private static CompoundTag root(Player player, boolean createIfMissing) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT_TAG)) {
            if (!createIfMissing) {
                return new CompoundTag();
            }
            persistent.put(ROOT_TAG, new CompoundTag());
        }
        return persistent.getCompound(ROOT_TAG);
    }

    public static int getDialogueBranch(Player player) {
        return root(player, false).getInt(DIALOGUE_BRANCH_KEY);
    }

    /** No-op if a branch is already locked in — the choice is meant to be one-way. */
    public static boolean setDialogueBranch(Player player, int branch) {
        CompoundTag root = root(player, true);
        if (root.getInt(DIALOGUE_BRANCH_KEY) != BRANCH_UNDECIDED) {
            return false;
        }
        root.putInt(DIALOGUE_BRANCH_KEY, branch);
        player.getPersistentData().put(ROOT_TAG, root);
        return true;
    }

    /** Admin-only override for testing (see com.lotusblight.command.LotusCommands) — bypasses the one-way lock that {@link #setDialogueBranch} enforces for real dialogue choices. */
    public static void forceDialogueBranch(Player player, int branch) {
        CompoundTag root = root(player, true);
        root.putInt(DIALOGUE_BRANCH_KEY, branch);
        player.getPersistentData().put(ROOT_TAG, root);
    }

    public static boolean hasJoinedLotus(Player player) {
        return getDialogueBranch(player) == BRANCH_ALLIANCE;
    }

    public static boolean hasFullMapVisibility(Player player) {
        return root(player, false).getBoolean(FULL_MAP_VISIBILITY_KEY);
    }

    public static void setFullMapVisibility(Player player, boolean visible) {
        CompoundTag root = root(player, true);
        root.putBoolean(FULL_MAP_VISIBILITY_KEY, visible);
        player.getPersistentData().put(ROOT_TAG, root);
    }

    /** How many times eating a glowing_berry has shown an inner-voice scene. Gates the tooltip hint (0 = show it). */
    public static int getInnerVoiceUses(Player player) {
        return root(player, false).getInt(INNER_VOICE_USES_KEY);
    }

    public static boolean hasHeardInnerVoice(Player player) {
        return getInnerVoiceUses(player) > 0;
    }

    /** Berries eaten before the voice is allowed to speak at all (bug #9 - it was firing on the very first berry, before the player had even chosen a side against the lotus). */
    private static final int INNER_VOICE_SILENT_BERRIES = 2;

    /**
     * The voice is the mysterious "Неизвестный" pushing the player toward fighting the infection -
     * it makes no sense for it to speak to a player who hasn't even committed to opposing the
     * lotus yet (bug #9: "сначала тебе нужно быть против лотоса"). Silent for the first
     * {@link #INNER_VOICE_SILENT_BERRIES} berries regardless of branch, then an unconditional
     * introduction for the next {@link #INNER_VOICE_FREE_USES} berries once on the RESISTANCE
     * branch specifically. Beyond that, no scene fires on eating at all; later scenes are meant to
     * be gated behind specific story triggers (not implemented yet) rather than every berry eaten.
     */
    public static boolean canTriggerInnerVoiceFreely(Player player) {
        int uses = getInnerVoiceUses(player);
        return hasTalkedToLotus(player)
                && getDialogueBranch(player) == BRANCH_RESISTANCE
                && uses >= INNER_VOICE_SILENT_BERRIES
                && uses < INNER_VOICE_SILENT_BERRIES + INNER_VOICE_FREE_USES;
    }

    /**
     * Whether this player has ever actually opened a conversation with the Lotus (see
     * DialogueOpenedPacket, sent once from LotusDialogueScreen). Eating a glowing_berry used to
     * trigger the inner-voice scene for a player's very first two berries regardless of whether
     * they'd ever spoken to the Lotus at all - the voice was "revealing itself" before the player
     * had any context for why a voice would be speaking to them in the first place.
     */
    public static boolean hasTalkedToLotus(Player player) {
        return root(player, false).getBoolean(HAS_TALKED_KEY);
    }

    public static void setHasTalkedToLotus(Player player) {
        CompoundTag root = root(player, true);
        root.putBoolean(HAS_TALKED_KEY, true);
        player.getPersistentData().put(ROOT_TAG, root);
    }

    public static void incrementInnerVoiceUses(Player player) {
        CompoundTag root = root(player, true);
        root.putInt(INNER_VOICE_USES_KEY, root.getInt(INNER_VOICE_USES_KEY) + 1);
        player.getPersistentData().put(ROOT_TAG, root);
    }

    /**
     * The dialogue's "give me powder" / "give me a grafting rod" answers used to grant the item
     * on every single click with no gate at all - reopening the dialogue and picking the same
     * answer repeatedly was an infinite item duplication exploit. These are one-time claims.
     */
    public static boolean hasReceivedCleansingPowderGift(Player player) {
        return root(player, false).getBoolean(RECEIVED_CLEANSING_POWDER_KEY);
    }

    public static void setReceivedCleansingPowderGift(Player player) {
        CompoundTag root = root(player, true);
        root.putBoolean(RECEIVED_CLEANSING_POWDER_KEY, true);
        player.getPersistentData().put(ROOT_TAG, root);
    }

    public static boolean hasReceivedGraftingRodGift(Player player) {
        return root(player, false).getBoolean(RECEIVED_GRAFTING_ROD_KEY);
    }

    public static void setReceivedGraftingRodGift(Player player) {
        CompoundTag root = root(player, true);
        root.putBoolean(RECEIVED_GRAFTING_ROD_KEY, true);
        player.getPersistentData().put(ROOT_TAG, root);
    }

    private static final String SCIENTIST_PAGES_FOUND_KEY = "ScientistPagesFound";

    /**
     * "Постоянный дроп после получения страниц дневника" - ScientistPageItem#createRandomStack
     * picked a fully random variant every single guardian kill with no memory of what the player
     * already had, so the "rare find" reward degenerated into an endless stream of duplicates once
     * a player had already seen all 5 (or even just gotten unlucky and seen the same one twice).
     * Tracked as a bitmask (one bit per ObjectZeroPages variant index) so GuardianManager can pick
     * a variant the killer doesn't have yet, and stop dropping once they've found all of them.
     */
    public static boolean hasFoundScientistPage(Player player, int variant) {
        return (root(player, false).getInt(SCIENTIST_PAGES_FOUND_KEY) & (1 << variant)) != 0;
    }

    public static void markScientistPageFound(Player player, int variant) {
        CompoundTag root = root(player, true);
        root.putInt(SCIENTIST_PAGES_FOUND_KEY, root.getInt(SCIENTIST_PAGES_FOUND_KEY) | (1 << variant));
        player.getPersistentData().put(ROOT_TAG, root);
    }

    public static boolean hasFoundAllScientistPages(Player player, int totalVariants) {
        int mask = root(player, false).getInt(SCIENTIST_PAGES_FOUND_KEY);
        return mask == (1 << totalVariants) - 1;
    }

    private static final String TAMED_GUARDIAN_COUNT_KEY = "TamedGuardianCount";

    /** How many outbreak guardians this player has tamed (see GuardianManager) - drives the "Повелитель лотоса...или пушистых хвостов?" advancement at 13. */
    public static int getTamedGuardianCount(Player player) {
        return root(player, false).getInt(TAMED_GUARDIAN_COUNT_KEY);
    }

    /** Returns the new total. */
    public static int incrementTamedGuardianCount(Player player) {
        CompoundTag root = root(player, true);
        int newCount = root.getInt(TAMED_GUARDIAN_COUNT_KEY) + 1;
        root.putInt(TAMED_GUARDIAN_COUNT_KEY, newCount);
        player.getPersistentData().put(ROOT_TAG, root);
        return newCount;
    }

    private static final String ALLIANCE_GUARDIAN_KILLS_KEY = "AllianceGuardianKills";
    private static final String IS_TRAITOR_KEY = "IsTraitor";
    private static final String HEARD_WORLD_LOTUS_LECTURE_KEY = "HeardWorldLotusLecture";
    private static final String TRAITOR_BOSS_DEFEATED_KEY = "TraitorBossDefeated";

    /**
     * "Часть лотоса ненавидит, когда собаки ЛЮБЯТ" - an ALLIANCE player who keeps killing their
     * own side's guardians anyway is offered a permanent, deliberate switch onto the hidden
     * traitor path once this hits GuardianManager#ALLIANCE_BETRAYAL_KILL_COUNT (20). Only counted
     * while actually on ALLIANCE - see GuardianManager#onDrops.
     */
    public static int getAllianceGuardianKills(Player player) {
        return root(player, false).getInt(ALLIANCE_GUARDIAN_KILLS_KEY);
    }

    public static int incrementAllianceGuardianKills(Player player) {
        CompoundTag root = root(player, true);
        int newCount = root.getInt(ALLIANCE_GUARDIAN_KILLS_KEY) + 1;
        root.putInt(ALLIANCE_GUARDIAN_KILLS_KEY, newCount);
        player.getPersistentData().put(ROOT_TAG, root);
        return newCount;
    }

    /** The hidden third path - a RESISTANCE branch reached specifically by betraying ALLIANCE, not by choosing it directly in dialogue. */
    public static boolean isTraitor(Player player) {
        return root(player, false).getBoolean(IS_TRAITOR_KEY);
    }

    public static void setTraitor(Player player) {
        CompoundTag root = root(player, true);
        root.putBoolean(IS_TRAITOR_KEY, true);
        player.getPersistentData().put(ROOT_TAG, root);
    }

    /**
     * The literal "action → branch flips" design the user asked for: no separate confirmation
     * dialog, because the "(!) Стражи?" dialogue aside (see LotusDialogueLibrary#guardianLoreLine)
     * already warns any player who has met a guardian that killing them matters, BEFORE they can
     * rack up 20 kills by accident. By the time this fires the player was told. Overrides the
     * normal one-way lock setDialogueBranch enforces, since this is the one legitimate case of a
     * branch actually changing after being set. No-op if not currently ALLIANCE (can't betray a
     * side you were never on) or already flagged as a traitor (fires once).
     */
    public static boolean betrayAlliance(Player player) {
        if (getDialogueBranch(player) != BRANCH_ALLIANCE || isTraitor(player)) return false;
        CompoundTag root = root(player, true);
        root.putInt(DIALOGUE_BRANCH_KEY, BRANCH_RESISTANCE);
        root.putBoolean(IS_TRAITOR_KEY, true);
        player.getPersistentData().put(ROOT_TAG, root);
        return true;
    }

    public static boolean hasHeardWorldLotusLecture(Player player) {
        return root(player, false).getBoolean(HEARD_WORLD_LOTUS_LECTURE_KEY);
    }

    public static void setHeardWorldLotusLecture(Player player) {
        CompoundTag root = root(player, true);
        root.putBoolean(HEARD_WORLD_LOTUS_LECTURE_KEY, true);
        player.getPersistentData().put(ROOT_TAG, root);
    }

    public static boolean hasDefeatedTraitorBoss(Player player) {
        return root(player, false).getBoolean(TRAITOR_BOSS_DEFEATED_KEY);
    }

    public static void setDefeatedTraitorBoss(Player player) {
        CompoundTag root = root(player, true);
        root.putBoolean(TRAITOR_BOSS_DEFEATED_KEY, true);
        player.getPersistentData().put(ROOT_TAG, root);
    }

    private static final String HAS_SEEN_GUARDIAN_KEY = "HasSeenGuardian";

    /**
     * Set the first time a guardian wolf comes within sight range of this player (see
     * GuardianManager#markGuardianSeen) - unlocks the "(!) Стражи?" dialogue aside so the World
     * Lotus can warn the player not to kill her guardians BEFORE they've had a chance to, instead
     * of only reacting after 20 kills already happened.
     */
    public static boolean hasSeenGuardian(Player player) {
        return root(player, false).getBoolean(HAS_SEEN_GUARDIAN_KEY);
    }

    public static void setSeenGuardian(Player player) {
        CompoundTag root = root(player, true);
        root.putBoolean(HAS_SEEN_GUARDIAN_KEY, true);
        player.getPersistentData().put(ROOT_TAG, root);
    }

    private static final String ALLIANCE_CLEANSE_USES_KEY = "AllianceCleanseUses";

    /**
     * How many times an ALLIANCE player has used cleansing powder while on that branch (see
     * LotusEvents#onPlayerInteract). Drives an escalating response from the Lotus instead of a
     * single flag: the "(!) Лечение?" dialogue aside unlocks on the very first use (soft warning),
     * turns blunt at 18, and 20/24/30 trigger real in-world consequences - see
     * GuardianManager.ALLIANCE_CLEANSE_* thresholds for the actual effects.
     */
    public static int getAllianceCleanseUses(Player player) {
        return root(player, false).getInt(ALLIANCE_CLEANSE_USES_KEY);
    }

    public static boolean hasCleansedAsAlly(Player player) {
        return getAllianceCleanseUses(player) > 0;
    }

    /** Returns the new total. */
    public static int incrementAllianceCleanseUses(Player player) {
        CompoundTag root = root(player, true);
        int newCount = root.getInt(ALLIANCE_CLEANSE_USES_KEY) + 1;
        root.putInt(ALLIANCE_CLEANSE_USES_KEY, newCount);
        player.getPersistentData().put(ROOT_TAG, root);
        return newCount;
    }

    private static final String TRUE_LIGHT_HEARTS_EXPIRES_KEY = "TrueLightHeartsExpiresAtGameTime";

    /** Game-time tick this player's True-Light bonus absorption hearts expire at, or 0 if inactive. */
    public static long getTrueLightHeartsExpireAt(Player player) {
        return root(player, false).getLong(TRUE_LIGHT_HEARTS_EXPIRES_KEY);
    }

    public static void setTrueLightHeartsExpireAt(Player player, long gameTime) {
        CompoundTag root = root(player, true);
        root.putLong(TRUE_LIGHT_HEARTS_EXPIRES_KEY, gameTime);
        player.getPersistentData().put(ROOT_TAG, root);
    }
}
