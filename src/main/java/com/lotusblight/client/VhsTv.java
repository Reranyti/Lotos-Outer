package com.lotusblight.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.Random;

/**
 * The old TV and the VCR under it that the endings menu is shown on. The menu opens with a cassette sliding into the deck, the set
 * switching on (a line of light that opens into the picture), and from then on everything is drawn inside the glass, with scanlines,
 * a crawling tracking band, snow and the tape's marks over it. Content is drawn between {@link #beginScreen} and {@link #endScreen}.
 */
final class VhsTv {
    private static final long INSERT_MS = 2600;      // the cassette goes in (the recording of it is about that long)
    private static final long POWER_MS = 500;        // the picture opens from a line
    private static final int BODY = 0xFF2A2420;
    private static final int BODY_LIGHT = 0xFF4A4038;
    private static final int BODY_DARK = 0xFF14100E;

    /** The glass: where the picture is. */
    int sx, sy, sw, sh;
    private int tvX, tvY, tvW, tvH;
    private int vcrX, vcrY, vcrW, vcrH;

    private final long openedAt = Util.getMillis();
    private boolean skipped;
    private boolean insertSoundPlayed;
    private long ejectAt = -1;                       // the cassette is being taken out (the menu is closing)
    private boolean ejectSkipped;
    private static final long EJECT_MS = 3500;       // as long as the rewind recording
    private static final long EJECT_OUT_MS = 1200;   // the last stretch: the cassette comes out of the slot
    private final boolean intro;

    VhsTv(boolean intro) {
        this.intro = intro;
    }

    void layout(int w, int h) {
        tvW = (int) (w * 0.94);
        tvH = (int) (h * 0.84);
        tvX = (w - tvW) / 2;
        tvY = (int) (h * 0.025);
        sx = tvX + 18;
        sy = tvY + 18;
        sw = tvW - 36 - 74;       // the control strip on the right
        sh = tvH - 36;
        vcrW = (int) (w * 0.56);
        vcrH = (int) (h * 0.085);
        vcrX = (w - vcrW) / 2;
        vcrY = tvY + tvH + 6;
    }

    /** Where the VCR's buttons can go: its own area, so the screens can put their Back button on the deck. */
    int vcrX() { return vcrX; }
    int vcrY() { return vcrY; }
    int vcrW() { return vcrW; }
    int vcrH() { return vcrH; }

    /** Closing the menu: stop the tape, play the rewind, show the blue PAUSE screen, then the cassette comes out. */
    void startEject() {
        if (ejectAt >= 0) return;
        ejectAt = Util.getMillis();
        stopLoops();
        play("vhs_rewind");
    }

    boolean ejecting() { return ejectAt >= 0; }

    boolean ejectDone() {
        return ejectAt >= 0 && (ejectSkipped || Util.getMillis() - ejectAt >= EJECT_MS);
    }

    void skipEject() { ejectSkipped = true; }

    void skip() {
        skipped = true;
    }

    private long age() {
        return skipped || !intro ? Long.MAX_VALUE / 4 : Util.getMillis() - openedAt;
    }

    /** True once the picture is on and the screen's content may be drawn and clicked. */
    boolean picture() {
        return age() >= INSERT_MS + POWER_MS * 0.6;
    }

    /** One sound that goes on until it is stopped: the music of the menu, and under it the hiss of the tape. */
    private static final class Loop extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        Loop(String name, float volume) {
            super(SoundEvent.createVariableRangeEvent(new ResourceLocation("lotusblight", name)),
                    net.minecraft.sounds.SoundSource.MASTER, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = volume;
            this.relative = true;
            this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {}

        void end() {
            stop();
        }
    }

    private static Loop music;
    private static Loop hiss;

