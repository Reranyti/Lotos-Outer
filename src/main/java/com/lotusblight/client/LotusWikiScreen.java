package com.lotusblight.client;

import com.lotusblight.data.Faction;
import com.lotusblight.dialogue.LotusWikiLibrary;
import com.lotusblight.map.ClientReputationCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Replaces the vanilla BookViewScreen path entirely — LotusWikiItem used to
 * extend WrittenBookItem and rely on Player#openItemGui, but vanilla's own
 * client-side implementation (LocalPlayer#openItemGui) hardcodes a check
 * against `Items.WRITTEN_BOOK` by exact reference, not `instanceof
 * WrittenBookItem`. A custom item subclass never passes that check, so the
 * screen silently never opened — no crash, no error, nothing. This is a
 * fully custom screen instead, with two decks (see LotusWikiLibrary): the
 * practical wiki, and the scientist's lore journal (meant to grow with
 * story-triggered entries later).
 */
public final class LotusWikiScreen extends Screen {
    private static final int WIDTH = 380;
    private static final int HEIGHT = 260;

    /** How long the just-turned page's text stays flashed gold before fading to its normal color — the "Звёздный свет" note feel the user asked for. */
    private static final long PAGE_FLASH_MS = 450;
    private static final int FLASH_COLOR = 0xFFFFD54F;
    private static final int NORMAL_COLOR = 0xFFE4E7D8;

    private static final int TAB_WIKI = 0;
    private static final int TAB_JOURNAL = 1;
    /** Only reachable once ClientReputationCache.starFallSeen() - "репутация...после старфолла". */
    private static final int TAB_REPUTATION = 2;

    private int tab = TAB_WIKI;
    private int pageIndex;
    private long pageShownAtMs;
    private Button prevButton;
    private Button nextButton;
    private Button tabButton;

    public LotusWikiScreen() {
        super(Component.literal("Вики Lotus Blight"));
    }

