package com.lotusblight.cinema;

import com.lotusblight.LotusBlight;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Plays a {@link Cutscene} full screen: a warning page first (what the scene shows, how it is made, and which way to draw it), then the
 * clip with its music. The picture is built on a thread of its own, so the game is never held up by it. Hold Esc to skip.
 * <p>
 * Two ways to draw it, chosen on the warning page and remembered: the graphics card (sharp, smooth, shadows, light and the tape's look
 * done by shaders, up to 2K) and the processor (the compatible one: slower and softer, but it needs nothing from the card).
 * A scene is given to the screen by a factory that is handed the player's own skin.
 */
public class CutsceneScreen extends Screen {
    /** What a scene says about itself on the warning page. */
    public record Info(String musicSound, String[] warning, double lengthSeconds) {}

    public interface Factory {
        Cutscene create(int[] skinPixels);
    }

    public enum Renderer { GPU, CPU }

    private static final long SKIP_HOLD_MS = 1500;
    private static final String FILE = "cutscenes.properties";

    private final Screen parent;
    private final Info info;
    private final Factory factory;

    private Renderer renderer = Renderer.GPU;
    private boolean started;
    private volatile boolean running;
    private long startedAt;
    private long escDownAt = -1;
    private Thread worker;
    private net.minecraft.client.resources.sounds.SoundInstance music;
    private String note;                                     // a line shown on the warning page, e.g. why the card could not be used

    // the processor's way
    private DynamicTexture cpuTexture;
    private ResourceLocation cpuTextureId;
    private int[] cpuFrame;
    private volatile boolean cpuFrameReady;
    private int cpuW, cpuH;

    // the card's way
    private GpuScene gpu;
    private GpuTexture gpuTexture;
    private ResourceLocation gpuTextureId;
    private volatile GpuScene.Frame readyFrame;
    private int outW, outH;

    public CutsceneScreen(Screen parent, Info info, Factory factory) {
        super(Component.literal("Cutscene"));
        this.parent = parent;
        this.info = info;
        this.factory = factory;
        this.renderer = loadChoice();
    }

