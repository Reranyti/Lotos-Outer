package com.lotusblight.client;

import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.dialogue.LotusDialogueLibrary;
import com.lotusblight.map.ClientPlayerStateCache;
import com.lotusblight.map.DialogueChoicePacket;
import com.lotusblight.map.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
        this.phase = Math.max(0, Math.min(3, phase));
        this.target = target;
        Minecraft client = Minecraft.getInstance();
        this.held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandItem().copy();
        this.branch = toBranch(ClientPlayerStateCache.dialogueBranch());
        this.branchWasPreLocked = branch != LotusDialogueLibrary.Branch.UNDECIDED;
        setConversation(branchWasPreLocked ? 1 : 0);
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

    private void rebuildButtons() {
        this.clearWidgets();
        int left = (this.width - 340) / 2;
        int top = this.height / 2 + 42;

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
        }
        if (choice == primaryAnswers.length - 1) {
            if (this.minecraft != null) this.minecraft.setScreen(null);
            return;
        }
        setConversation(line + 1);
        rebuildButtons();
    }

    private void confirmBranch() {
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
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int width = 370;
        int height = 210;
        int left = (this.width - width) / 2;
        int top = (this.height - height) / 2;
        graphics.fill(0, 0, this.width, this.height, 0x99050907);
        graphics.fill(left - 3, top - 3, left + width + 3, top + height + 3, 0xFF17251B);
        graphics.fill(left, top, left + width, top + height, 0xF20B100D);
        graphics.fill(left + 10, top + 10, left + width - 10, top + 36, 0xFF263C2B);
        graphics.drawString(this.font, Component.literal(asideOpen ? "ПОЛЕВОЙ ЖУРНАЛ" : "ГЛАВНЫЙ ЛОТОС"), left + 18, top + 18, asideOpen ? 0xFF9AD47D : 0xFFF1A9CF, false);
        graphics.drawString(this.font, Component.literal(headerRight()), left + 240, top + 18, branchColor(), false);
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

    private int branchColor() {
        return switch (branch) {
            case ALLIANCE -> 0xFF9AD47D;
            case RESISTANCE -> 0xFFE0705A;
            default -> 0xFF9AD47D;
        };
    }

    private String phaseName() {
        return switch (phase) {
            case 1 -> "Захват реки";
            case 2 -> "Захват соседей";
            case 3 -> "Мини-биом";
            default -> "Цветение";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