    private static void play(String name) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvent.createVariableRangeEvent(new ResourceLocation("lotusblight", name)), 1.0f));
    }

    /** Once the picture is on: the music of the menu, and the tape's hiss under it, quieter than the music. Safe to call every frame. */
    static void ensureLoops() {
        Minecraft mc = Minecraft.getInstance();
        mc.getMusicManager().stopPlaying();                       // the game's own menu music stays off the whole time this is open
        if (music == null || !mc.getSoundManager().isActive(music)) {
            music = new Loop("vhs_music", 0.7f);
            mc.getSoundManager().play(music);
        }
        if (hiss == null || !mc.getSoundManager().isActive(hiss)) {
            hiss = new Loop("vhs_noise", 0.3f);
            mc.getSoundManager().play(hiss);
        }
    }

    /** The menu is left: the tape stops. */
    static void stopLoops() {
        if (music != null) music.end();
        if (hiss != null) hiss.end();
        music = null;
        hiss = null;
    }

    /** The room, the set, the deck. Call first, before anything else is drawn. */
    void body(GuiGraphics g, int w, int h) {
        long age = age();
        if (intro && !skipped) {
            if (!insertSoundPlayed) { insertSoundPlayed = true; play("vhs_insert"); }
        }
        g.fill(0, 0, w, h, 0xFF0A0807);
        // the set
        panel(g, tvX, tvY, tvX + tvW, tvY + tvH, BODY, BODY_LIGHT, BODY_DARK);
        // the control strip: speaker slots, two knobs, a button row and the power light
        int cx = sx + sw + 18;
        for (int i = 0; i < 9; i++) g.fill(cx + 4, sy + 6 + i * 6, cx + 46, sy + 8 + i * 6, BODY_DARK);
        knob(g, cx + 25, sy + 86, 17, (int) (Util.getMillis() / 40 % 360));
        knob(g, cx + 25, sy + 132, 13, 40);
        for (int i = 0; i < 4; i++) panel(g, cx + 2 + i * 12, sy + sh - 38, cx + 12 + i * 12, sy + sh - 26, 0xFF3A322C, 0xFF5A5046, BODY_DARK);
        boolean on = age >= INSERT_MS;
        g.fill(cx + 36, sy + sh - 16, cx + 44, sy + sh - 10, on ? 0xFFE03030 : 0xFF501010);
        // the VCR
        panel(g, vcrX, vcrY, vcrX + vcrW, vcrY + vcrH, 0xFF1E1A18, 0xFF3A322C, 0xFF0C0A09);
        int slotW = (int) (vcrW * 0.5);
        int slotX = vcrX + (vcrW - slotW) / 2;
        g.fill(slotX, vcrY + vcrH / 2 - 3, slotX + slotW, vcrY + vcrH / 2 + 4, 0xFF040302);
        g.fill(slotX, vcrY + vcrH / 2 - 3, slotX + slotW, vcrY + vcrH / 2 - 2, 0xFF5A5046);
        for (int i = 0; i < 5; i++) g.fill(vcrX + 12 + i * 10, vcrY + 8, vcrX + 18 + i * 10, vcrY + 12, 0xFF3A322C);
        g.drawString(Minecraft.getInstance().font, "VHS", vcrX + vcrW - 38, vcrY + 7, on ? 0xFFE0A030 : 0xFF605040, false);
        // the cassette going into the deck (and, when the menu closes, coming out again): it is hidden by the deck's front past the slot
        float pIn = Math.min(1f, age / (INSERT_MS * 0.6f));
        float amount = age < INSERT_MS ? 1 - (1 - pIn) * (1 - pIn) : 1f;           // 0 = below the deck, 1 = in
        boolean show = age < INSERT_MS;
        if (ejecting()) {
            long ea = Util.getMillis() - ejectAt;
            if (ea > EJECT_MS - EJECT_OUT_MS) {
                float q = Math.min(1f, (ea - (EJECT_MS - EJECT_OUT_MS)) / (float) EJECT_OUT_MS);
                amount = 1 - (1 - (1 - q) * (1 - q));
                show = true;
            }
        }
        if (show) {
            int cw = (int) (slotW * 0.94);
            int ch = (int) (cw * 0.62);
            int cxp = slotX + (slotW - cw) / 2;
            int slotY = vcrY + vcrH / 2 + 4;
            int start = vcrY + vcrH + 40;
            int end = slotY - ch + 12;
            int cy = (int) (start + (end - start) * amount);
            g.enableScissor(slotX - 6, slotY, slotX + slotW + 6, h);
            cassette(g, cxp, cy, cw, ch, amount);
            g.disableScissor();
        }
    }

    private static void cassette(GuiGraphics g, int x, int y, int w, int h, float p) {
        g.fill(x, y, x + w, y + h, 0xFF16161A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF22222A);
        g.fill(x + 8, y + 6, x + w - 8, y + h * 5 / 9, 0xFFC9C2A6);                        // the label
        g.fill(x + 12, y + 10, x + w - 12, y + 14, 0xFF8A1F1F);
        g.fill(x + 12, y + 18, x + w - 30, y + 20, 0xFF6A6454);
        int ry = y + h * 5 / 9 + 6;
        int spin = (int) (p * 40);
        disc(g, x + w / 3, ry + 5, 6, 0xFF0A0A0C);
        disc(g, x + 2 * w / 3, ry + 5, 6, 0xFF0A0A0C);
        for (int i = 0; i < 3; i++) {
            double a = (spin + i * 120) * Math.PI / 180;
            g.fill(x + w / 3 + (int) (Math.cos(a) * 4), ry + 5 + (int) (Math.sin(a) * 4), x + w / 3 + (int) (Math.cos(a) * 4) + 1, ry + 6 + (int) (Math.sin(a) * 4), 0xFFE0E0E0);
            g.fill(x + 2 * w / 3 + (int) (Math.cos(a) * 4), ry + 5 + (int) (Math.sin(a) * 4), x + 2 * w / 3 + (int) (Math.cos(a) * 4) + 1, ry + 6 + (int) (Math.sin(a) * 4), 0xFFE0E0E0);
        }
        g.fill(x + w / 3 + 8, ry + 2, x + 2 * w / 3 - 8, ry + 8, 0xFF3A2A1A);              // the tape between the reels
    }

    private static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int base, int light, int dark) {
        g.fill(x0 + 3, y0, x1 - 3, y1, base);
        g.fill(x0, y0 + 3, x1, y1 - 3, base);
        g.fill(x0 + 3, y0, x1 - 3, y0 + 2, light);
        g.fill(x0, y0 + 3, x0 + 2, y1 - 3, light);
        g.fill(x0 + 3, y1 - 2, x1 - 3, y1, dark);
        g.fill(x1 - 2, y0 + 3, x1, y1 - 3, dark);
    }

    private static void knob(GuiGraphics g, int cx, int cy, int r, int deg) {
        disc(g, cx, cy, r + 2, BODY_DARK);
        disc(g, cx, cy, r, 0xFF3A322C);
        disc(g, cx, cy, r - 3, 0xFF4A4038);
        double a = deg * Math.PI / 180;
        for (int i = 0; i < r - 2; i++) g.fill(cx + (int) (Math.cos(a) * i), cy + (int) (Math.sin(a) * i), cx + (int) (Math.cos(a) * i) + 2, cy + (int) (Math.sin(a) * i) + 2, 0xFFD0C8B0);
    }

    static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.floor(Math.sqrt(r * r - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** Starts the glass: everything drawn until {@link #endScreen} stays inside it. */
    void beginScreen(GuiGraphics g) {
        g.fill(sx, sy, sx + sw, sy + sh, 0xFF000000);
        g.enableScissor(sx, sy, sx + sw, sy + sh);
    }

    /** Ends the glass: the tape's look, the curve of the tube, and, while the set is switching on, the line of light. */
    void endScreen(GuiGraphics g, Font font) {
        long ms = Util.getMillis();
        long age = age();
        if (ejecting()) {
            long ea = ms - ejectAt;
            g.fill(sx, sy, sx + sw, sy + sh, 0xFF1428C8);                                  // the blue of a deck with no picture
            if (ea < EJECT_MS - 200) {
                int size = Math.min(sw, sh) / 2;
                g.blit(new ResourceLocation("lotusblight", "textures/gui/vhs_pause.png"),
                        sx + (sw - size) / 2, sy + (sh - size) / 2, size, size, 0, 0, 120, 120, 120, 120);
            }
            age = Long.MAX_VALUE / 4;
        } else if (age < INSERT_MS) {
            // before the tape plays the set shows only blue snow
            Random r = new Random(ms / 45);
            g.fill(sx, sy, sx + sw, sy + sh, 0xFF000000);
            g.disableScissor();
            return;
        }
        // the line of light that opens into the picture
        if (age < INSERT_MS + POWER_MS) {
            float p = (age - INSERT_MS) / (float) POWER_MS;
            int midY = sy + sh / 2;
            int half = Math.max(1, (int) (sh / 2 * Math.min(1f, Math.max(0f, (p - 0.25f) / 0.75f))));
            g.fill(sx, sy, sx + sw, midY - half, 0xFF000000);
            g.fill(sx, midY + half, sx + sw, sy + sh, 0xFF000000);
            g.fill(sx, midY - 1, sx + sw, midY + 1, 0xFFFFFFFF);
            Random r = new Random(ms / 30);
            for (int i = 0; i < 160; i++) {
                int x = sx + r.nextInt(sw);
                int y = midY - half + r.nextInt(Math.max(1, half * 2));
                g.fill(x, y, x + 2, y + 1, 0x60FFFFFF);
            }
        }
        // the tape: scanlines, a tracking band crawling up, torn lines, snow
        for (int y = sy; y < sy + sh; y += 2) g.fill(sx, y, sx + sw, y + 1, 0x2E000000);
        int band = sy + sh - (int) ((ms / 14) % (sh + 80));
        g.fill(sx, band, sx + sw, band + 5, 0x16FFFFFF);
        g.fill(sx, band + 5, sx + sw, band + 7, 0x0CFFFFFF);
        Random rnd = new Random(ms / 90);
        for (int i = 0; i < 14; i++) {
            int y = band + rnd.nextInt(14) - 4;
            int x0 = sx + rnd.nextInt(sw);
            g.fill(x0, y, Math.min(sx + sw, x0 + 20 + rnd.nextInt(120)), y + 1, 0x30FFFFFF);
        }
        Random snow = new Random(ms / 60);
        for (int i = 0; i < 110; i++) {
            int x = sx + snow.nextInt(sw);
            int y = sy + snow.nextInt(sh);
            g.fill(x, y, x + 1 + snow.nextInt(2), y + 1, 0x30FFFFFF);
        }
        // the tube: dark corners and edges, a faint reflection of the room
        int steps = 22;
        for (int i = 0; i < steps; i++) {
            int a = (int) (110 * Math.pow(1 - i / (double) steps, 2.2));
            int col = a << 24;
            g.fill(sx, sy + i, sx + sw, sy + i + 1, col);
            g.fill(sx, sy + sh - i - 1, sx + sw, sy + sh - i, col);
            g.fill(sx + i, sy, sx + i + 1, sy + sh, col);
            g.fill(sx + sw - i - 1, sy, sx + sw - i, sy + sh, col);
        }
        for (int i = 0; i < 40; i++) g.fill(sx + 10 + i * 2, sy + 12 - i / 5, sx + 14 + i * 2, sy + 13 - i / 5, 0x0AFFFFFF);
        // the corners are round
        for (int i = 0; i < 14; i++) {
            int cut = (int) (14 - Math.sqrt(14 * 14 - (14 - i) * (14 - i)));
            g.fill(sx, sy + i, sx + cut, sy + i + 1, 0xFF000000);
            g.fill(sx + sw - cut, sy + i, sx + sw, sy + i + 1, 0xFF000000);
            g.fill(sx, sy + sh - i - 1, sx + cut, sy + sh - i, 0xFF000000);
            g.fill(sx + sw - cut, sy + sh - i - 1, sx + sw, sy + sh - i, 0xFF000000);
        }
        g.disableScissor();
        // the player's marks on the glass
        g.drawString(font, "▶ PLAY", sx + sw - 62, sy + 10, 0xFFE0E0E0, true);
        g.drawString(font, "SP", sx + 12, sy + 10, 0xFFE0E0E0, true);
        long sec = ms / 1000;
        g.drawString(font, String.format("%02d:%02d", (sec / 60) % 100, sec % 60), sx + sw - 50, sy + sh - 20, 0xFFA0A0A0, true);
    }
}
