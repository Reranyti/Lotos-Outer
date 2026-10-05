package com.lotusblight.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/**
 * The thirteenth ending, started by clicking its circle in the endings menu. It takes the whole screen and shows only its own 3D picture
 * (see {@link Ending13Scene}), cut to its track; nothing of the game shows. A warning comes first; holding Esc skips the scene.
 * The picture is drawn on a thread of its own, from the player's own skin, so the game itself is never held up by it.
 */
public final class Ending13Screen extends Screen {
    private static final long SKIP_HOLD_MS = 1500;

    private final Screen parent;
    private DynamicTexture texture;
    private ResourceLocation textureId;
    private boolean started;
    private long startedAt;
    private long escDownAt = -1;
    private Thread worker;
    private volatile boolean running;
    private final int[] frame = new int[Ending13Scene.W * Ending13Scene.H];
    private volatile boolean frameReady;
    private net.minecraft.client.resources.sounds.SoundInstance music;

    public Ending13Screen(Screen parent) {
        super(Component.literal("Ending"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (texture == null) {
            texture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, Ending13Scene.W, Ending13Scene.H, false));
            textureId = this.minecraft.getTextureManager().register("lotusblight_ending13", texture);
        }
        // asking for the skin now starts its load, if it was never needed before: by the time ENTER is pressed it is there
        this.minecraft.getSkinManager().getInsecureSkinLocation(this.minecraft.getUser().getGameProfile());
    }

    @Override
    public void removed() {
        stopScene();
        if (texture != null) {
            this.minecraft.getTextureManager().release(textureId);
            texture = null;
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

    private void start() {
        started = true;
        final int[] skinPixels = readSkin();
        this.minecraft.getMusicManager().stopPlaying();
        music = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(new ResourceLocation("lotusblight", "ending13_music")), 1.0f);
        this.minecraft.getSoundManager().play(music);
        startedAt = System.currentTimeMillis();
        running = true;
        worker = new Thread(() -> {
            Ending13Scene scene = skinPixels == null ? new Ending13Scene() : new Ending13Scene(skinPixels);
            scene.setKicks(Ending13Scene.loadKicks());
            while (running) {
                double t = (System.currentTimeMillis() - startedAt) / 1000.0;
                if (t >= Ending13Scene.LENGTH) break;
                int[] argb = scene.render(t);
                synchronized (frame) {
                    System.arraycopy(argb, 0, frame, 0, frame.length);
                    frameReady = true;
                }
            }
        }, "lotusblight-ending13");
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
                this.minecraft.setScreen(parent);
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
            return;
        }
        boolean esc = InputConstants.isKeyDown(this.minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE);
        if (esc) {
            if (escDownAt < 0) escDownAt = System.currentTimeMillis();
            if (System.currentTimeMillis() - escDownAt >= SKIP_HOLD_MS) {
                this.minecraft.setScreen(parent);
                return;
            }
        } else {
            escDownAt = -1;
        }
        if ((System.currentTimeMillis() - startedAt) / 1000.0 >= Ending13Scene.LENGTH + 0.5) {
            this.minecraft.setScreen(parent);
            return;
        }
        if (frameReady) {
            NativeImage image = texture.getPixels();
            synchronized (frame) {
                for (int y = 0; y < Ending13Scene.H; y++) {
                    for (int x = 0; x < Ending13Scene.W; x++) {
                        int p = frame[y * Ending13Scene.W + x];
                        image.setPixelRGBA(x, y, (p & 0xFF00FF00) | ((p >> 16) & 0xFF) | ((p & 0xFF) << 16));      // ARGB to ABGR
                    }
                }
                frameReady = false;
            }
            texture.upload();
        }
        float scale = Math.min(this.width / (float) Ending13Scene.W, this.height / (float) Ending13Scene.H);
        int w = (int) (Ending13Scene.W * scale), h = (int) (Ending13Scene.H * scale);
        g.blit(textureId, (this.width - w) / 2, (this.height - h) / 2, w, h, 0, 0, Ending13Scene.W, Ending13Scene.H, Ending13Scene.W, Ending13Scene.H);
        if (escDownAt >= 0) {
            int bar = (int) (80 * Math.min(1f, (System.currentTimeMillis() - escDownAt) / (float) SKIP_HOLD_MS));
            g.fill(this.width / 2 - 40, this.height - 14, this.width / 2 - 40 + bar, this.height - 11, 0xC0FFFFFF);
        }
    }

    private void warning(GuiGraphics g) {
        int cx = this.width / 2;
        int y = this.height / 2 - 44;
        g.drawCenteredString(this.font, "WARNING", cx, y, 0xFFCC2020);
        g.drawCenteredString(this.font, "This scene shows self-harm and violent imagery. It lasts about three minutes.", cx, y + 20, 0xFFD0D0D0);
        g.drawCenteredString(this.font, "В этой сцене есть самоповреждение и жестокость. Длится около трёх минут.", cx, y + 34, 0xFF909090);
        g.drawCenteredString(this.font, "ENTER - start      ESC - back      hold ESC while it plays - skip", cx, y + 64, 0xFFE0E0E0);
    }
}
