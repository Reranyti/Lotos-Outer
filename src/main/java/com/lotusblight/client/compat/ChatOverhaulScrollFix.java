package com.lotusblight.client.compat;

import com.lotusblight.LotusBlight;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Bug #10 - scrolling with the chat screen open didn't scroll chat (it fell through to whatever
 * vanilla control happened to be underneath, e.g. the command-suggestion popup). Root cause is a
 * third-party mod on this modpack ("chatoverhaul", package com.example.chatoverhaul) that replaces
 * chat rendering with its own overlay (CustomChatComponent) and only forwards scroll to it when
 * its own internal isChatOpen() flag happens to be true - when that flag is out of sync with the
 * real screen state, the scroll event falls through uncaptured.
 *
 * Not our bug, but it's still part of what a player experiences running this mod, so this patches
 * around it from our side instead of leaving it broken: reflectively drive that mod's own chat
 * overlay directly whenever the ChatScreen is open, regardless of its own isChatOpen() state. Pure
 * reflection (no compile-time dependency on that mod) so this is a no-op - not a crash - on any
 * install that doesn't have it.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChatOverhaulScrollFix {

    private static final String MOD_CLASS = "com.example.chatoverhaul.ChatOverhaulMod";
    private static Boolean available;
    private static java.lang.reflect.Method getChat;
    private static java.lang.reflect.Method scroll;

    private ChatOverhaulScrollFix() {}

    private static boolean resolveReflection() {
        if (available != null) return available;
        try {
            Class<?> modClass = Class.forName(MOD_CLASS);
            getChat = modClass.getMethod("getChat");
            Class<?> chatComponentClass = getChat.getReturnType();
            scroll = chatComponentClass.getMethod("scroll", int.class);
            available = true;
        } catch (Throwable t) {
            // Mod not installed (or its internals changed) - just let vanilla handle scroll.
            available = false;
        }
        return available;
    }

    // HIGHEST so this runs before that mod's own (buggy) handler and decides the outcome itself,
    // instead of hoping its isChatOpen() check happens to be true this time.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen)) return;
        if (!resolveReflection()) return;
        try {
            Object chatComponent = getChat.invoke(null);
            if (chatComponent == null) return;
            // Matches that mod's own onMouseScroll math (see its ClientEventHandler) so scroll
            // direction/speed feels the same as it does everywhere else in that overlay.
            int amount = (int) -event.getScrollDelta() * 3;
            scroll.invoke(chatComponent, amount);
            event.setCanceled(true);
        } catch (Throwable ignored) {
            // Reflection failing at runtime (e.g. that mod updates its internals) should never
            // break our own chat - just leave the event alone and let vanilla take it.
        }
    }
}