    private List<String> pages() {
        return tab == TAB_JOURNAL ? LotusWikiLibrary.JOURNAL_PAGES : LotusWikiLibrary.WIKI_PAGES;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        tabButton = addRenderableWidget(Button.builder(Component.literal(""), b -> switchTab())
                .bounds(left + WIDTH - 140, top + 8, 130, 20).build());
        prevButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> turnPage(-1))
                .bounds(left + 16, top + HEIGHT - 32, 40, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> turnPage(1))
                .bounds(left + WIDTH - 56, top + HEIGHT - 32, 40, 20).build());
        updateButtons();
        pageShownAtMs = System.currentTimeMillis();
    }

    private void switchTab() {
        int tabCount = ClientReputationCache.starFallSeen() ? 3 : 2;
        tab = (tab + 1) % tabCount;
        pageIndex = 0;
        updateButtons();
        pageShownAtMs = System.currentTimeMillis();
    }

    private void turnPage(int delta) {
        int size = pages().size();
        pageIndex = Math.max(0, Math.min(size - 1, pageIndex + delta));
        updateButtons();
        pageShownAtMs = System.currentTimeMillis();
    }

    /** Fresh page's text starts gold ("Звёздный свет"-style) and eases down to its normal color. */
    private int currentTextColor() {
        long elapsed = System.currentTimeMillis() - pageShownAtMs;
        if (elapsed >= PAGE_FLASH_MS) return NORMAL_COLOR;
        float t = elapsed / (float) PAGE_FLASH_MS;
        return lerpColor(FLASH_COLOR, NORMAL_COLOR, t);
    }

    private static int lerpColor(int from, int to, float t) {
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = (int) (fr + (tr - fr) * t);
        int gr = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    /** How far a letter shies away from the cursor at most, and the radius within which it reacts at all. */
    private static final float SHY_MAX_OFFSET = 3.0f;
    private static final float SHY_RADIUS = 26.0f;

    /**
     * "Буквы немного отодвигаются, если мышка рядом, и плавно" — normal text is legible by
     * default; a letter the cursor gets close to eases a couple pixels away from it (falls off
     * smoothly with distance, no snapping) and fades toward the gold "voice" ink underneath as it
     * moves, so the golden layer only really shows through right where the cursor is.
     */
    private void drawVoiceOverlayText(GuiGraphics g, List<String> lines, int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < lines.size(); i++) {
            int lineY = y + i * 11;
            int penX = x;
            String plain = lines.get(i);
            for (int c = 0; c < plain.length(); c++) {
                String glyph = String.valueOf(plain.charAt(c));
                int glyphWidth = this.font.width(glyph);
                float cx = penX + glyphWidth / 2f;
                float cy = lineY + 4f;
                float dx = cx - mouseX;
                float dy = cy - mouseY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float t = Math.max(0f, 1f - dist / SHY_RADIUS); // 1 at cursor, 0 at/beyond radius, smooth falloff
                t = t * t; // ease-in, so it's barely noticeable until the cursor is genuinely close
                float offset = t * SHY_MAX_OFFSET;
                float nx = dist > 0.001f ? dx / dist : 0f;
                float ny = dist > 0.001f ? dy / dist : 0f;

                int goldAlpha = (int) (t * 0xFF);
                int goldColor = (goldAlpha << 24) | 0xFFD54F;
                g.drawString(this.font, glyph, penX, lineY, goldColor, false);

                int normalAlpha = 0xFF - (int) (t * 0x90);
                int normalColor = (normalAlpha << 24) | 0xE4E7D8;
                g.drawString(this.font, glyph, penX + Math.round(nx * offset), lineY + Math.round(ny * offset), normalColor, false);

                penX += glyphWidth;
            }
        }
    }

    /** Plain-string word wrap (unlike Font#split's FormattedCharSequence result) so drawVoiceOverlayText can walk real chars. */
    private List<String> wrapPlain(String text, int maxWidth) {
        List<String> out = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (this.font.width(candidate) > maxWidth && !line.isEmpty()) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private void drawSparkles(GuiGraphics g, int left, int top) {
        int[][] positions = {{40, 60}, {WIDTH - 60, 90}, {60, HEIGHT - 60}, {WIDTH - 90, HEIGHT - 90}, {WIDTH / 2, 40}};
        for (int i = 0; i < positions.length; i++) {
            float phase = (System.currentTimeMillis() / 300.0f) + i * 1.3f;
            float twinkle = (float) (0.4 + 0.6 * Math.abs(Math.sin(phase)));
            int alpha = (int) (twinkle * 0xFF);
            int color = (alpha << 24) | 0xFFD54F;
            g.drawString(this.font, "*", left + positions[i][0], top + positions[i][1], color, false);
        }
    }

    private void updateButtons() {
        String nextLabel = switch (tab) {
            case TAB_WIKI -> "-> Дневник";
            case TAB_JOURNAL -> ClientReputationCache.starFallSeen() ? "-> Репутация" : "-> Вики";
            default -> "-> Вики";
        };
        tabButton.setMessage(Component.literal(nextLabel));
        boolean paged = tab != TAB_REPUTATION;
        prevButton.visible = paged;
        nextButton.visible = paged;
        prevButton.active = paged && pageIndex > 0;
        nextButton.active = paged && pageIndex < pages().size() - 1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        g.fill(left - 3, top - 3, left + WIDTH + 3, top + HEIGHT + 3, 0xFF17251B);
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0xF20B100D);
        g.fill(left + 10, top + 10, left + WIDTH - 150, top + 34, 0xFF263C2B);

        String heading = switch (tab) {
            case TAB_JOURNAL -> "ДНЕВНИК";
            case TAB_REPUTATION -> "РЕПУТАЦИЯ";
            default -> "ВИКИ LOTUS BLIGHT";
        };
        g.drawString(this.font, heading, left + 18, top + 18, 0xFFF1A9CF, false);

        if (tab == TAB_REPUTATION) {
            renderReputation(g, left, top);
        } else {
            List<String> pages = pages();
            String rawText = pages.isEmpty() ? "" : pages.get(pageIndex);
            boolean voiceEntry = rawText.startsWith(LotusWikiLibrary.VOICE_MARKER);
            String pageText = voiceEntry ? rawText.substring(LotusWikiLibrary.VOICE_MARKER.length()) : rawText;

            if (voiceEntry) {
                drawSparkles(g, left, top);
                drawVoiceOverlayText(g, wrapPlain(pageText, WIDTH - 36), left + 18, top + 46, mouseX, mouseY);
            } else {
                var lines = this.font.split(Component.literal(pageText), WIDTH - 36);
                int textColor = currentTextColor();
                for (int i = 0; i < lines.size(); i++) {
                    g.drawString(this.font, lines.get(i), left + 18, top + 46 + i * 11, textColor, false);
                }
            }

            String counter = (pageIndex + 1) + " / " + pages.size();
            g.drawString(this.font, counter, left + WIDTH / 2 - this.font.width(counter) / 2, top + HEIGHT - 26, 0xFF9AD47D, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** "плохие действия плохо хорошие хорошо" - a plain signed number per faction, colored red/green/grey by sign, no page navigation. */
    private void renderReputation(GuiGraphics g, int left, int top) {
        Faction[] factions = Faction.values();
        for (int i = 0; i < factions.length; i++) {
            Faction faction = factions[i];
            int value = ClientReputationCache.reputation(faction);
            int color = value > 0 ? 0xFF7CD672 : (value < 0 ? 0xFFE0645A : 0xFFAAAAAA);
            String line = faction.displayName() + ": " + (value > 0 ? "+" + value : String.valueOf(value));
            g.drawString(this.font, line, left + 18, top + 46 + i * 16, color, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
