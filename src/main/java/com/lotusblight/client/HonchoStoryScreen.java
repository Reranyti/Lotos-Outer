package com.lotusblight.client;

import com.lotusblight.dialogue.HonchoLibrary;
import com.lotusblight.map.HonchoAssistantChoicePacket;
import com.lotusblight.map.HonchoStoryQuestionPacket;
import com.lotusblight.map.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Honcho's story, told inside the meeting cutscene right after he helps the player up (see
 * HonchoMeetingCutscene) - the same lines as the standalone assistant scene (HonchoAssistantOverlay),
 * but without leaving the cutscene. Enter, Space or a click moves on; on the last line he kneels and
 * prays (HonchoStoryQuestionPacket) and the plea is answered with "Да" / "Нет". Esc is off and the
 * game isn't paused, same as HonchoMeetingScreen.
 */
public final class HonchoStoryScreen extends Screen {
    private static final int BOX_WIDTH = 380;
    private static final int NAME_COLOR = 0xFFB388FF;
    private static final int TEXT_COLOR = 0xFFEDEDED;
    private static final int HINT_COLOR = 0x90B388FF;
    private static final int BOX_COLOR = 0xC0140A1E;
    private static final int BOX_BORDER = 0xFF6B4FC9;

    private final List<String> lines = HonchoLibrary.assistantLines();
    private int index;
    private boolean onQuestion;
    private List<FormattedCharSequence> wrapped;
    private int wrappedWidth = -1;

    public HonchoStoryScreen() {
        super(Component.literal("Хончо"));
        ChatDialogue.postLine(ChatDialogue.HONCHO, lines.get(0));
    }

    private String currentText() {
        return onQuestion ? HonchoLibrary.assistantQuestion() : lines.get(index);
    }

    private int boxWidth() {
        return Math.min(BOX_WIDTH, this.width - 24);
    }

    private List<FormattedCharSequence> wrapped() {
        int wrapWidth = boxWidth() - 16;
        if (wrapped == null || wrappedWidth != wrapWidth) {
            wrapped = this.font.split(Component.literal(currentText()), wrapWidth);
            wrappedWidth = wrapWidth;
        }
        return wrapped;
    }

    private int boxTop() {
        // With Chat Overhaul there's no text box - the buttons sit where its bottom edge would be.
        if (ChatDialogue.active()) return this.height - 64;
        return this.height - (26 + wrapped().size() * 10) - 64;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (!onQuestion) return;
        int left = (this.width - boxWidth()) / 2;
        int top = boxTop() - 26;
        int buttonWidth = boxWidth() / 2 - 15;
        addRenderableWidget(Button.builder(Component.literal("Нет"), b -> answer(false))
                .bounds(left + 10, top, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Да"), b -> answer(true))
                .bounds(left + boxWidth() / 2 + 5, top, buttonWidth, 20).build());
    }

    private void advance() {
        if (onQuestion) return;
        if (index + 1 < lines.size()) {
            index++;
        } else {
            onQuestion = true;
            NetworkHandler.CHANNEL.sendToServer(new HonchoStoryQuestionPacket());
        }
        ChatDialogue.postLine(ChatDialogue.HONCHO, currentText());
        wrapped = null;
        init();
    }

    private void answer(boolean accepted) {
        NetworkHandler.CHANNEL.sendToServer(new HonchoAssistantChoicePacket(accepted));
        if (this.minecraft != null) this.minecraft.setScreen(null);
        HonchoMeetingCutscene.reset();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!onQuestion && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)) {
            advance();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (!onQuestion) {
            advance();
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (ChatDialogue.active()) {
            // The HUD is hidden during the cutscene, so the story's chat is drawn from here.
            ChatDialogue.renderChat(g, partialTick);
            if (!onQuestion) ChatDialogue.drawAdvanceHint(g);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        List<FormattedCharSequence> text = wrapped();
        int width = boxWidth();
        int boxHeight = 26 + text.size() * 10;
        int left = (this.width - width) / 2;
        int top = boxTop();
        g.fill(left, top, left + width, top + boxHeight, BOX_COLOR);
        g.fill(left, top, left + width, top + 1, BOX_BORDER);
        g.fill(left, top + boxHeight - 1, left + width, top + boxHeight, BOX_BORDER);
        g.drawString(this.font, "ХОНЧО", left + 8, top + 6, NAME_COLOR, true);
        for (int i = 0; i < text.size(); i++) {
            g.drawString(this.font, text.get(i), left + 8, top + 18 + i * 10, TEXT_COLOR, true);
        }
        if (!onQuestion) {
            String hint = "[Enter] продолжить";
            g.drawString(this.font, hint, left + width - this.font.width(hint) - 8, top + boxHeight - 10, HINT_COLOR, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