    // ------------------------------------------------------------------ the choice, remembered

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(LotusBlight.MODID).resolve(FILE);
    }

    private static Renderer loadChoice() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Properties p = new Properties();
                try (InputStream in = Files.newInputStream(f)) {
                    p.load(in);
                }
                return "cpu".equalsIgnoreCase(p.getProperty("renderer")) ? Renderer.CPU : Renderer.GPU;
            }
        } catch (IOException ignored) {
        }
        return Renderer.GPU;
    }

    private static void saveChoice(Renderer r) {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Properties p = new Properties();
            p.setProperty("renderer", r == Renderer.CPU ? "cpu" : "gpu");
            try (OutputStream out = Files.newOutputStream(f)) {
                p.store(out, "How cutscenes are drawn: gpu (graphics card) or cpu (processor)");
            }
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init() {
        // asking for the skin now starts its load, if it was never needed before: by the time Start is pressed it is there
        this.minecraft.getSkinManager().getInsecureSkinLocation(this.minecraft.getUser().getGameProfile());
        if (started) return;
        int cx = this.width / 2;
        int y = this.height - 70;
        addRenderableWidget(Button.builder(Component.literal(rendererLabel()), b -> {
            renderer = renderer == Renderer.GPU ? Renderer.CPU : Renderer.GPU;
            note = null;
            saveChoice(renderer);
            b.setMessage(Component.literal(rendererLabel()));
        }).bounds(cx - 150, y, 300, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Начать / Start"), b -> start()).bounds(cx - 150, y + 24, 146, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назад / Back"), b -> onClose()).bounds(cx + 4, y + 24, 146, 20).build());
    }

    private String rendererLabel() {
        return renderer == Renderer.GPU ? "Рисовать: видеокарта (GPU), чётко и плавно" : "Рисовать: процессор (CPU), для слабых систем";
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        stopScene();
        if (cpuTexture != null) {
            this.minecraft.getTextureManager().release(cpuTextureId);
            cpuTexture = null;
        }
        if (gpuTexture != null) {
            this.minecraft.getTextureManager().release(gpuTextureId);
            gpuTexture = null;
        }
        if (gpu != null) {
            try {
                gpu.delete();
            } catch (Throwable ignored) {
            }
            gpu = null;
        }
    }

    private void stopScene() {
        running = false;
        if (music != null) {
            this.minecraft.getSoundManager().stop(music);
            music = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /** The player's own skin as 64x64 ARGB, or null when it can't be read (the scene then uses a plain stand-in). */
    private int[] readSkin() {
        try {
            ResourceLocation loc = this.minecraft.getSkinManager().getInsecureSkinLocation(this.minecraft.getUser().getGameProfile());
            AbstractTexture tex = this.minecraft.getTextureManager().getTexture(loc);
            RenderSystem.bindTexture(tex.getId());
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w != 64 || h != 64) return null;
            try (NativeImage img = new NativeImage(NativeImage.Format.RGBA, 64, 64, false)) {
                img.downloadTexture(0, false);
                int[] out = new int[64 * 64];
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        int p = img.getPixelRGBA(x, y);
                        out[y * 64 + x] = (p & 0xFF000000) | ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF);
                    }
                }
                return out;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** The graphics card's picture, handed to the game as a texture it can draw. It does not own the card's texture. */
    private static final class GpuTexture extends AbstractTexture {
        GpuTexture(int id) {
            this.id = id;
        }

        @Override
        public void load(ResourceManager manager) {
        }

        @Override
        public void releaseId() {
        }

        @Override
        public void close() {
        }
    }

    private void start() {
        if (started) return;
        started = true;
        this.clearWidgets();
        final int[] skinPixels = readSkin();
        this.minecraft.getMusicManager().stopPlaying();
        music = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(new ResourceLocation(LotusBlight.MODID, info.musicSound())), 1.0f);
        this.minecraft.getSoundManager().play(music);
        final Cutscene scene = factory.create(skinPixels);
        if (renderer == Renderer.GPU) {
            try {
                int fbH = this.minecraft.getWindow().getHeight();
                outH = fbH >= 1400 ? 1440 : fbH >= 1000 ? 1080 : 720;
                outW = outH * 16 / 9;
                gpu = new GpuScene(outW, outH);
                gpuTexture = new GpuTexture(gpu.outputTexture());
                gpuTextureId = new ResourceLocation(LotusBlight.MODID, "cutscene_gpu");
                this.minecraft.getTextureManager().register(gpuTextureId, gpuTexture);
            } catch (Throwable t) {
                renderer = Renderer.CPU;                                // the card could not do it: fall back to the processor
                gpu = null;
                note = "Видеокарта не справилась, рисуем процессором.";
            }
        }
        if (renderer == Renderer.CPU) {
            cpuW = scene.width();
            cpuH = scene.height();
            cpuFrame = new int[cpuW * cpuH];
            cpuTexture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, cpuW, cpuH, false));
            cpuTextureId = this.minecraft.getTextureManager().register("lotusblight_cutscene_cpu", cpuTexture);
        }
        startedAt = System.currentTimeMillis();
        running = true;
        final boolean onCard = renderer == Renderer.GPU;
        final int ow = outW, oh = outH;
        worker = new Thread(() -> {
            while (running) {
                double t = (System.currentTimeMillis() - startedAt) / 1000.0;
                if (t >= scene.length()) break;
                if (onCard) {
                    GpuScene.Frame f = scene.collectGpu(t, ow, oh);
                    readyFrame = f;
                    while (running && readyFrame == f) {                    // wait for the game to take it: one frame ahead, no more
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                } else {
                    int[] argb = scene.renderCpu(t);
                    synchronized (cpuFrame) {
                        System.arraycopy(argb, 0, cpuFrame, 0, cpuFrame.length);
                        cpuFrameReady = true;
                    }
                }
            }
        }, "lotusblight-cutscene");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!started) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                start();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                onClose();
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.minecraft.getMusicManager().stopPlaying();
        g.fill(0, 0, this.width, this.height, 0xFF000000);
        if (!started) {
            warning(g);
            super.render(g, mouseX, mouseY, partial);
            return;
        }
        boolean esc = InputConstants.isKeyDown(this.minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE);
        if (esc) {
            if (escDownAt < 0) escDownAt = System.currentTimeMillis();
            if (System.currentTimeMillis() - escDownAt >= SKIP_HOLD_MS) {
                onClose();
                return;
            }
        } else {
            escDownAt = -1;
        }
        if ((System.currentTimeMillis() - startedAt) / 1000.0 >= info.lengthSeconds() + 0.5) {
            onClose();
            return;
        }
        int picW, picH;
        ResourceLocation tex;
        float v0, vh;
        if (gpu != null) {
            GpuScene.Frame f = readyFrame;
            if (f != null) {
                readyFrame = null;
                GpuScene.State saved = GpuScene.saveState();
                try {
                    gpu.draw(f);
                } catch (Throwable t) {
                    // the card failed mid-way: stop drawing with it rather than crash the game
                    stopScene();
                    note = "Видеокарта не справилась.";
                    onClose();
                    return;
                } finally {
                    GpuScene.restoreState(saved);
                }
            }
            picW = outW;
            picH = outH;
            tex = gpuTextureId;
            v0 = outH;                                               // the card's texture is upside down for the game's coordinates
            vh = -outH;
        } else {
            if (cpuFrameReady) {
                NativeImage image = cpuTexture.getPixels();
                synchronized (cpuFrame) {
                    for (int y = 0; y < cpuH; y++) {
                        for (int x = 0; x < cpuW; x++) {
                            int p = cpuFrame[y * cpuW + x];
                            image.setPixelRGBA(x, y, (p & 0xFF00FF00) | ((p >> 16) & 0xFF) | ((p & 0xFF) << 16));      // ARGB to ABGR
                        }
                    }
                    cpuFrameReady = false;
                }
                cpuTexture.upload();
            }
            picW = cpuW;
            picH = cpuH;
            tex = cpuTextureId;
            v0 = 0;
            vh = cpuH;
        }
        float scale = Math.min(this.width / (float) picW, this.height / (float) picH);
        int w = (int) (picW * scale), h = (int) (picH * scale);
        g.blit(tex, (this.width - w) / 2, (this.height - h) / 2, w, h, 0, v0, picW, (int) vh, picW, picH);
        if (escDownAt >= 0) {
            int bar = (int) (80 * Math.min(1f, (System.currentTimeMillis() - escDownAt) / (float) SKIP_HOLD_MS));
            g.fill(this.width / 2 - 40, this.height - 14, this.width / 2 - 40 + bar, this.height - 11, 0xC0FFFFFF);
        }
    }

    private void warning(GuiGraphics g) {
        int cx = this.width / 2;
        int y = 22;
        g.drawCenteredString(this.font, "WARNING", cx, y, 0xFFCC2020);
        y += 16;
        for (String line : info.warning()) {
            g.drawCenteredString(this.font, line, cx, y, 0xFFD0D0D0);
            y += 12;
        }
        y += 10;
        g.drawCenteredString(this.font, "Как это сделано / How it is made", cx, y, 0xFFE0C060);
        y += 14;
        String[] how = {
                "Это не видео. Сцена строится прямо сейчас вашим компьютером: модель с вашим скином двигается по ключевым кадрам,",
                "как в Blockbench, а камера, свет и тени считаются заново в каждом кадре. Поверх накладываются эффекты:",
                "свечение, глубина резкости, лучи света, дымка и плёнка VHS. Поэтому на вас в сцене именно ваш скин.",
                "This is not a video. Your computer builds the scene live: a model wearing your skin moves on keyframes, as in",
                "Blockbench, while the camera, light and shadows are computed every frame, with glow, depth of field and a VHS look on top.",
        };
        for (String line : how) {
            g.drawCenteredString(this.font, line, cx, y, 0xFF9A9A9A);
            y += 11;
        }
        y += 8;
        g.drawCenteredString(this.font, renderer == Renderer.GPU
                ? "Видеокарта: до 2K, тени и свет шейдерами, плавно. / Graphics card: up to 2K, shader light and shadows, smooth."
                : "Процессор: запасной вариант, мягче и тяжелее. / Processor: the fallback, softer and heavier.", cx, y, 0xFF9AB8C8);
        if (note != null) g.drawCenteredString(this.font, note, cx, y + 12, 0xFFE08040);
        g.drawCenteredString(this.font, "ENTER - start      ESC - back      hold ESC while it plays - skip", cx, this.height - 90, 0xFFE0E0E0);
    }
}
