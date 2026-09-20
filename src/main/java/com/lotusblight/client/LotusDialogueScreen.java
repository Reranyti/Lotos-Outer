package com.lotusblight.client;

import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.dialogue.LotusDialogueLibrary;
import com.lotusblight.map.ClientPlayerStateCache;
import com.lotusblight.map.DialogueAnswerPacket;
import com.lotusblight.map.DialogueChoicePacket;
import com.lotusblight.map.DialogueOpenedPacket;
import com.lotusblight.map.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/** Atmospheric conversation with one lotus outbreak. */
public final class LotusDialogueScreen extends Screen {
    // Fixed positions of the UNDECIDED answer set (see LotusDialogueLibrary#playerAnswers).
    private static final int UNDECIDED_JOIN_INDEX = 2;
    private static final int UNDECIDED_RESIST_INDEX = 3;

    private final int phase;
    private final BlockHitResult target;
    private final ItemStack held;
    private int line;
    private LotusDialogueLibrary.Branch branch;
    /** True once the branch was already locked in before this screen opened (a past visit). */
    private final boolean branchWasPreLocked;
    private String lotusText;
    private String[] primaryAnswers;
    private boolean asideOpen;
    /** Non-null while showing the "this is permanent" confirmation for this pending branch. */
    private LotusDialogueLibrary.Branch pendingConfirm;

    public LotusDialogueScreen(int phase, BlockHitResult target) {
        super(Component.literal("Разговор с лотосом"));
        // Real outbreak phases are 1-4 (see InfectionPhases), but this field and
        // LotusDialogueLibrary's dialogue tiers are 0-indexed (0-3) - passing the raw 1-4 value
        // straight through used to shift every real phase one tier too advanced (a brand new
        // phase-1 "Цветение" outbreak read phase-2 "Захват реки" dialogue, etc.) and made
        // headerRight()'s phase LABEL disagree with the boss bar for the same outbreak.
        this.phase = Math.max(0, Math.min(3, phase - 1));
        this.target = target;
        Minecraft client = Minecraft.getInstance();
        this.held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandItem().copy();
        this.branch = toBranch(ClientPlayerStateCache.dialogueBranch());
        this.branchWasPreLocked = branch != LotusDialogueLibrary.Branch.UNDECIDED;
        setConversation(branchWasPreLocked ? 1 : 0);
        NetworkHandler.CHANNEL.sendToServer(new DialogueOpenedPacket());
    }

    private static LotusDialogueLibrary.Branch toBranch(int stored) {
        return switch (stored) {
            case LotusPlayerState.BRANCH_ALLIANCE -> LotusDialogueLibrary.Branch.ALLIANCE;
            case LotusPlayerState.BRANCH_RESISTANCE -> LotusDialogueLibrary.Branch.RESISTANCE;
            default -> LotusDialogueLibrary.Branch.UNDECIDED;
        };
    }

