package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.branch.NormalBranchJar;
import com.lotusblight.branch.NormalBranchLock;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Client side of the Нормальная_ветка exit. A moment after the respawn the game "hangs": the last frame
 * is held on screen, every sound stops and input goes nowhere - all of it only looks broken, nothing is.
 * Meanwhile the exit runs as its own small Java process started from this mod's jar with the game's own
 * Java (com.lotusblight.overlay.ExitMain) - the scene over the desktop and then the fight. When that
 * process ends the game comes back.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NormalBranchFreeze {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String EXIT_MAIN = "com.lotusblight.overlay.ExitMain";
    /** Ticks of normal play after the respawn before the game "hangs", so the frame held is the new world. */
    private static final int SETTLE_TICKS = 20;

    private enum State { OFF, WAITING, FROZEN }

    private static State state = State.OFF;
    private static int settled;
    private static ResourceLocation frame;
    private static int frameW, frameH;
    private static FrozenScreen screen;
    /** Bumped for every hang, so an exit process that ends late only ever lets go of its own. */
    private static int generation;

    private NormalBranchFreeze() {}

    /** Starts the exit: waits for the respawned world to show, then freezes and starts the process. */
    public static void begin() {
        if (state != State.OFF || NormalBranchLock.isLocked()) return;
        state = State.WAITING;
        settled = 0;
    }

    public static boolean frozen() {
        return state == State.FROZEN;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || state == State.OFF) return;
        Minecraft mc = Minecraft.getInstance();
        if (state == State.WAITING) {
            // Loading screens and the death screen come and go first; count only ticks spent in the world.
            if (mc.level == null || mc.player == null || mc.screen != null || !mc.player.isAlive()) {
                settled = 0;
                return;
            }
            if (++settled >= SETTLE_TICKS) freeze(mc);
        } else if (mc.screen != screen) {
            // Something else opened or closed a screen over the hang - put the held frame back, and
            // the silence with it (closing a screen resumes all sound).
            mc.setScreen(screen);
            mc.getSoundManager().pause();
        }
    }

    /** No new sound starts while the game is "hung". */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (state == State.FROZEN) event.setSound(null);
    }

    private static void freeze(Minecraft mc) {
        NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        frameW = image.getWidth();
        frameH = image.getHeight();
        frame = mc.getTextureManager().register("normal_branch_frame", new DynamicTexture(image));
        screen = new FrozenScreen();
        state = State.FROZEN;
        int gen = ++generation;
        mc.setScreen(screen);
        mc.getSoundManager().pause();
        startExit(mc, gen);
    }

    private static void startExit(Minecraft mc, int gen) {
        String classpath = NormalBranchJar.classpath();
        if (classpath == null) {
            LOG.warn("Нормальная_ветка: can't find the mod's own classes, the exit can't start");
            reset();
            return;
        }
        List<String> cp = new ArrayList<>();
        cp.add(classpath);
        cp.addAll(NormalBranchTracks.libraries(mc));
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-cp");
        command.add(String.join(File.pathSeparator, cp));
        command.add(EXIT_MAIN);
        command.add("--jar");
        command.add(LotusBlight.MODID + "-" + NormalBranchJar.version() + ".jar");
        // The fight's tracks are decoded meanwhile; the exit waits for them when the fight starts.
        NormalBranchTracks.prepare(mc);
        command.add("--song1");
        command.add(NormalBranchTracks.song(1).toString());
        command.add("--song2");
        command.add(NormalBranchTracks.song(2).toString());
        // Honcho's track for the lesson in the second song, if this jar carries it.
        if (NormalBranchTracks.hasContact()) {
            command.add("--contact");
            command.add(NormalBranchTracks.contact().toString());
        }
        // The third song, if this jar carries it.
        if (NormalBranchTracks.hasSong3()) {
            command.add("--song3");
            command.add(NormalBranchTracks.song3().toString());
        }
        // The music of the cutscene before it.
        if (NormalBranchTracks.hasInterlude()) {
            command.add("--interlude");
            command.add(NormalBranchTracks.interlude().toString());
        }
        // The video is read straight out of the jar by the fight - no copy on disk.
        command.add("--video-resource");
        command.add("/assets/" + LotusBlight.MODID + "/overlay/finale.mp4");
        // Our temporary files go away with the process.
        command.add("--cleanup");
        // On the Chromo difficulty the first two songs are played on their own, harder maps.
        if (ChromoClient.isActive()) command.add("--chromo");
        Thread starter = new Thread(() -> {
            try {
                Process process = new ProcessBuilder(command)
                        .directory(mc.gameDirectory)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .start();
                process.onExit().thenAccept(done -> mc.execute(() -> {
                    NormalBranchTracks.cleanup();
                    release(gen);
                    if (done.exitValue() == NormalBranchReward.FINALE_EXIT_CODE) NormalBranchReward.grantHorn(mc);
                }));
            } catch (Exception e) {
                LOG.warn("Нормальная_ветка: the exit didn't start: {}", e.toString());
                mc.execute(() -> release(gen));
            }
        }, "LotusBlight exit");
        starter.setDaemon(true);
        starter.start();
    }

    /** The Java running the game itself, or the one next to it in java.home. */
    private static String javaExecutable() {
        String running = ProcessHandle.current().info().command().orElse(null);
        if (running != null && new File(running).isFile()) return running;
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        File bin = new File(System.getProperty("java.home"), "bin");
        File javaw = new File(bin, "javaw.exe");
        if (windows && javaw.isFile()) return javaw.getPath();
        return new File(bin, windows ? "java.exe" : "java").getPath();
    }

    /** Ends the hang, but only if it's still the one this exit process was started for. */
    private static void release(int gen) {
        if (gen == generation) reset();
    }

    /** Lets the game go again and drops the held frame. Also the clean-up when leaving a world. */
    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        boolean wasFrozen = state == State.FROZEN;
        state = State.OFF;
        settled = 0;
        if (mc.screen == screen && screen != null) mc.setScreen(null);
        screen = null;
        if (frame != null) {
            mc.getTextureManager().release(frame);
            frame = null;
        }
        if (wasFrozen) mc.getSoundManager().resume();
    }

    /** The "hung" game: the held frame and nothing else, swallowing every key and click. */
    private static final class FrozenScreen extends Screen {
        FrozenScreen() {
            super(Component.empty());
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (frame == null) return;
            g.blit(frame, 0, 0, width, height, 0, 0, frameW, frameH, frameW, frameH);
        }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers) {
            return true;
        }

        @Override
        public boolean charTyped(char c, int modifiers) {
            return true;
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            return true;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }
    }
}
