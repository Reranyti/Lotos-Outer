package com.lotusblight.client;

import com.example.chatoverhaul.ChatOverhaulMod;
import com.example.chatoverhaul.client.ChatMessage;
import com.example.chatoverhaul.client.CustomChatComponent;
import com.example.chatoverhaul.data.NickColorManager;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.util.LinkedList;

/**
 * Mirrors ChatOverhaulBranchColor's soft-dependency pattern: only method bodies touch
 * chatoverhaul's classes, so this class itself stays safe to load with the mod absent
 * (see LotusSeerLensItem's javadoc for why implementing an optional mod's interface directly
 * would be unsafe here, unlike this method-body-only usage).
 *
 * CustomChatComponent's own public entry point, addMessage(Component), can't give the inner
 * voice a name or portrait - it only applies that styled render path to messages whose sender
 * matches a currently-connected player (isOnlinePlayer, checked inside addMessage itself before
 * the ChatMessage is even built), and hard-codes a plain grey "system message" render for
 * everything else. There's no public way to opt into the styled path for a fictional sender.
 *
 * ChatMessage's constructor is public, though, and its isPlayerMessage flag is all the renderer
 * actually branches on - it never re-checks isOnlinePlayer at render time. So this builds the
 * ChatMessage directly with isPlayerMessage=true and reaches into CustomChatComponent's private
 * message list via reflection to insert it, bypassing addMessage()'s regex parsing entirely.
 * SkinManager.getPlayerSkin falls back to the vanilla default skin for an unrecognized name (pure
 * local lookup against connected players, no network call - checked in the decompiled class
 * before relying on this), so the head renders as a generic Steve/Alex rather than stalling.
 * Any failure (a future chatoverhaul build renaming/removing the field) disables this permanently
 * for the session instead of retrying every line.
 */
public final class InnerVoiceChatBridge {
    private static final String SPEAKER = "Неизвестный";
    public static final boolean LOADED = ModList.get().isLoaded("chatoverhaul");

    private static Field messagesField;
    private static boolean broken;

    static {
        if (LOADED) {
            NickColorManager.setClientColor(SPEAKER, "gold");
        }
    }

    private InnerVoiceChatBridge() {}

    public static void postLine(String text) {
        if (!LOADED || broken) return;
        try {
            CustomChatComponent chat = ChatOverhaulMod.getChat();
            if (messagesField == null) {
                messagesField = CustomChatComponent.class.getDeclaredField("messages");
                messagesField.setAccessible(true);
            }
            @SuppressWarnings("unchecked")
            LinkedList<ChatMessage> messages = (LinkedList<ChatMessage>) messagesField.get(chat);
            messages.addFirst(new ChatMessage(Component.literal(text), SPEAKER, text, true));
            while (messages.size() > 100) {
                messages.removeLast();
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            broken = true;
        }
    }
}
