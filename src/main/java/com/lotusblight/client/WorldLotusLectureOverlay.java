package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.dialogue.LotusDialogueLibrary;
import com.lotusblight.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * "Нудная лекция" cutscene — the World Lotus confronting a freshly-turned traitor. Same
 * visual-novel subtitle shape as {@link InnerVoiceOverlay} (queue of lines, Enter to advance), but
 * the speaker switches per line between the Lotus (Lotoniriya icon, per explicit request — "у
 * мирового лотоса будет иконка что и у лоторинии вместо головы") and the player's own scripted
 * reaction (their real skin portrait + name). Also drives the "ты будешь испепелён" ordeal itself:
 * a few seconds of Blindness (applied server-side, see GuardianManager#triggerBetrayal) paired
 * with client-side screen shake and hijacked look input, so betrayal actually feels violent instead
 * of just being a chat message.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldLotusLectureOverlay {
    private static final long LINE_TIMEOUT_MS = 12000;
    private static final int LOTUS_NAME_COLOR = 0xFFFF5555;
    private static final int PLAYER_NAME_COLOR = 0xFFFFD54F;
    private static final int TEXT_COLOR = 0xFFEDEDED;
    private static final int HINT_COLOR = 0x90FFD54F;
    private static final int BOX_COLOR = 0xC0140505;
    private static final int BOX_BORDER = 0xFFB33A3A;

    private static final ResourceLocation LOTUS_ICON = new ResourceLocation(LotusBlight.MODID, "textures/mob_effect/lotoniriya.png");

    /** "твой экран будет трясти а твоя мышка будет сходить с ума" — how long the ordeal (shake + hijacked look) lasts. Matches GuardianManager's own incineration duration. */
    public static final int CHAOS_DURATION_TICKS = 20 * 8;

    private static final Deque<LotusDialogueLibrary.LectureLine> queue = new ArrayDeque<>();
    private static LotusDialogueLibrary.LectureLine activeLine;
    private static long lineExpireAtMs;
    private static SimpleSoundInstance musicInstance;
    private static List<net.minecraft.util.FormattedCharSequence> cachedLines;
    private static int cachedLinesWidth = -1;
    private static int chaosTicksLeft;

    private WorldLotusLectureOverlay() {}

    public static void show() {
        queue.clear();
        queue.addAll(LotusDialogueLibrary.worldLotusLectureLines());
        advance();
        if (musicInstance != null) {
            Minecraft.getInstance().getSoundManager().stop(musicInstance);
        }
        musicInstance = SimpleSoundInstance.forUI(ModSounds.WORLD_LOTUS_LECTURE.get(), 1.0f, 1.0f);
        Minecraft.getInstance().getSoundManager().play(musicInstance);
        chaosTicksLeft = CHAOS_DURATION_TICKS;
    }

    private static void advance() {
        activeLine = queue.poll();
        cachedLines = null;
        lineExpireAtMs = System.currentTimeMillis() + LINE_TIMEOUT_MS;
        if (activeLine == null && musicInstance != null) {
            Minecraft.getInstance().getSoundManager().stop(musicInstance);
            musicInstance = null;
        }
    }

    private static boolean active() {
        return activeLine != null;
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (!active()) return;
        if (event.getKey() != GLFW.GLFW_KEY_ENTER && event.getKey() != GLFW.GLFW_KEY_KP_ENTER) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        advance();
    }

    /**
     * "твоя мышка будет сходить с ума" — rather than a post-process camera shake (which would look
     * shaky but leave the player's actual look direction, and therefore their control, untouched),
     * this directly jitters the real yaw/pitch each tick. That's what actually reads as the
     * player's own mouse fighting them, not just a visual flourish layered on top.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (chaosTicksLeft <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        chaosTicksLeft--;
        var random = mc.player.getRandom();
        float yawJitter = (random.nextFloat() - 0.5f) * 24.0f;
        float pitchJitter = (random.nextFloat() - 0.5f) * 12.0f;
        mc.player.setYRot(mc.player.getYRot() + yawJitter);
        mc.player.setXRot(net.minecraft.util.Mth.clamp(mc.player.getXRot() + pitchJitter, -90.0f, 90.0f));
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

        GuiGraphics g = event.getGuiGraphics();
        boolean isLotus = activeLine.speaker() == LotusDialogueLibrary.LectureSpeaker.LOTUS;
        String text = activeLine.text();

        int portrait = 28;
        int textLeftPad = portrait + 16;
        int width = Math.min(360, g.guiWidth() - 24);
        int wrapWidth = width - textLeftPad - 8;
        if (cachedLines == null || cachedLinesWidth != wrapWidth) {
            cachedLines = mc.font.split(Component.literal(text), wrapWidth);
            cachedLinesWidth = wrapWidth;
        }
        List<net.minecraft.util.FormattedCharSequence> lines = cachedLines;
        int boxHeight = Math.max(portrait + 12, 20 + lines.size() * 10);
        int left = (g.guiWidth() - width) / 2;
        int top = g.guiHeight() - boxHeight - 64;

        g.fill(left, top, left + width, top + boxHeight, BOX_COLOR);
        g.fill(left, top, left + width, top + 1, BOX_BORDER);
        g.fill(left, top + boxHeight - 1, left + width, top + boxHeight, BOX_BORDER);

        if (isLotus) {
            drawLotusIcon(g, left + 6, top + (boxHeight - portrait) / 2, portrait);
            g.drawString(mc.font, "МИРОВОЙ ЛОТОС", left + textLeftPad, top + 6, LOTUS_NAME_COLOR, true);
        } else {
            drawPortrait(g, mc, left + 6, top + (boxHeight - portrait) / 2, portrait);
            g.drawString(mc.font, mc.player.getName().getString(), left + textLeftPad, top + 6, PLAYER_NAME_COLOR, true);
        }
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(mc.font, lines.get(i), left + textLeftPad, top + 18 + i * 10, TEXT_COLOR, true);
        }
        if (!queue.isEmpty()) {
            String hint = "[Enter] продолжить";
            g.drawString(mc.font, hint, left + width - mc.font.width(hint) - 8, top + boxHeight - 10, HINT_COLOR, false);
        }
    }

    private static void drawLotusIcon(GuiGraphics g, int x, int y, int size) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        g.blit(LOTUS_ICON, x, y, size, size, 0.0f, 0.0f, 18, 18, 18, 18);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** Crops the player's own skin: base face layer (8,8)-(16,16) plus the hat overlay (40,8)-(48,16). */
    private static void drawPortrait(GuiGraphics g, Minecraft mc, int x, int y, int size) {
        if (mc.player == null) return;
        AbstractClientPlayer clientPlayer = mc.player;
        var texture = clientPlayer.getSkinTextureLocation();
        g.blit(texture, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64);
        g.blit(texture, x, y, size, size, 40.0f, 8.0f, 8, 8, 64, 64);
    }
}