    private void setConversation(int nextLine) {
        this.line = nextLine;
        this.asideOpen = false;
        this.pendingConfirm = null;
        lotusText = LotusDialogueLibrary.mainLine(phase, held, branch);
        primaryAnswers = LotusDialogueLibrary.playerAnswers(branch).toArray(String[]::new);
        if (branchWasPreLocked) {
            lotusText = (branch == LotusDialogueLibrary.Branch.ALLIANCE
                    ? "— Ты уже сделал выбор. Я помню.\n\n"
                    : "— Твой выбор давно сделан. Назад пути нет.\n\n") + lotusText;
        } else if (line > 0) {
            lotusText += branch == LotusDialogueLibrary.Branch.ALLIANCE
                    ? "\n\n— Тогда слушай. Молодые побеги уже выбрали для тебя первый корень — иди за ними."
                    : "\n\n— Ладно. Только сначала найди главный якорь — на побеги порошок не трать, без толку.";
        }
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    /**
     * Panel width/height/position shared by render() and rebuildButtons() - they used to compute
     * their layouts completely independently (a fixed 210-tall box in render(), a button start
     * position derived from screen center in rebuildButtons() with no relation to that box height
     * at all), so any time there were enough answer rows (primary answers + aside choices, up to
     * 6 total) the buttons simply overflowed past the panel's own bottom border instead of the
     * panel growing to fit them.
     */
    private int panelRowCount() {
        return pendingConfirm != null ? 2 : primaryAnswers.length + LotusDialogueLibrary.asideAnswers().size();
    }

    private int panelHeight() {
        var lines = this.font.split(Component.literal(lotusText), 370 - 36);
        int textBlockHeight = 62 + lines.size() * 10;
        int buttonsHeight = panelRowCount() * 24 + 8;
        return Math.max(210, textBlockHeight + buttonsHeight + 14);
    }

    private void rebuildButtons() {
        this.clearWidgets();
        int width = 370;
        int height = panelHeight();
        int panelLeft = (this.width - width) / 2;
        int panelTop = (this.height - height) / 2;
        int left = panelLeft + 15;
        int top = panelTop + height - panelRowCount() * 24 - 14;

        if (pendingConfirm != null) {
            boolean joining = pendingConfirm == LotusDialogueLibrary.Branch.ALLIANCE;
            this.addRenderableWidget(Button.builder(
                    Component.literal(joining ? "Да. Я присоединяюсь — навсегда." : "Да. Я объявляю войну — навсегда."),
                    button -> confirmBranch()).bounds(left, top, 340, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Нет, я ещё подумаю."),
                    button -> { pendingConfirm = null; rebuildButtons(); }).bounds(left, top + 24, 340, 20).build());
            return;
        }

        int row = 0;
        for (int i = 0; i < primaryAnswers.length; i++, row++) {
            final int choice = i;
            this.addRenderableWidget(Button.builder(Component.literal(primaryAnswers[i]), button -> choosePrimary(choice))
                    .bounds(left, top + row * 24, 340, 20).build());
        }
        var asides = LotusDialogueLibrary.asideAnswers();
        for (int i = 0; i < asides.size(); i++, row++) {
            final int asideChoice = i;
            this.addRenderableWidget(Button.builder(Component.literal(asides.get(i)), button -> chooseAside(asideChoice))
                    .bounds(left, top + row * 24, 340, 20).build());
        }
    }

    private void choosePrimary(int choice) {
        if (branch == LotusDialogueLibrary.Branch.UNDECIDED) {
            if (choice == UNDECIDED_JOIN_INDEX) {
                pendingConfirm = LotusDialogueLibrary.Branch.ALLIANCE;
                rebuildButtons();
                return;
            }
            if (choice == UNDECIDED_RESIST_INDEX) {
                pendingConfirm = LotusDialogueLibrary.Branch.RESISTANCE;
                rebuildButtons();
                return;
            }
        } else {
            // Branch-specific answers used to just advance or close the text with no actual
            // effect - "Где твой настоящий якорь?"/"Я найду твоё Сердце" asked a real question
            // and got nothing back. Dispatch to the server so specific answers can grant a real
            // payoff (see DialogueAnswerPacket), then fall through to the normal advance/close flow.
            NetworkHandler.CHANNEL.sendToServer(new DialogueAnswerPacket(branch.ordinal(), choice));
        }
        if (choice == primaryAnswers.length - 1) {
            if (this.minecraft != null) this.minecraft.setScreen(null);
            return;
        }
        setConversation(line + 1);
        rebuildButtons();
    }

    private void confirmBranch() {
        // Guard against confirmBranch() firing a second time after the first click already
        // consumed pendingConfirm (setConversation() below resets it to null) - a stray/duplicate
        // click event on the old "Да..." button reaching here after rebuildButtons() already
        // swapped it out would otherwise set branch itself to null, crashing every subsequent
        // render (headerRight()/branchColor() switch on branch with no null case).
        if (pendingConfirm == null) return;
        branch = pendingConfirm;
        int stored = branch == LotusDialogueLibrary.Branch.ALLIANCE
                ? LotusPlayerState.BRANCH_ALLIANCE
                : LotusPlayerState.BRANCH_RESISTANCE;
        NetworkHandler.CHANNEL.sendToServer(new DialogueChoicePacket(stored));
        setConversation(1);
        rebuildButtons();
    }

    /** Shows a journal/village-crisis aside without advancing the conversation. */
    private void chooseAside(int asideChoice) {
        asideOpen = true;
        lotusText = asideChoice == 0
                ? LotusDialogueLibrary.scientistNote(phase)
                : LotusDialogueLibrary.villageWaterCrisisLines().get(line % LotusDialogueLibrary.villageWaterCrisisLines().size());
        // Every other place that changes lotusText (setConversation, confirmBranch, the "Нет, я ещё
        // подумаю" button) pairs it with rebuildButtons() right after - this was the one spot that
        // didn't. panelHeight()/rebuildButtons() both read lotusText fresh, but only render() picks
        // up the new height every frame; the buttons stay frozen at whatever position they were
        // last built at, so after an aside (which is often a different length of text) they visibly
        // drift away from the panel that just resized around them.
        rebuildButtons();
    }

    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int width = 370;
        int height = panelHeight();
        int left = (this.width - width) / 2;
        int top = (this.height - height) / 2;
        // Was one flat, uniformly-dark fill covering the whole screen - a hard rectangle with no
        // falloff. This layers vanilla's own radial vignette texture (untinted - white shader
        // color, so it stays neutral gray/black, never the jarring yellow an earlier attempt at
        // this apparently had) under a lighter flat dim, so the edges darken smoothly instead of
        // the whole screen dropping to one flat shade all at once.
        graphics.fill(0, 0, this.width, this.height, 0x66050907);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.blit(VIGNETTE, 0, 0, 0, 0, this.width, this.height, this.width, this.height);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        graphics.fill(left - 3, top - 3, left + width + 3, top + height + 3, 0xFF17251B);
        graphics.fill(left, top, left + width, top + height, 0xF20B100D);
        graphics.fill(left + 10, top + 10, left + width - 10, top + 36, 0xFF263C2B);
        int titleLeft = left + 18;
        ResourceLocation icon = asideOpen ? null : branchIcon();
        if (icon != null) {
            graphics.blit(icon, titleLeft, top + 14, 0, 0, 18, 18, 18, 18);
            titleLeft += 22;
        }
        graphics.drawString(this.font, Component.literal(asideOpen ? "ПОЛЕВОЙ ЖУРНАЛ" : "ГЛАВНЫЙ ЛОТОС"), titleLeft, top + 18, asideOpen ? 0xFF9AD47D : 0xFFF1A9CF, false);
        // Was a fixed "left + 240" offset - long combinations like "Фаза: Увядание | Ветка: Альянс"
        // only had ~120px before the panel's own right edge and overflowed past it. Right-aligning
        // against the panel's own width means it always fits, growing left instead of overflowing right.
        String headerRightText = headerRight();
        int headerRightX = left + width - 14 - this.font.width(headerRightText);
        graphics.drawString(this.font, Component.literal(headerRightText), headerRightX, top + 18, branchColor(), false);
        graphics.drawString(this.font, Component.literal("В руке: " + held.getHoverName().getString()), left + 18, top + 47, 0xFFB8C8BE, false);
        var lines = this.font.split(Component.literal(lotusText), width - 36);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(this.font, lines.get(i), left + 18, top + 62 + i * 10, 0xFFE4E7D8, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String headerRight() {
        return switch (branch) {
            case ALLIANCE -> "Фаза: " + phaseName() + " | Ветка: Альянс";
            case RESISTANCE -> "Фаза: " + phaseName() + " | Ветка: Война";
            default -> "Фаза: " + phaseName();
        };
    }

    /**
     * True Light and Lotoniriya are already documented as thematic opposites (see
     * TrueLightEffect's own class doc: "clarity instead of confusion") - RESISTANCE gets the
     * gold True Light icon, ALLIANCE gets the red Lotoniriya icon. Null (no icon) while UNDECIDED,
     * since the player hasn't actually chosen a side yet.
     */
    private ResourceLocation branchIcon() {
        return switch (branch) {
            case ALLIANCE -> new ResourceLocation("lotusblight", "textures/mob_effect/lotoniriya.png");
            case RESISTANCE -> new ResourceLocation("lotusblight", "textures/mob_effect/true_light.png");
            default -> null;
        };
    }

    private int branchColor() {
        return switch (branch) {
            case ALLIANCE -> 0xFF9AD47D;
            case RESISTANCE -> 0xFFE0705A;
            default -> 0xFF9AD47D;
        };
    }

    /** Delegates to the canonical phase names instead of keeping its own copy - the duplicate
     * here had drifted from InfectionPhases' real names ("Захват соседей"/"Мини-биом" vs the
     * boss bar's "Захват ближников"/"Лотосовый мини-биом") on top of the indexing bug above. */
    private String phaseName() {
        return com.lotusblight.spread.InfectionPhases.phaseName(phase + 1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
