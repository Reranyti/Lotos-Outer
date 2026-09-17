package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
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
 * The "inner voice" subtitle box — a non-blocking portrait+name+text overlay
 * (visual-novel subtitle style, not a modal Screen like LotusDialogueScreen)
 * shown after eating a glowing_berry. A short scene (2-3 lines, see
 * InnerVoiceLibrary) is queued and advanced one line at a time with Enter,
 * same as the game's own vanilla sign-editing "confirm" key.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InnerVoiceOverlay {
    /** Fallback auto-advance so a scene can't get stuck forever if the player never presses Enter. */
    private static final long LINE_TIMEOUT_MS = 12000;
    private static final int NAME_COLOR = 0xFFFFD54F;
    private static final int TEXT_COLOR = 0xFFEDEDED;
    private static final int HINT_COLOR = 0x90FFD54F;
    private static final int BOX_COLOR = 0xC0141414;
    private static final int BOX_BORDER = 0xFFFFD54F;
    private static final int VIGNETTE = 0x50FFD54F;
    private static final int VIGNETTE_THICKNESS = 5;

    private static final Deque<String> queue = new ArrayDeque<>();
    private static String activeText;
    private static long lineExpireAtMs;

    private InnerVoiceOverlay() {}

    public static void show(List<String> scene) {
        queue.clear();
        queue.addAll(scene);
        advance();
    }

    private static void advance() {
        activeText = queue.poll();
        lineExpireAtMs = System.currentTimeMillis() + LINE_TIMEOUT_MS;
    }

    private static boolean active() {
        return activeText != null;
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (!active()) return;
        if (event.getKey() != GLFW.GLFW_KEY_ENTER && event.getKey() != GLFW.GLFW_KEY_KP_ENTER) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return; // don't steal Enter from an actual open GUI (inventory, chat, etc.)
        advance();
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (active() && System.currentTimeMillis() > lineExpireAtMs) {
            advance();
        }
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        GuiGraphics g = event.getGuiGraphics();
        drawVignette(g);

        int portrait = 28;
        int textLeftPad = portrait + 16;
        int width = Math.min(360, g.guiWidth() - 24);
        List<net.minecraft.util.FormattedCharSequence> lines = mc.font.split(Component.literal(activeText), width - textLeftPad - 8);
        int boxHeight = Math.max(portrait + 12, 20 + lines.size() * 10);
        int left = (g.guiWidth() - width) / 2;
        int top = g.guiHeight() - boxHeight - 64;

        g.fill(left, top, left + width, top + boxHeight, BOX_COLOR);
        g.fill(left, top, left + width, top + 1, BOX_BORDER);
        g.fill(left, top + boxHeight - 1, left + width, top + boxHeight, BOX_BORDER);

        drawPortrait(g, mc, left + 6, top + (boxHeight - portrait) / 2, portrait);

        String name = "[" + mc.player.getGameProfile().getName() + "]";
        g.drawString(mc.font, name, left + textLeftPad, top + 6, NAME_COLOR, true);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(mc.font, lines.get(i), left + textLeftPad, top + 18 + i * 10, TEXT_COLOR, true);
        }
        if (!queue.isEmpty()) {
            String hint = "[Enter] продолжить";
            g.drawString(mc.font, hint, left + width - mc.font.width(hint) - 8, top + boxHeight - 10, HINT_COLOR, false);
        }
    }

    /** A thin translucent gold border along all four screen edges, gradient-faded inward. */
    private static void drawVignette(GuiGraphics g) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        for (int i = 0; i < VIGNETTE_THICKNESS; i++) {
            int alpha = ((VIGNETTE >> 24) & 0xFF) * (VIGNETTE_THICKNESS - i) / VIGNETTE_THICKNESS;
            int color = (alpha << 24) | (VIGNETTE & 0xFFFFFF);
            g.fill(i, i, w - i, i + 1, color);
            g.fill(i, h - i - 1, w - i, h - i, color);
            g.fill(i, i, i + 1, h - i, color);
            g.fill(w - i - 1, i, w - i, h - i, color);
        }
    }

    /** Crops the player's own skin: base face layer (8,8)-(16,16) plus the hat overlay (40,8)-(48,16), any skin, any name. */
    private static void drawPortrait(GuiGraphics g, Minecraft mc, int x, int y, int size) {
        if (mc.player == null) return;
        AbstractClientPlayer clientPlayer = mc.player;
        var texture = clientPlayer.getSkinTextureLocation();
        g.blit(texture, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64);
        g.blit(texture, x, y, size, size, 40.0f, 8.0f, 8, 8, 64, 64);
    }
}
