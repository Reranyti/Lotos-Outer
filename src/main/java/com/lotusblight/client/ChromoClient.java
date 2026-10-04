package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.map.ChromoRequestPacket;
import com.lotusblight.map.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.world.Difficulty;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * The Chromo difficulty on the player's side: the fifth entry of Minecraft's own difficulty selector (in the pause
 * menu's options and in the world-creation screen), the warning that comes with picking it, and whether this world is on it.
 *
 * Minecraft's {@code Difficulty} is a closed enum of four, so the fifth entry is one more instance of it that is made
 * here and is not in {@code Difficulty.values()}: it only ever stands in the selector's list and is never sent anywhere
 * (picking it sends our own request, and the world below it runs on Hard).
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChromoClient {
    private static final Logger LOG = LogUtils.getLogger();
    private static final long PENDING_LIFE_MS = 20 * 60_000L;

    private static volatile boolean active;
    private static boolean pendingNewWorld;
    private static long pendingAt;
    private static Difficulty chromo;
    private static boolean triedToMake;

    private ChromoClient() {}

    // ------------------------------------------------------------ state

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
    }

    /** The world-creation screen picked Chromo; it takes hold when the world is actually made. */
    public static void setPendingNewWorld(boolean value) {
        pendingNewWorld = value;
        pendingAt = System.currentTimeMillis();
    }

    public static boolean isPendingNewWorld() {
        return pendingNewWorld && System.currentTimeMillis() - pendingAt < PENDING_LIFE_MS;
    }

    /** Asked by the server when a new world's spawn is made: true once, if the creation screen picked Chromo. */
    public static boolean takePendingNewWorld() {
        boolean wanted = isPendingNewWorld();
        pendingNewWorld = false;
        return wanted;
    }

    // ------------------------------------------------------------ the fifth entry

    /** The selector's fifth entry, or null if it couldn't be made (then the selector simply stays as it was). */
    public static Difficulty chromo() {
        if (chromo != null || triedToMake) return chromo;
        triedToMake = true;
        try {
            Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
            Difficulty made = (Difficulty) unsafe.allocateInstance(Difficulty.class);
            unsafe.putObject(made, unsafe.objectFieldOffset(Enum.class.getDeclaredField("name")), "CHROMO");
            unsafe.putInt(made, unsafe.objectFieldOffset(Enum.class.getDeclaredField("ordinal")), Difficulty.values().length);
            for (Field f : Difficulty.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() == int.class) unsafe.putInt(made, unsafe.objectFieldOffset(f), Difficulty.values().length);
                else if (f.getType() == String.class) unsafe.putObject(made, unsafe.objectFieldOffset(f), "chromo");
            }
            // It must read back as a difficulty with the right name before it is trusted.
            if (!"options.difficulty.chromo".equals(((net.minecraft.network.chat.contents.TranslatableContents) made.getDisplayName().getContents()).getKey())) {
                throw new IllegalStateException("fifth difficulty reads back wrong");
            }
            chromo = made;
        } catch (Throwable t) {
            LOG.warn("Chromo: can't make the fifth entry of the difficulty selector: {}", t.toString());
            chromo = null;
        }
        return chromo;
    }

    public static boolean isChromo(Difficulty difficulty) {
        return difficulty != null && difficulty == chromo;
    }

    /** The values the selector lists: the four of Minecraft and, last, Chromo. */
    public static Difficulty[] selectorValues() {
        Difficulty extra = chromo();
        Difficulty[] vanilla = Difficulty.values();
        if (extra == null) return vanilla;
        Difficulty[] all = java.util.Arrays.copyOf(vanilla, vanilla.length + 1);
        all[vanilla.length] = extra;
        return all;
    }

    // ------------------------------------------------------------ the warning

    /** The warning that comes with picking Chromo; {@code answer} gets true if it is accepted. The previous screen comes back either way. */
    public static void warn(Screen back, java.util.function.Consumer<Boolean> answer) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(accepted -> {
            answer.accept(accepted);
            mc.setScreen(back);
        }, Component.translatable("lotusblight.chromo.title"), Component.translatable("lotusblight.chromo.message"),
                Component.translatable("lotusblight.chromo.accept"), Component.translatable("lotusblight.chromo.cancel")));
    }

    // ------------------------------------------------------------ the selector in the options of a world being played

    /** The difficulty selector of the options screen, as Minecraft builds it, with Chromo as the last entry. */
    public static CycleButton<Difficulty> optionsButton(int x, int y, String key, Minecraft mc) {
        Difficulty extra = chromo();
        Difficulty current = mc.level != null ? mc.level.getDifficulty() : Difficulty.NORMAL;
        Difficulty initial = active && extra != null ? extra : current;
        return CycleButton.builder(Difficulty::getDisplayName)
                .withValues(selectorValues())
                .withInitialValue(initial)
                .create(x, y, 150, 20, Component.translatable(key), (button, picked) -> {
                    if (isChromo(picked)) {
                        if (active) return;
                        Screen here = mc.screen;
                        Difficulty before = mc.level != null ? mc.level.getDifficulty() : Difficulty.NORMAL;
                        warn(here, accepted -> {
                            if (accepted) {
                                NetworkHandler.CHANNEL.sendToServer(new ChromoRequestPacket());
                            } else {
                                button.setValue(before);
                            }
                        });
                    } else if (active) {
                        button.setValue(extra);               // there is no way back
                    } else if (mc.getConnection() != null) {
                        mc.getConnection().send(new ServerboundChangeDifficultyPacket(picked));
                    }
                });
    }
}
