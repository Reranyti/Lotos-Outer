package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LotusHudOverlay {
    private static final int SIZE = 92;
    private static final int RADIUS = 8;
    private static final int GRID = RADIUS * 2 + 1;
    private static final int UPDATE_INTERVAL = 40;
    private static final int[][] COLORS = new int[GRID][GRID];
    private static long lastScanTick = Long.MIN_VALUE;
    private static BlockPos cachedCenter = BlockPos.ZERO;
    private static int cachedFlowers;
    private static int cachedInfected;
    private static int cachedHearts;
    private static double cachedNearestSq = Double.MAX_VALUE;
    private static boolean hidden;

    public static void toggleHidden() {
        hidden = !hidden;
    }

    public static void setHidden(boolean value) {
        hidden = value;
    }

    public static boolean isHidden() {
        return hidden;
    }

    public static int getCachedFlowers() {
        return cachedFlowers;
    }

    public static int getCachedInfected() {
        return cachedInfected;
    }

    public static int getCachedHearts() {
        return cachedHearts;
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (hidden || mc.player == null || mc.level == null || mc.screen instanceof ChatScreen) return;

        long tick = mc.level.getGameTime();
        if (tick - lastScanTick >= UPDATE_INTERVAL || cachedCenter.distSqr(mc.player.blockPosition()) > 64.0) {
            scan(mc);
            lastScanTick = tick;
        }

        GuiGraphics g = event.getGuiGraphics();
        int left = g.guiWidth() - SIZE - 8;
        int top = 8;
        g.fill(left - 2, top - 2, left + SIZE + 2, top + SIZE + 28, 0xB0101618);
        g.fill(left, top, left + SIZE, top + SIZE, 0xD01C2825);
        int mapCenterX = left + SIZE / 2;
        int mapCenterY = top + SIZE / 2;
        g.fill(mapCenterX - 1, mapCenterY - 1, mapCenterX + 2, mapCenterY + 2, 0xFFFFFFFF);
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                int color = COLORS[x][z];
                if (color != 0) {
                    int px = mapCenterX + (x - RADIUS) * 5;
                    int pz = mapCenterY + (z - RADIUS) * 5;
                    g.fill(px, pz, px + 4, pz + 4, color);
                }
            }
        }
        g.drawString(mc.font, "Лотосы: " + cachedFlowers + "  Якоря: " + cachedHearts, left, top + SIZE + 3, 0xFFFFB3D7, false);
        g.drawString(mc.font, "Заражено: " + cachedInfected, left, top + SIZE + 14, 0xFF8CFF9F, false);
        if (cachedNearestSq < Double.MAX_VALUE) {
            g.drawString(mc.font, "Ближайший: " + Math.round(Math.sqrt(cachedNearestSq)) + " м", left, top + SIZE + 25, 0xFFFFD66E, false);
        }
    }

    private static void scan(Minecraft mc) {
        BlockPos center = mc.player.blockPosition();
        cachedCenter = center;
        cachedFlowers = 0;
        cachedInfected = 0;
        cachedHearts = 0;
        cachedNearestSq = Double.MAX_VALUE;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                BlockPos p = center.offset(x, 0, z);
                BlockState state = mc.level.getBlockState(p);
                int color = 0xFF35483D;
                if (state.is(ModBlocks.INFECTED_LOTUS.get())) {
                    color = 0xFFFF4F9A;
                    cachedFlowers++;
                    cachedNearestSq = Math.min(cachedNearestSq, p.distSqr(center));
                } else if (state.is(ModBlocks.INFECTED_SOIL.get()) || state.is(ModBlocks.LOTUS_ROOTS.get()) || state.is(ModBlocks.LOTUS_LOG.get()) || state.is(ModBlocks.LOTUS_LEAVES.get())) {
                    color = 0xFF53C56E;
                    cachedInfected++;
                } else if (state.is(ModBlocks.LOTUS_HEART.get())) {
                    color = 0xFFFFC1EC;
                    cachedHearts++;
                    cachedNearestSq = Math.min(cachedNearestSq, p.distSqr(center));
                } else if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                    color = 0xFF55A9C6;
                }
                COLORS[x + RADIUS][z + RADIUS] = color;
            }
        }
    }

    private LotusHudOverlay() {}
}
