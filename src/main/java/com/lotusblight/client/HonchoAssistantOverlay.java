package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.dialogue.HonchoLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * "Позволь мне стать твоим помощником" - Honcho's war-branch-only scene. Same visual-novel subtitle
 * shape as StarFallOverlay/WorldLotusLectureOverlay, but ends in a Y/N choice instead of just
 * advancing - the final line is the question itself, answered with the Y/N keys rather than Enter.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HonchoAssistantOverlay {
    private static final long LINE_TIMEOUT_MS = 16000;
    private static final int NAME_COLOR = 0xFFB388FF;
    private static final int TEXT_COLOR = 0xFFEDEDED;
    private static final int HINT_COLOR = 0x90B388FF;
    private static final int BOX_COLOR = 0xC0140A1E;
    private static final int BOX_BORDER = 0xFF6B4FC9;

    private static final Deque<String> queue = new ArrayDeque<>();
    private static String activeLine;
    private static boolean onQuestion;
    private static long lineExpireAtMs;
    private static List<net.minecraft.util.FormattedCharSequence> cachedLines;
    private static int cachedLinesWidth = -1;

    private HonchoAssistantOverlay() {}

    public static void show() {
        queue.clear();
        queue.addAll(HonchoLibrary.assistantLines());
        onQuestion = false;
        advance();
    }

    private static void advance() {
        if (queue.isEmpty() && !onQuestion) {
            activeLine = HonchoLibrary.assistantQuestion();
            onQuestion = true;
        } else {
            activeLine = queue.poll();
        }
        cachedLines = null;
        lineExpireAtMs = System.currentTimeMillis() + LINE_TIMEOUT_MS;
    }

    private static boolean active() {
        return activeLine != null;
    }

    private static void answer(boolean accepted) {
        com.lotusblight.map.NetworkHandler.CHANNEL.sendToServer(new com.lotusblight.map.HonchoAssistantChoicePacket(accepted));
        activeLine = null;
        onQuestion = false;
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        if (onQuestion) {
            if (event.getKey() == GLFW.GLFW_KEY_Y) answer(true);
            else if (event.getKey() == GLFW.GLFW_KEY_N) answer(false);
            return;
        }
        if (event.getKey() == GLFW.GLFW_KEY_ENTER || event.getKey() == GLFW.GLFW_KEY_KP_ENTER) {
            advance();
        }
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (!LotusClientHooks.isOverlayPass(event)) return;
        if (active() && !onQuestion && System.currentTimeMillis() > lineExpireAtMs) {
            advance();
        }
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        GuiGraphics g = event.getGuiGraphics();
        String text = activeLine;
        int width = Math.min(380, g.guiWidth() - 24);
        int wrapWidth = width - 16;
        if (cachedLines == null || cachedLinesWidth != wrapWidth) {
            cachedLines = mc.font.split(Component.literal(text), wrapWidth);
            cachedLinesWidth = wrapWidth;
        }
        List<net.minecraft.util.FormattedCharSequence> lines = cachedLines;
        int boxHeight = 26 + lines.size() * 10;
        int left = (g.guiWidth() - width) / 2;
        int top = g.guiHeight() - boxHeight - 64;

        g.fill(left, top, left + width, top + boxHeight, BOX_COLOR);
        g.fill(left, top, left + width, top + 1, BOX_BORDER);
        g.fill(left, top + boxHeight - 1, left + width, top + boxHeight, BOX_BORDER);
        g.drawString(mc.font, "ХОНЧО", left + 8, top + 6, NAME_COLOR, true);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(mc.font, lines.get(i), left + 8, top + 18 + i * 10, TEXT_COLOR, true);
        }
        String hint = onQuestion ? "[Y] Да    [N] Нет" : "[Enter] продолжить";
        g.drawString(mc.font, hint, left + width - mc.font.width(hint) - 8, top + boxHeight - 10, HINT_COLOR, false);
    }
}
