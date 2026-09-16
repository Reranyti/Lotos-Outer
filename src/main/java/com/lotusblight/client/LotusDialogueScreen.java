package com.lotusblight.client;

import com.lotusblight.dialogue.LotusDialogueLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/** Atmospheric conversation with one lotus outbreak. */
public final class LotusDialogueScreen extends Screen {
    private final int phase;
    private final BlockHitResult target;
    private final ItemStack held;
    private int line;
    private LotusDialogueLibrary.Branch branch = LotusDialogueLibrary.Branch.UNDECIDED;
    private String lotusText;
    private String[] answers;

    public LotusDialogueScreen(int phase, BlockHitResult target) {
        super(Component.literal("Разговор с лотосом"));
        this.phase = Math.max(0, Math.min(3, phase));
        this.target = target;
        Minecraft client = Minecraft.getInstance();
        this.held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandItem().copy();
        setConversation(0);
    }

    private void setConversation(int nextLine) {
        this.line = nextLine;
        lotusText = LotusDialogueLibrary.mainLine(phase, held, branch);
        answers = LotusDialogueLibrary.playerAnswers(branch).toArray(String[]::new);
        if (line > 0) {
            if (branch == LotusDialogueLibrary.Branch.ALLIANCE) {
                lotusText += "\n\n— Тогда слушай. Маленькие голоса уже выбрали для тебя первый корень.";
            } else if (branch == LotusDialogueLibrary.Branch.RESISTANCE) {
                lotusText += "\n\n— Хорошо. Сначала найди главный якорь. Не трать порошок на побеги.";
            } else {
                lotusText += "\n\n— Ответь ещё раз. Вода запоминает не слова, а выбор.";
            }
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
        for (int i = 0; i < answers.length; i++) {
            final int choice = i;
            this.addRenderableWidget(Button.builder(Component.literal(answers[i]), button -> choose(choice))
                    .bounds(left, top + i * 24, 340, 20).build());
        }
    }

    private void choose(int choice) {
        if (branch == LotusDialogueLibrary.Branch.UNDECIDED) {
            if (choice == 2) branch = LotusDialogueLibrary.Branch.ALLIANCE;
            if (choice == 3) branch = LotusDialogueLibrary.Branch.RESISTANCE;
        }
        if (choice >= 2 && branch != LotusDialogueLibrary.Branch.UNDECIDED) {
            setConversation(1);
            rebuildButtons();
            return;
        }
        if (choice == answers.length - 1) {
            if (this.minecraft != null) this.minecraft.setScreen(null);
            return;
        }
        setConversation(line + 1);
        rebuildButtons();
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
        graphics.drawString(this.font, Component.literal("ГЛАВНЫЙ ЛОТОС"), left + 18, top + 18, 0xFFF1A9CF, false);
        graphics.drawString(this.font, Component.literal("Фаза: " + phaseName()), left + 240, top + 18, 0xFF9AD47D, false);
        graphics.drawString(this.font, Component.literal("В руке: " + held.getHoverName().getString()), left + 18, top + 47, 0xFFB8C8BE, false);
        var lines = this.font.split(Component.literal(lotusText), width - 36);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(this.font, lines.get(i), left + 18, top + 62 + i * 10, 0xFFE4E7D8, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
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
