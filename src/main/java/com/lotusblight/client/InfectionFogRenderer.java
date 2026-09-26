package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.map.ClientMapCache;
import com.lotusblight.map.MapMarker;
import com.lotusblight.world.InfectedGroundBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Thickens and tints the fog toward a sickly green the closer the player is
 * to a mature outbreak, instead of the biome having no felt presence beyond
 * MiniBiomeAtmosphere's particles once you're already standing in one. The
 * effect ramps in gradually starting at phase 2 (not just the mini-biome at
 * phase 4) and by phase 4 should read as genuinely oppressive up close -
 * "заросли, а не декорация" applies to how it feels to walk toward one, not
 * just what it looks like once you're inside it.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InfectionFogRenderer {

    /** Clean-air fog color to blend away from - vanilla's own default is close to this. */
    private static final float CLEAR_R = 0.75f, CLEAR_G = 0.85f, CLEAR_B = 0.80f;
    private static final float SICK_R = 0.20f, SICK_G = 0.42f, SICK_B = 0.24f;

    private InfectionFogRenderer() {}

    /** How far out (blocks) each phase's fog starts fading in, and how strong it gets at zero distance. */
    private static double rangeForPhase(int phase) {
        return switch (phase) {
            case 5 -> 72.0;
            case 4 -> 56.0;
            case 3 -> 40.0;
            default -> 24.0; // phase 2
        };
    }

    private static float intensityCapForPhase(int phase) {
        return switch (phase) {
            case 4, 5 -> 1.0f;
            case 3 -> 0.6f;
            default -> 0.35f;
        };
    }

    /** 0 (clean air) to 1 (thick as this system gets) - the strongest nearby phase 2+ outbreak wins. */
    private static float currentDensity() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return 0f;
        var playerPos = player.blockPosition();

        float best = 0f;
        for (MapMarker marker : ClientMapCache.markers()) {
            if (marker.phase() < 2) continue;
            double range = rangeForPhase(marker.phase());
            double dist = Math.sqrt(marker.pos().distSqr(playerPos));
            if (dist >= range) continue;
            float density = intensityCapForPhase(marker.phase()) * (float) (1.0 - dist / range);
            if (density > best) best = density;
        }
        // The marker-based falloff above measures distance to an outbreak's original ANCHOR
        // point, and only counts outbreaks the player has actually discovered on the map. A large
        // phase 5 outbreak's real infected footprint sprawls far past rangeForPhase's 56-block cap
        // from that one anchor, and standing deep in undiscovered territory got zero fog at all -
        // "тумана нет" even while physically surrounded by infected ground. This local sample
        // answers "is the block under and around me actually infected right now", independent of
        // anchor distance or discovery, so the air itself reads as infected instead of only the
        // horizon changing color.
        return Math.max(best, localGroundDensity);
    }

    private static volatile float localGroundDensity = 0f;
    private static final int LOCAL_SAMPLE_INTERVAL_TICKS = 10;
    private static int ticksSinceLocalSample = LOCAL_SAMPLE_INTERVAL_TICKS;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++ticksSinceLocalSample < LOCAL_SAMPLE_INTERVAL_TICKS) return;
        ticksSinceLocalSample = 0;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.level() == null) {
            localGroundDensity = 0f;
            return;
        }
        BlockPos center = player.blockPosition();
        int infected = 0;
        int sampled = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 1, 4))) {
            var state = player.level().getBlockState(pos);
            // Only solid ground counts toward the fraction - the air the player stands in used to be
            // half of the sampled volume, so even fully infected ground topped out near the threshold.
            if (state.getCollisionShape(player.level(), pos).isEmpty()) continue;
            sampled++;
            if (state.getBlock() instanceof InfectedGroundBlock) {
                infected++;
            }
        }
        // Requires a real patch underfoot (not just one stray infected block at the sample's edge)
        // before it counts as "standing in it" - a third of the sampled ground is enough to read as
        // genuinely infected rather than the outbreak's thin leading edge.
        float fraction = sampled == 0 ? 0f : (float) infected / sampled;
        localGroundDensity = Math.min(1f, fraction / 0.33f);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float density = currentDensity();
        if (density <= 0f) return;
        float[] sick = sickColorForCurrentBiome(density);
        event.setRed(lerp(event.getRed(), sick[0], density));
        event.setGreen(lerp(event.getGreen(), sick[1], density));
        event.setBlue(lerp(event.getBlue(), sick[2], density));
    }

    /**
     * Was one fixed green everywhere - BiomeFogColors now dictates a gradient per biome (sampled
     * across its stops by density, so the fog visibly shifts tone the deeper into an outbreak you
     * are, not just gets thicker). Falls back to the original fixed green for any biome that
     * doesn't have a dictated gradient yet.
     */
    private static float[] sickColorForCurrentBiome(float density) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            var biome = player.level().getBiome(player.blockPosition());
            var biomeId = biome.unwrapKey().map(net.minecraft.resources.ResourceKey::location).orElse(null);
            if (biomeId != null) {
                int[] gradient = BiomeFogColors.gradientFor(biomeId);
                if (gradient != null) {
                    int color = BiomeFogColors.sample(gradient, density);
                    return new float[]{
                            ((color >> 16) & 0xFF) / 255f,
                            ((color >> 8) & 0xFF) / 255f,
                            (color & 0xFF) / 255f
                    };
                }
            }
        }
        return new float[]{SICK_R, SICK_G, SICK_B};
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        // Underwater/lava/powder snow already have their own short fog - leave those alone.
        if (event.getType() != FogType.NONE) return;
        float density = currentDensity();
        if (density <= 0f) return;
        // At full density, the far plane closes down to roughly a third of what it would otherwise
        // be - close enough to feel oppressive without dropping to a disorienting wall of green.
        // The near plane is pulled in harder so the haze starts right in front of the player
        // instead of only swallowing the horizon.
        float farMultiplier = 1.0f - 0.65f * density;
        float nearMultiplier = 1.0f - 0.9f * density;
        event.setNearPlaneDistance(event.getNearPlaneDistance() * nearMultiplier);
        event.setFarPlaneDistance(event.getFarPlaneDistance() * farMultiplier);
        // Forge only applies the new distances when the event is canceled (ForgeHooksClient#onFogRender).
        event.setCanceled(true);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
