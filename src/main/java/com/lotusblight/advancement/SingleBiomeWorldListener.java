package com.lotusblight.advancement;

import com.lotusblight.LotusBlight;
import com.lotusblight.registry.ModBiomes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Checks, once per player on world join, whether the world was generated
 * with the vanilla "Single Biome" preset locked to the Lotus Marsh biome,
 * and awards the {@code lotusblight:single_biome_secret} easter-egg
 * advancement via {@link SingleBiomeWorldTrigger} if so.
 *
 * <p>Uses the same "flag in persistent data, checked once" pattern as
 * {@code LotusBlightStarterGiven} in {@link com.lotusblight.world.LotusEvents}.
 *
 * <p>Registered automatically by Forge through {@code @Mod.EventBusSubscriber}
 * — no edit to {@code LotusBlight.java} is needed for the listener itself.
 * The trigger still needs to be registered with {@code CriteriaTriggers};
 * see {@link SingleBiomeWorldTrigger} for the exact line to add.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SingleBiomeWorldListener {
    private static final String FLAG_KEY = "LotusBlightSingleBiomeChecked";

    private SingleBiomeWorldListener() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getPersistentData().getBoolean(FLAG_KEY)) return;
        player.getPersistentData().putBoolean(FLAG_KEY, true);

        // The preset only ever applies to the overworld generator - checking whatever dimension the
        // player happened to log in to (and setting the flag first) could miss it for good.
        ServerLevel level = player.server.overworld();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator.getBiomeSource() instanceof FixedBiomeSource fixed) {
            var fixedBiome = fixed.getNoiseBiome(0, 0, 0);
            if (fixedBiome.is(ModBiomes.LOTUS_BIOME)) {
                SingleBiomeWorldTrigger.INSTANCE.trigger(player);
            }
        }
    }
}
