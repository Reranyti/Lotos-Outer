package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class LotusKeybinds {
    public static final KeyMapping TOGGLE_MAP = new KeyMapping(
            "key.lotusblight.toggle_map",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.lotusblight"
    );
    public static final KeyMapping OPEN_ATLAS = new KeyMapping(
            "key.lotusblight.open_atlas",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.lotusblight"
    );

    private LotusKeybinds() {}

    @Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_MAP);
            event.register(OPEN_ATLAS);
        }
    }

    @Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ClientEvents {
        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            while (TOGGLE_MAP.consumeClick()) {
                LotusHudOverlay.toggleHidden();
            }
            while (OPEN_ATLAS.consumeClick()) {
                Minecraft.getInstance().setScreen(new LotusAtlasScreen());
            }
        }
    }
}
