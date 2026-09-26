package com.lotusblight.client;

import com.example.chatoverhaul.ChatOverhaulMod;
import com.example.chatoverhaul.client.ChatMessage;
import com.example.chatoverhaul.client.CustomChatComponent;
import com.example.chatoverhaul.data.NickColorManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.Map;

/**
 * Story dialogue through Chat Overhaul (optional, modId "chatoverhaul"): with it installed, every
 * character line - Star Light, the World Lotus, Honcho, the inner voice - goes into its chat as that
 * character's message, with their own name color and icon (see SpeakerIcons and the renderHead
 * mixin), and our own dialogue boxes step aside. Choices stay ours (HonchoMeetingScreen,
 * HonchoStoryScreen buttons, the Y/N plea) - only the lines move.
 *
 * Only method bodies touch Chat Overhaul's classes, and every entry point checks LOADED first, so
 * this class is safe to load without it. CustomChatComponent#addMessage only styles messages from
 * currently connected players and renders everything else as plain grey system text, so the
 * ChatMessage is built directly with isPlayerMessage=true and put into its private message list
 * through reflection (the renderer only branches on that flag). Any reflective failure (a future
 * Chat Overhaul build renaming the field) switches the chat route off for the session and the
 * callers fall back to their own boxes.
 */
public final class ChatDialogue {
    public static final boolean LOADED = ModList.get().isLoaded("chatoverhaul");

    public static final String INNER_VOICE = "Неизвестный";
    public static final String STAR_LIGHT = "Звёздный Свет";
    public static final String HONCHO = "Хончо";
    public static final String WORLD_LOTUS = "Мировой Лотос";

    /** Name colors in Chat Overhaul's own palette (NickColor). */
    private static final Map<String, String> NAME_COLORS = Map.of(
            INNER_VOICE, "gold",
            STAR_LIGHT, "yellow",
            HONCHO, "light_purple",
            WORLD_LOTUS, "red");

    private static final int HINT_COLOR = 0xB0FFFFFF;
    private static final int MAX_MESSAGES = 100;

    private static Field messagesField;
    private static boolean broken;

    private ChatDialogue() {}

    /** True when lines should go to Chat Overhaul's chat instead of our own boxes. */
    public static boolean active() {
        return LOADED && !broken;
    }

    /** Posts one line as this speaker's chat message. Does nothing when the chat route is off. */
    public static void postLine(String speaker, String text) {
        if (!active()) return;
        try {
            // Chat Overhaul clears and re-syncs its client color cache on join - set it each time.
            String color = NAME_COLORS.get(speaker);
            if (color != null) NickColorManager.setClientColor(speaker, color);
            CustomChatComponent chat = ChatOverhaulMod.getChat();
            if (messagesField == null) {
                messagesField = CustomChatComponent.class.getDeclaredField("messages");
                messagesField.setAccessible(true);
            }
            @SuppressWarnings("unchecked")
            LinkedList<ChatMessage> messages = (LinkedList<ChatMessage>) messagesField.get(chat);
            messages.addFirst(new ChatMessage(Component.literal(text), speaker, text, true));
            while (messages.size() > MAX_MESSAGES) {
                messages.removeLast();
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            broken = true;
        }
    }

    /**
     * Draws Chat Overhaul's chat inside a Screen - during our cutscenes the HUD (and with it the
     * chat, drawn in RenderGuiEvent.Post) is hidden, but the story is still told through it.
     */
    public static void renderChat(GuiGraphics g, float partialTick) {
        if (!active()) return;
        ChatOverhaulMod.getChat().render(g, partialTick);
    }

    /** Small "press Enter" note where our box used to say it, so a line in chat doesn't look like a stall. */
    public static void drawAdvanceHint(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        String hint = "[Enter] продолжить";
        g.drawString(mc.font, hint, (g.guiWidth() - mc.font.width(hint)) / 2, g.guiHeight() - 72, HINT_COLOR, true);
    }
}
