package com.lotusblight.world;

import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.spread.InfectionPhases;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.registry.ModItems;
import com.lotusblight.item.LotusWikiItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import com.lotusblight.lib.LotusLib;
import com.lotusblight.lib.LotusTaskQueue;
import com.lotusblight.lib.LotusIntegrationManager;
import com.lotusblight.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import com.lotusblight.registry.ModBiomes;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Everything that is NOT the infection spread tick anymore — worldgen patch
 * seeding, the new-player starter kit / tutorial pond, villager trades,
 * dialogue interaction and cleansing powder. The spread simulation itself
 * lives in {@link com.lotusblight.spread.InfectionSpreadEngine}, driven by
 * {@link OutbreakSavedData} instead of the old non-persistent static maps.
 */
public class LotusEvents {
    private static final Random RANDOM = new Random();
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.2f, 0.95f, 0.35f), 1.0f);
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.55f), 1.0f);
    private static final LotusTaskQueue<GlobalPos> PENDING_LOTUS_CHUNKS = new LotusTaskQueue<>();
    private final LotusIntegrationManager integrations = new LotusIntegrationManager();

    /** Registers a new outbreak anchor in the persisted registry. Outbreaks start hidden until discovered (see the map system). */
    public static void rememberAnchor(ServerLevel level, BlockPos pos) {
        OutbreakSavedData.get(level).registerOutbreak(pos.immutable(), level.getGameTime(), true);
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) return;
        ChunkPos chunk = event.getChunk().getPos();
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        BlockPos probe = new BlockPos(minX + 8, 64, minZ + 8);
        if (level.getBiome(probe).is(ModBiomes.BLESSING_BIOME)) {
            generateBlessingPatch(level, minX, minZ);
        } else {
            boolean lotusBiome = level.getBiome(probe).is(ModBiomes.LOTUS_BIOME);
            if (lotusBiome || RANDOM.nextInt(24) == 0) {
                PENDING_LOTUS_CHUNKS.offer(GlobalPos.of(level.dimension(), new BlockPos(minX, 0, minZ)),
                        com.lotusblight.LotusConfig.MAX_PENDING_WORLDGEN_TASKS.get());
            }
        }
    }

    private void processPendingLotusChunk(net.minecraft.server.MinecraftServer server) {
        LotusLib.process(PENDING_LOTUS_CHUNKS, LotusLib.MAX_WORLDGEN_TASKS_PER_TICK, queued -> {
            ServerLevel level = server.getLevel(queued.dimension());
            if (level != null && level.hasChunkAt(queued.pos())) {
                generateLotusPatch(level, queued.pos().getX(), queued.pos().getZ());
            }
        });
    }

    private void generateBlessingPatch(ServerLevel level, int minX, int minZ) {
        for (int i = 0; i < 3; i++) {
            int x = minX + 2 + RANDOM.nextInt(12);
            int z = minZ + 2 + RANDOM.nextInt(12);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            if (y < level.getMinBuildHeight() || y > 250) continue;
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) pos = pos.above();
            if (!level.getBlockState(pos).isAir()) continue;
            level.setBlock(pos.below(), ModBlocks.BLESSING_SOIL.get().defaultBlockState(), 3);
            level.setBlock(pos, RANDOM.nextBoolean() ? ModBlocks.BLESSING_NODULE.get().defaultBlockState() : ModBlocks.BLOSSOM_GRASS.get().defaultBlockState(), 3);
            if (RANDOM.nextInt(3) == 0) level.setBlock(pos.east(), ModBlocks.GLOW_BERRIES.get().defaultBlockState(), 3);
        }
    }

    private void generateLotusPatch(ServerLevel level, int minX, int minZ) {
        for (int i = 0; i < 12; i++) {
            int x = minX + 1 + RANDOM.nextInt(14);
            int z = minZ + 1 + RANDOM.nextInt(14);
            BlockPos water = findWaterColumn(level, x, z);
            if (water != null && level.getBlockState(water.above()).isAir()) {
                int waterAround = countWater(level, water, 4);
                int flowersToPlace = waterAround >= 24 ? 2 : 1;
                for (int flower = 0; flower < flowersToPlace; flower++) {
                    BlockPos candidateWater = flower == 0 ? water : findNearbyWater(level, water, 5);
                    if (candidateWater == null || !level.getBlockState(candidateWater.above()).isAir()) continue;
                    BlockPos flowerPos = candidateWater.above();
                    level.setBlock(flowerPos, ModBlocks.INFECTED_LOTUS.get().defaultBlockState(), 3);
                    if (flower == 0) rememberAnchor(level, flowerPos);
                    bloom(level, flowerPos, PINK);
                }
                return;
            }
        }
    }

    private int countWater(ServerLevel level, BlockPos center, int radius) {
        int count = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, 0, -radius), center.offset(radius, 0, radius))) {
            if (level.getFluidState(p).is(Fluids.WATER)) count++;
        }
        return count;
    }

    private BlockPos findNearbyWater(ServerLevel level, BlockPos center, int radius) {
        for (int i = 0; i < 12; i++) {
            int x = center.getX() + RANDOM.nextInt(radius * 2 + 1) - radius;
            int z = center.getZ() + RANDOM.nextInt(radius * 2 + 1) - radius;
            BlockPos found = findWaterColumn(level, x, z);
            if (found != null) return found;
        }
        return null;
    }

    private BlockPos findWaterColumn(ServerLevel level, int x, int z) {
        int top = Math.min(level.getMaxBuildHeight() - 2, 160);
        int bottom = Math.max(level.getMinBuildHeight() + 1, 32);
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getFluidState(pos).is(Fluids.WATER) && level.getFluidState(pos).isSource()
                    && level.getBlockState(pos.above()).isAir()) return pos;
        }
        return null;
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getPersistentData().getBoolean("LotusBlightStarterGiven")) return;
        player.getPersistentData().putBoolean("LotusBlightStarterGiven", true);
        player.getInventory().add(new ItemStack(ModItems.LOTUS_SEED.get(), 3));
        player.getInventory().add(LotusWikiItem.createStack());
        player.displayClientMessage(Component.literal("Три семени лотоса и книга-вики появились у тебя. Ты сам решаешь, куда пустить корни."), false);
        // Used to scan up to 512 blocks out (16 radius tiers x 8 samples x a full-height column
        // scan each) synchronously in this very event handler — effectively the same bug that was
        // just found and fixed in EpicenterManager (unbounded synchronous world queries forcing
        // chunk generation), except this one fired on every single new player login instead of
        // once at world creation. Now spread across ticks via pendingStarterSearches below.
        pendingStarterSearches.put(player.getUUID(), new StarterSearch(player.serverLevel(), player.blockPosition()));
    }

    private static final class StarterSearch {
        final ServerLevel level;
        final BlockPos center;
        int radius = 16;
        int attemptsAtRadius = 0;

        StarterSearch(ServerLevel level, BlockPos center) {
            this.level = level;
            this.center = center;
        }
    }

    private final Map<UUID, StarterSearch> pendingStarterSearches = new HashMap<>();
    private static final int STARTER_ATTEMPTS_PER_RADIUS = 8;
    private static final int STARTER_MAX_RADIUS = 512;
    private static final int STARTER_RADIUS_STEP = 32;
    private static final int STARTER_PROBE_INTERVAL_TICKS = 4;

    /** One probe (one column scan) per call, budgeted to run at most once every few ticks — see the class javadoc on pendingStarterSearches' use site. */
    private void tickStarterSearches(net.minecraft.server.MinecraftServer server) {
        if (pendingStarterSearches.isEmpty()) return;
        if (server.getTickCount() % STARTER_PROBE_INTERVAL_TICKS != 0) return;

        var it = pendingStarterSearches.entrySet().iterator();
        if (!it.hasNext()) return;
        var entry = it.next();
        StarterSearch search = entry.getValue();

        int x = search.center.getX() + RANDOM.nextInt(search.radius * 2 + 1) - search.radius;
        int z = search.center.getZ() + RANDOM.nextInt(search.radius * 2 + 1) - search.radius;
        BlockPos water = findWaterColumn(search.level, x, z);
        if (water != null && search.level.getBlockState(water.above()).isAir()) {
            placeTutorialLotus(search.level, water);
            it.remove();
            return;
        }

        search.attemptsAtRadius++;
        if (search.attemptsAtRadius >= STARTER_ATTEMPTS_PER_RADIUS) {
            search.attemptsAtRadius = 0;
            search.radius += STARTER_RADIUS_STEP;
            if (search.radius > STARTER_MAX_RADIUS) {
                // A new player must always have a safe water lesson nearby.
                BlockPos pondWater = createTutorialPond(search.level, search.center);
                placeTutorialLotus(search.level, pondWater);
                it.remove();
            }
        }
    }

    private void placeTutorialLotus(ServerLevel level, BlockPos water) {
        BlockPos flowerPos = water.above();
        if (!level.getBlockState(flowerPos).isAir()) return;
        level.setBlock(flowerPos, ModBlocks.INFECTED_LOTUS.get().defaultBlockState(), 3);
        rememberAnchor(level, flowerPos);
        bloom(level, flowerPos, PINK);
    }

    private BlockPos createTutorialPond(ServerLevel level, BlockPos center) {
        int x = center.getX() + 4;
        int z = center.getZ() + 4;
        int y = Math.max(level.getMinBuildHeight() + 2, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1);
        BlockPos water = new BlockPos(x, y, z);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos p = new BlockPos(x + dx, y, z + dz);
                level.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
            }
        }
        return water;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        integrations.tick();
        processPendingLotusChunk(event.getServer());
        tickStarterSearches(event.getServer());
    }

    private void bloom(ServerLevel level, BlockPos pos, DustParticleOptions color) {
        level.sendParticles(color, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 5, 0.3, 0.25, 0.3, 0.01);
        level.playSound(null, pos, SoundEvents.GRASS_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 1.5f);
    }

    @SubscribeEvent
    public void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != ModVillagers.LOTUS_BOTANIST.get()) return;
        event.getTrades().get(1).add((trader, random) -> new MerchantOffer(new ItemStack(Items.EMERALD, 2), new ItemStack(ModItems.LOTUS_SEED.get(), 2), 12, 4, 0.05f));
        event.getTrades().get(2).add((trader, random) -> new MerchantOffer(new ItemStack(Items.EMERALD, 8), new ItemStack(ModItems.LOTUS_MAP.get()), 8, 8, 0.05f));
        event.getTrades().get(3).add((trader, random) -> new MerchantOffer(new ItemStack(Items.EMERALD, 5), new ItemStack(ModItems.CLEANSING_POWDER.get(), 4), 12, 10, 0.05f));
    }

    @SubscribeEvent
    public void onLotusInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level().isClientSide()) {
            if (event.getEntity().level().getBlockState(event.getPos()).is(ModBlocks.INFECTED_LOTUS.get())) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lotusblight.client.LotusClientHooks.openDialogue(event.getHitVec()));
                event.setCanceled(true);
            }
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(ModBlocks.INFECTED_LOTUS.get())) return;
        if (level.getGameTime() % 8 != 0) return;
        bloom(level, pos, PINK);
        Player player = event.getEntity();
        if (level.random.nextInt(3) == 0) {
            String[] whispers = {
                    "Я знаю, кто ты...",
                    "Я знаю, что ты делаешь.",
                    "Мы — лотос.",
                    "Ты слышишь нас под водой?"
            };
            player.displayClientMessage(Component.literal(whispers[level.random.nextInt(whispers.length)]), false);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickItem event) {
        if (event.getItemStack().is(ModItems.CLEANSING_POWDER.get()) && event.getEntity().level() instanceof ServerLevel level) {
            OutbreakSavedData data = OutbreakSavedData.get(level);
            BlockPos pos = event.getEntity().blockPosition();
            int cleansed = 0;
            for (BlockPos target : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
                if (level.getBlockState(target).is(ModBlocks.INFECTED_SOIL.get()) || level.getBlockState(target).is(ModBlocks.INFECTED_WATER.get()) || level.getBlockState(target).is(ModBlocks.LOTUS_ROOTS.get())) {
                    level.setBlock(target, Blocks.DIRT.defaultBlockState(), 3);
                    data.incrementChunkCount(new ChunkPos(target), -1);
                    cleansed++;
                    level.sendParticles(GREEN, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.01);
                }
            }
            // This used to only touch the per-chunk counter above (which nothing reads for the
            // boss bar/phase) and never OutbreakRecord#infectedBlockCount — the actual number the
            // progress bar and phase are computed from. Cleansing blocks visibly did nothing to
            // the bar. Now the nearest outbreak's count/phase/progress actually goes back down.
            if (cleansed > 0) {
                OutbreakRecord nearest = data.nearestOutbreak(pos, 128.0, false);
                if (nearest != null) {
                    int newCount = Math.max(0, nearest.infectedBlockCount() - cleansed);
                    int newPhase = InfectionPhases.phaseForBlockCount(newCount);
                    float progress = InfectionPhases.progressWithinPhase(newPhase, newCount);
                    data.updateOutbreak(nearest.withInfectedBlockCount(newCount).withPhase(newPhase).withProgress(progress));
                }
            }
            if (!event.getEntity().getAbilities().instabuild) event.getItemStack().shrink(1);
        }
    }
}
