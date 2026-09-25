package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.dialogue.StarFallLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * "StarFall" — Star Light's one-time appearance. Same visual-novel subtitle shape as
 * WorldLotusLectureOverlay/InnerVoiceOverlay, plus a big star drawn over everything (an original
 * design, not a copy of any reference image - see star_light.png's own generation). "лоровые
 * моменты что надо запомнить будут фиксировать камеру сами" — only lines flagged `important` in
 * StarFallLibrary freeze the player's look direction; the rest of the scene leaves them free to
 * move around while reading.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StarFallOverlay {
    private static final long LINE_TIMEOUT_MS = 14000;
    private static final int NAME_COLOR = 0xFFFFD54F;
    private static final int TEXT_COLOR = 0xFFEDEDED;
    private static final int HINT_COLOR = 0x90FFD54F;
    private static final int BOX_COLOR = 0xC00A0714;
    private static final int BOX_BORDER = 0xFF6B4FC9;
    private static final int SKY_DIM = 0x50120A2E;

    private static final ResourceLocation STAR_TEXTURE = new ResourceLocation(LotusBlight.MODID, "textures/misc/star_light.png");

    private static final Deque<StarFallLibrary.StarLine> queue = new ArrayDeque<>();
    private static StarFallLibrary.StarLine activeLine;
    private static long lineExpireAtMs;
    private static List<net.minecraft.util.FormattedCharSequence> cachedLines;
    private static int cachedLinesWidth = -1;
    private static float lockedYaw;
    private static float lockedPitch;
    private static boolean locking;
    private static boolean warBranch;
    private static int lineIndex;

    private StarFallOverlay() {}

    public static void show(boolean allianceBranch) {
        warBranch = !allianceBranch;
        queue.clear();
        queue.addAll(allianceBranch ? StarFallLibrary.allianceLines() : StarFallLibrary.warLines());
        lineIndex = -1;
        advance();
    }

    /** "Ты не сможешь вечно прятаться от меня." - the war script's own note: "(при попытке застроится или зайти в дом)". Only shows (and only deals its damage) if the player is actually sheltering when the scene reaches it; otherwise it's skipped entirely, straight to the next line. */
    private static final int WAR_SHELTER_LINE_INDEX = 1;

    private static void advance() {
        activeLine = queue.poll();
        lineIndex++;
        Minecraft mc = Minecraft.getInstance();

        if (activeLine != null && warBranch && lineIndex == WAR_SHELTER_LINE_INDEX && !isSheltering(mc)) {
            advance();
            return;
        }

        cachedLines = null;
        lineExpireAtMs = System.currentTimeMillis() + LINE_TIMEOUT_MS;
        if (activeLine != null && activeLine.important() && mc.player != null) {
            lockedYaw = mc.player.getYRot();
            lockedPitch = mc.player.getXRot();
            locking = true;
        } else {
            locking = false;
        }
        // Health/inventory are server-authoritative - the war script's (урон)/staff-removal/meteor
        // beats have to be applied server-side, keyed by which line the client just reached.
        if (activeLine != null && warBranch) {
            com.lotusblight.map.NetworkHandler.CHANNEL.sendToServer(new com.lotusblight.map.StarFallLineReachedPacket(lineIndex));
        }
    }

    /** "застроится" (walled in on at least 3 sides) or "зайти в дом" (roofed overhead) - either counts as hiding. */
    private static boolean isSheltering(Minecraft mc) {
        if (mc.player == null || mc.level == null) return false;
        BlockPos base = mc.player.blockPosition();
        for (int dy = 1; dy <= 4; dy++) {
            if (!mc.level.getBlockState(base.above(dy)).isAir()) return true;
        }
        int solidSides = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!mc.level.getBlockState(base.relative(dir)).isAir()) solidSides++;
        }
        return solidSides >= 3;
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

    /** "нельзя отвести взгляд" — while locking, the real look direction is pinned every tick regardless of mouse input, same technique WorldLotusLectureOverlay uses for its own camera hijack. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!locking) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.setYRot(lockedYaw);
        mc.player.setXRot(lockedPitch);
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keySprint.setDown(false);
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
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), SKY_DIM);

        int starSize = 96;
        int starX = (g.guiWidth() - starSize) / 2;
        int starY = Math.max(20, g.guiHeight() / 4 - starSize / 2);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        g.blit(STAR_TEXTURE, starX, starY, 0, 0, starSize, starSize, starSize, starSize);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();

        String text = activeLine.text();
        int width = Math.min(360, g.guiWidth() - 24);
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
        g.drawString(mc.font, "СТАРФОЛЛ", left + 8, top + 6, NAME_COLOR, true);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(mc.font, lines.get(i), left + 8, top + 18 + i * 10, TEXT_COLOR, true);
        }
        if (!queue.isEmpty()) {
            String hint = "[Enter] продолжить";
            g.drawString(mc.font, hint, left + width - mc.font.width(hint) - 8, top + boxHeight - 10, HINT_COLOR, false);
        }
    }

    public static void reset() {
        queue.clear();
        activeLine = null;
        cachedLines = null;
        locking = false;
    }
}
