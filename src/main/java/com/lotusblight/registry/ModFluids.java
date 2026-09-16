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
            .canSwim(false)
            .canDrown(false)
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
