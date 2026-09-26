package com.lotusblight.mixin.worldedit;

import com.sk89q.worldedit.forge.ForgeWorld;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.biome.BiomeType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fix for WorldEdit 7.2.15 (Forge), which the phase 4 biome rewrite depends on (see
 * LotusBiomeConverter). Since 1.18 biomes are stored per 4x4x4 cell, but its ForgeWorld#setBiome
 * picks the cell inside a chunk section with {@code x & 3, y & 3, z & 3} instead of
 * {@code (x >> 2) & 3, ...} - a block position is treated as if it were already a cell index. Every
 * write at block coordinates lands in a wrong cell (with a step of 4, always the same corner one),
 * so only about 1/64 of any area ever changed. Same code otherwise: same chunk lookup, same
 * section, same registry holder, marks the chunk unsaved. Also fixes WorldEdit's own //setbiome.
 */
@Mixin(value = ForgeWorld.class, remap = false)
public abstract class ForgeWorldBiomeFixMixin {
    @Shadow
    public abstract ServerLevel getWorld();

    @Inject(method = "setBiome(Lcom/sk89q/worldedit/math/BlockVector3;Lcom/sk89q/worldedit/world/biome/BiomeType;)Z",
            at = @At("HEAD"), cancellable = true)
    private void lotusblight$setBiomeInItsOwnCell(BlockVector3 position, BiomeType biome, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel level = getWorld();
        LevelChunk chunk = level.getChunk(position.getBlockX() >> 4, position.getBlockZ() >> 4);
        int sectionIndex = chunk.getSectionIndex(position.getBlockY());
        if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
            cir.setReturnValue(false);
            return;
        }
        Holder<Biome> holder = level.registryAccess().registryOrThrow(Registries.BIOME)
                .getHolder(ResourceKey.create(Registries.BIOME, new ResourceLocation(biome.getId()))).orElse(null);
        if (holder == null) {
            cir.setReturnValue(false);
            return;
        }
        @SuppressWarnings("unchecked")
        PalettedContainer<Holder<Biome>> cells = (PalettedContainer<Holder<Biome>>) chunk.getSection(sectionIndex).getBiomes();
        cells.getAndSet((position.getBlockX() >> 2) & 3, (position.getBlockY() >> 2) & 3, (position.getBlockZ() >> 2) & 3, holder);
        chunk.setUnsaved(true);
        cir.setReturnValue(true);
    }
}
