package com.lotusblight.registry;

import com.lotusblight.LotusBlight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, LotusBlight.MODID);
    public static final RegistryObject<FluidType> INFECTED_WATER_TYPE = FLUID_TYPES.register("infected_water", () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid.lotusblight.infected_water")
            .density(1000)
            .viscosity(1100)
            .temperature(285)
            .lightLevel(2)
            // Both were false - a supposedly dangerous "infected" water that let the player
            // breathe indefinitely and disabled swim physics, the opposite of threatening.
            // Matches plain water's own defaults now.
            .canSwim(true)
            .canDrown(true)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)) {
        // Forge 1.20.1 has no RegisterClientExtensionsEvent (that's a later-version API) —
        // FluidType itself carries this override, and Forge only ever invokes it from client
        // code paths, so this is safe on a dedicated server despite referencing a client-only type.
        @Override
        public void initializeClient(java.util.function.Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return stillTexture();
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return flowingTexture();
                }

                // Everything below used to fall through to IClientFluidTypeExtensions.DEFAULT,
                // which only tints textures/renders the source/flowing model - it does NOT give
                // the camera any underwater fog/overlay the way vanilla water gets (that's wired
                // into vanilla's own FogRenderer specifically for FluidTags.WATER, which a custom
                // Fluid never matches). Standing inside infected water therefore rendered at full
                // outdoor view distance with only ordinary sky fog - an "x-ray" look where distant
                // terrain stayed clearly visible through the water instead of the murky close-up
                // vanilla water gives.
                // Was one fixed dark-green Vector3f no matter where the player actually was -
                // "постоянно зелёная вода вне зависимости от континента" - even though
                // BiomeFogColors already dictates a real per-biome fog gradient for the ambient
                // InfectionFogRenderer, this never reused it. Now samples that same gradient at
                // the camera's own biome, falling back to the old fixed green only for a biome
                // with no gradient dictated yet.
                @Override
                public org.joml.Vector3f modifyFogColor(net.minecraft.client.Camera camera, float partialTick,
                        net.minecraft.client.multiplayer.ClientLevel level, int renderDistance, float darkenWorldAmount,
                        org.joml.Vector3f fluidFogColor) {
                    net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(camera.getPosition());
                    var biome = level.getBiome(pos);
                    var biomeId = biome.unwrapKey().map(net.minecraft.resources.ResourceKey::location).orElse(null);
                    int[] gradient = biomeId != null ? com.lotusblight.client.BiomeFogColors.gradientFor(biomeId) : null;
                    if (gradient == null) {
                        return new org.joml.Vector3f(0.08F, 0.20F, 0.10F);
                    }
                    int color = com.lotusblight.client.BiomeFogColors.sample(gradient, 1.0f);
                    return new org.joml.Vector3f(
                            ((color >> 16) & 0xFF) / 255f,
                            ((color >> 8) & 0xFF) / 255f,
                            (color & 0xFF) / 255f);
                }

                @Override
                public void modifyFogRender(net.minecraft.client.Camera camera, net.minecraft.client.renderer.FogRenderer.FogMode mode,
                        float renderDistance, float partialTick, float nearDistance, float farDistance, com.mojang.blaze3d.shaders.FogShape shape) {
                    com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(-8.0F);
                    com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(24.0F);
                }

                @Override
                public net.minecraft.resources.ResourceLocation getRenderOverlayTexture(net.minecraft.client.Minecraft mc) {
                    return new net.minecraft.resources.ResourceLocation("textures/misc/underwater.png");
                }

                // Never overridden before - fell through to IClientFluidTypeExtensions.DEFAULT's
                // solid white (0xFFFFFFFF), meaning the still/flowing texture rendered completely
                // untinted ("вода вообще не красится в биомах"). Reuses the exact same per-biome
                // gradient modifyFogColor already samples above, just keyed off the actual fluid
                // block's biome instead of the camera's.
                @Override
                public int getTintColor(net.minecraft.world.level.material.FluidState state,
                        net.minecraft.world.level.BlockAndTintGetter getter, net.minecraft.core.BlockPos pos) {
                    net.minecraft.resources.ResourceLocation biomeId = null;
                    if (getter instanceof net.minecraft.world.level.LevelReader reader) {
                        biomeId = reader.getBiome(pos).unwrapKey().map(net.minecraft.resources.ResourceKey::location).orElse(null);
                    }
                    int[] gradient = biomeId != null ? com.lotusblight.client.BiomeFogColors.gradientFor(biomeId) : null;
                    int color = gradient != null ? com.lotusblight.client.BiomeFogColors.sample(gradient, 1.0f) : 0x08331A;
                    return 0xFF000000 | color;
                }
            });
        }
    });

    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, LotusBlight.MODID);
    public static final RegistryObject<FlowingFluid> INFECTED_WATER = FLUIDS.register("infected_water", () -> new ForgeFlowingFluid.Source(properties()));
    public static final RegistryObject<FlowingFluid> FLOWING_INFECTED_WATER = FLUIDS.register("flowing_infected_water", () -> new ForgeFlowingFluid.Flowing(properties()));

    private static ForgeFlowingFluid.Properties properties() {
        return new ForgeFlowingFluid.Properties(INFECTED_WATER_TYPE, INFECTED_WATER, FLOWING_INFECTED_WATER)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .block(() -> ModBlocks.INFECTED_WATER.get())
                .bucket(() -> ModItems.INFECTED_WATER_BUCKET.get());
    }

    public static ResourceLocation stillTexture() {
        return new ResourceLocation(LotusBlight.MODID, "block/infected_water_still");
    }

    public static ResourceLocation flowingTexture() {
        return new ResourceLocation(LotusBlight.MODID, "block/infected_water_flow");
    }

    private ModFluids() {}
}
