package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
    /** Was 0x60 (96/255) - too saturated/heavy a color for how often the box shows up. */
    private static final int VIGNETTE = 0x40FFD54F;
    /** Was 5 (a thin hard-edged border, not a vignette at all) - a real vignette needs to be a
     * large soft gradient reaching well into the screen, not a border a few pixels wide. Later
     * pulled back from 140 - that read as too heavy/intrusive for how often the box shows up. */
    private static final int VIGNETTE_THICKNESS = 60;
    /** White - the speaker's name is unknown this early in the story ("Неизвестный"); the shimmer
     * toward gold is a visual hint that it's tied to True Light, without spelling that out yet. */
    private static final int SHIMMER_FROM = 0xFFFFFFFF;

    private static final Deque<String> queue = new ArrayDeque<>();
    private static String activeText;
    private static long lineExpireAtMs;
    private static SimpleSoundInstance musicInstance;
    // font.split() result for activeText, recomputed only when the text or wrap width changes
    // instead of every frame the box is on screen (e.g. across the full LINE_TIMEOUT_MS).
    private static List<net.minecraft.util.FormattedCharSequence> cachedLines;
    private static int cachedLinesWidth = -1;

    private InnerVoiceOverlay() {}

    public static void show(List<String> scene) {
        queue.clear();
        queue.addAll(scene);
        advance();
        // Without this, a second scene starting while the first's theme was still playing (e.g.
        // eating a second free berry quickly) orphaned the old SimpleSoundInstance — its reference
        // was overwritten below with no way left to stop it, so it just kept playing underneath.
        if (musicInstance != null) {
            Minecraft.getInstance().getSoundManager().stop(musicInstance);
        }
        musicInstance = SimpleSoundInstance.forUI(ModSounds.INNER_VOICE_THEME.get(), 1.0f, 0.6f);
        Minecraft.getInstance().getSoundManager().play(musicInstance);
    }

    private static void advance() {
        activeText = queue.poll();
        cachedLines = null;
        lineExpireAtMs = System.currentTimeMillis() + LINE_TIMEOUT_MS;
        if (activeText != null) {
            InnerVoiceChatBridge.postLine(activeText);
        }
        if (activeText == null && musicInstance != null) {
            Minecraft.getInstance().getSoundManager().stop(musicInstance);
            musicInstance = null;
        }
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
        if (!LotusClientHooks.isOverlayPass(event)) return;
        if (active() && System.currentTimeMillis() > lineExpireAtMs) {
            advance();
        }
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        // The line still gets pushed into Chat Overhaul's own chat (see advance() above, which
        // runs before this point regardless) - drawing our OWN portrait/name/vignette box on top
        // of that was the same line showing twice on screen at once, stacked on top of each other
        // (and colliding with the phase-up actionbar too). With Chat Overhaul installed, the chat
        // is the one and only presentation; without it, this box is still the only one there is.
        if (ChatDialogue.active()) {
            ChatDialogue.drawAdvanceHint(event.getGuiGraphics());
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        drawVignette(g);

        int portrait = 28;
        int textLeftPad = portrait + 16;
        int width = Math.min(360, g.guiWidth() - 24);
        int wrapWidth = width - textLeftPad - 8;
        if (cachedLines == null || cachedLinesWidth != wrapWidth) {
            cachedLines = mc.font.split(Component.literal(activeText), wrapWidth);
            cachedLinesWidth = wrapWidth;
        }
        List<net.minecraft.util.FormattedCharSequence> lines = cachedLines;
        int boxHeight = Math.max(portrait + 12, 20 + lines.size() * 10);
        int left = (g.guiWidth() - width) / 2;
        int top = g.guiHeight() - boxHeight - 64;

        g.fill(left, top, left + width, top + boxHeight, BOX_COLOR);
        g.fill(left, top, left + width, top + 1, BOX_BORDER);
        g.fill(left, top + boxHeight - 1, left + width, top + boxHeight, BOX_BORDER);

        drawPortrait(g, mc, left + 6, top + (boxHeight - portrait) / 2, portrait);

        // Was the player's own username - the portrait is deliberately the player's own face
        // (it's their own inner voice, not an NPC), but labeling it with their username made the
        // line read as something the player said themselves in chat, not a voice speaking to them.
        // Renamed again to "Неизвестный" (Unknown) - the story hasn't revealed who/what this voice
        // actually is yet, and the white-to-gold shimmer hints at the True Light connection early
        // players won't consciously clock, without naming it outright.
        drawShimmerText(g, mc, "Неизвестный", left + textLeftPad, top + 6);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(mc.font, lines.get(i), left + textLeftPad, top + 18 + i * 10, TEXT_COLOR, true);
        }
        if (!queue.isEmpty()) {
            String hint = "[Enter] продолжить";
            g.drawString(mc.font, hint, left + width - mc.font.width(hint) - 8, top + boxHeight - 10, HINT_COLOR, false);
        }
    }

    /**
     * Draws text one character at a time with a traveling white-to-gold wave running across it -
     * each character's own phase is offset from its neighbours so the color visibly sweeps left
     * to right instead of the whole word pulsing in sync.
     */
    private static void drawShimmerText(GuiGraphics g, Minecraft mc, String text, int x, int y) {
        long time = System.currentTimeMillis();
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            double phase = time / 260.0 - i * 0.5;
            float t = (float) (Math.sin(phase) * 0.5 + 0.5);
            g.drawString(mc.font, ch, cx, y, lerpColor(SHIMMER_FROM, NAME_COLOR, t), true);
            cx += mc.font.width(ch);
        }
    }

    private static int lerpColor(int from, int to, float t) {
        int fa = (from >> 24) & 0xFF, fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int ta = (to >> 24) & 0xFF, tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int a = (int) (fa + (ta - fa) * t);
        int r = (int) (fr + (tr - fr) * t);
        int gg = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    /**
     * A soft golden vignette reaching well into the screen from all four edges, fading toward
     * the center - not a thin bordered frame. Capped to a fraction of the screen so it never eats
     * the whole view on a small window.
     *
     * The falloff used to be quadratic (t*t), which looks strong right at the edge and then dies
     * off fast a few pixels in - reads as a hard line, not a glow. sqrt(t) keeps it near full
     * strength for longer and lets it trail off gradually instead, which is what actually reads
     * as "soft light" rather than a stripe.
     */
    private static void drawVignette(GuiGraphics g) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        int maxAlpha = (VIGNETTE >> 24) & 0xFF;
        int rgb = VIGNETTE & 0xFFFFFF;
        int thickness = Math.min(VIGNETTE_THICKNESS, Math.min(w, h) / 3);
        for (int i = 0; i < thickness; i++) {
            float t = (thickness - i) / (float) thickness;
            int alpha = Math.round(maxAlpha * (float) Math.sqrt(t));
            if (alpha <= 0) continue;
            int color = (alpha << 24) | rgb;
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

    public static void reset() {
        queue.clear();
        activeText = null;
        cachedLines = null;
        if (musicInstance != null) {
            Minecraft.getInstance().getSoundManager().stop(musicInstance);
            musicInstance = null;
        }
    }
}
