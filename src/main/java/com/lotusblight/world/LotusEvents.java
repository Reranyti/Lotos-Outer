package com.lotusblight.world;

import com.lotusblight.LotusBlight;
import com.lotusblight.LotusConfig;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.registry.ModEffects;
import com.lotusblight.registry.ModItems;
import com.lotusblight.item.LotusWikiItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import com.lotusblight.lib.LotusLib;
import com.lotusblight.lib.LotusTaskQueue;
import com.lotusblight.lib.LotusIntegrationManager;
import com.lotusblight.registry.ModFluids;
import com.lotusblight.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ChunkPos;
import com.lotusblight.registry.ModBiomes;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;


public class LotusEvents {
    private static final Random RANDOM = new Random();
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.2f, 0.95f, 0.35f), 1.0f);
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.55f), 1.0f);
    private static final int PHASE_LENGTH_TICKS = 2400;
    private final Map<UUID, ServerBossEvent> infectionBars = new HashMap<>();
    private static final Map<GlobalPos, Integer> ANCHOR_PHASES = new HashMap<>();
    private static final Map<GlobalPos, Long> ANCHOR_START_TICKS = new HashMap<>();
    private static final LotusTaskQueue<GlobalPos> PENDING_LOTUS_CHUNKS = new LotusTaskQueue<>();
    private final LotusIntegrationManager integrations = new LotusIntegrationManager();

    public static void rememberAnchor(ServerLevel level, BlockPos pos) {
        GlobalPos key = GlobalPos.of(level.dimension(), pos.immutable());
        ANCHOR_PHASES.putIfAbsent(key, 0);
        ANCHOR_START_TICKS.putIfAbsent(key, level.getGameTime());
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
            // Queue natural generation instead of scanning vertical columns inside chunk generation.
            boolean lotusBiome = level.getBiome(probe).is(ModBiomes.LOTUS_BIOME);
            if (lotusBiome || RANDOM.nextInt(24) == 0) {
                PENDING_LOTUS_CHUNKS.offer(GlobalPos.of(level.dimension(), new BlockPos(minX, 0, minZ)));
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
        ensureNearbyOutbreak(player.serverLevel(), player.blockPosition());
    }

    private void ensureNearbyOutbreak(ServerLevel level, BlockPos center) {
        // 32 chunks are the tutorial area: 32 * 16 = 512 blocks.
        for (int radius = 16; radius <= 512; radius += 32) {
            for (int i = 0; i < 8; i++) {
                int x = center.getX() + RANDOM.nextInt(radius * 2 + 1) - radius;
                int z = center.getZ() + RANDOM.nextInt(radius * 2 + 1) - radius;
                BlockPos water = findWaterColumn(level, x, z);
                if (water != null && level.getBlockState(water.above()).isAir()) {
                    placeTutorialLotus(level, water);
                    return;
                }
            }
        }
        // A new player must always have a safe water lesson nearby.
        BlockPos pondWater = createTutorialPond(level, center);
        placeTutorialLotus(level, pondWater);
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
        if (event.getServer().getTickCount() % 10 == 0) {
            updateAnchorPhases(event.getServer());
            for (ServerLevel level : event.getServer().getAllLevels()) {
                for (ServerPlayer player : level.players()) updateInfectionBar(level, player);
            }
        }
        if (event.getServer().getTickCount() % LotusConfig.SPREAD_INTERVAL_TICKS.get() != 0) return;
        // Only registered main anchors spread. Player proximity never creates extra flowers.
        spreadFromRegisteredAnchors(event.getServer());
    }

    private void updateAnchorPhases(net.minecraft.server.MinecraftServer server) {
        for (GlobalPos anchor : new java.util.ArrayList<>(ANCHOR_PHASES.keySet())) {
            ServerLevel level = server.getLevel(anchor.dimension());
            if (level == null) continue;
            BlockPos pos = anchor.pos();
            BlockState anchorState = level.getBlockState(pos);
            if (!anchorState.is(ModBlocks.LOTUS_HEART.get()) && !anchorState.is(ModBlocks.INFECTED_LOTUS.get())) {
                ANCHOR_PHASES.remove(anchor);
                continue;
            }
            long age = Math.max(0L, level.getGameTime() - ANCHOR_START_TICKS.getOrDefault(anchor, level.getGameTime()));
            int phase = age < 2400L ? 1 : age < 7200L ? 2 : age < 14400L ? 3 : 4;
            int previous = ANCHOR_PHASES.getOrDefault(anchor, 0);
            if (phase > previous) {
                ANCHOR_PHASES.put(anchor, phase);
                level.sendParticles(GREEN, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 32, 1.4, 0.7, 1.4, 0.04);
                level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.BLOCKS, 0.55f, 0.45f);
            }
        }
    }

    private void updateInfectionBar(ServerLevel level, ServerPlayer player) {
        int radius = 8;
        int infected = 0;
        BlockPos center = player.blockPosition();
        for (BlockPos target : BlockPos.betweenClosed(center.offset(-radius, -3, -radius), center.offset(radius, 3, radius))) {
            BlockState state = level.getBlockState(target);
            if (state.is(ModBlocks.INFECTED_LOTUS.get()) || state.is(ModBlocks.INFECTED_SOIL.get()) || state.is(ModBlocks.LOTUS_ROOTS.get())) infected++;
        }
        ServerBossEvent bar = infectionBars.computeIfAbsent(player.getUUID(), id -> {
            ServerBossEvent created = new ServerBossEvent(Component.literal("Разрастание лотоса"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            created.setVisible(false);
            return created;
        });
        if (infected == 0) {
            bar.removePlayer(player);
            bar.setVisible(false);
            return;
        }
        float progress = Mth.clamp(infected / 24.0f, 0.0f, 1.0f);
        int percent = Math.round(progress * 100.0f);
        String phaseName = percent < 25 ? "Цветение" : percent < 50 ? "Захват реки" : percent < 75 ? "Захват ближников" : "Лотосовый мини-биом";
        bar.setName(Component.literal(phaseName + " — " + percent + "%"));
        bar.setProgress(progress);
        bar.addPlayer(player);
        bar.setVisible(true);
    }

    private void spreadFromRegisteredAnchors(net.minecraft.server.MinecraftServer server) {
        for (GlobalPos anchor : new java.util.ArrayList<>(ANCHOR_PHASES.keySet())) {
            ServerLevel level = server.getLevel(anchor.dimension());
            if (level == null || !level.hasChunkAt(anchor.pos())) continue;
            BlockPos source = anchor.pos();
            BlockState state = level.getBlockState(source);
            if (state.is(ModBlocks.LOTUS_HEART.get()) || state.is(ModBlocks.INFECTED_LOTUS.get())) {
                trySpread(level, source);
                if (level.random.nextInt(3) == 0) {
                    level.sendParticles(GREEN, source.getX() + 0.5, source.getY() + 0.15, source.getZ() + 0.5, 4, 0.35, 0.08, 0.35, 0.01);
                }
            }
        }
    }

    private void spreadNearPlayer(ServerLevel level, Player player) {
        int radius = LotusConfig.SPREAD_RADIUS.get();
        BlockPos center = player.blockPosition();
        for (int i = 0; i < 3; i++) {
            BlockPos source = center.offset(RANDOM.nextInt(radius * 2 + 1) - radius, RANDOM.nextInt(7) - 3, RANDOM.nextInt(radius * 2 + 1) - radius);
            BlockState sourceState = level.getBlockState(source);
            if (sourceState.is(ModBlocks.LOTUS_HEART.get()) || sourceState.is(ModBlocks.INFECTED_LOTUS.get()) || sourceState.is(ModBlocks.INFECTED_WATER.get()) || sourceState.is(ModBlocks.INFECTED_SOIL.get())) {
                trySpread(level, source);
                infectLiving(level, source);
            }
        }
    }

    private void trySpread(ServerLevel level, BlockPos source) {
        BlockPos target = source.offset(RANDOM.nextInt(3) - 1, RANDOM.nextInt(3) - 1, RANDOM.nextInt(3) - 1);
        if (LotusConfig.STREAMS_COMPATIBILITY.get() && (level.getFluidState(source).is(Fluids.WATER) || level.getFluidState(source).is(ModFluids.INFECTED_WATER.get()))) {
            // Streams Reflowing changes the water layout; following actual water blocks gives soft compatibility without a hard dependency.
            target = findWaterDownstream(level, source);
        }
        if (!level.hasChunkAt(target)) return;
        BlockState state = level.getBlockState(target);
        if (level.getFluidState(target).is(Fluids.WATER) && level.getFluidState(target).isSource()) {
            // First the infection sends a visible, waterlogged root through the water.
            if (!level.getBlockState(target).is(ModBlocks.LOTUS_ROOTS.get()) && RANDOM.nextInt(3) != 0) {
                level.setBlock(target, ModBlocks.LOTUS_ROOTS.get().defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), 3);
                bloom(level, target, GREEN);
                return;
            }
            // Only a minority of water steps creates a protected surface shoot.
            BlockPos flowerPos = target.above();
            if (level.getBlockState(flowerPos).isAir()) {
                level.setBlock(flowerPos, ModBlocks.LOTUS_SHOOT.get().defaultBlockState(), 3);
                bloom(level, flowerPos, PINK);
            }
            return;
        }
        int phase = phaseForPosition(level, source);
        if (phase >= 3 && (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES))) {
            level.setBlock(target, state.is(BlockTags.LOGS) ? ModBlocks.LOTUS_LOG.get().defaultBlockState() : ModBlocks.LOTUS_LEAVES.get().defaultBlockState(), 3);
            bloom(level, target, GREEN);
            return;
        }
        if (isNaturalGround(state) && Math.abs(target.getY() - source.getY()) <= 1) {
            level.setBlock(target, ModBlocks.INFECTED_SOIL.get().defaultBlockState(), 3);
            if (RANDOM.nextInt(4) == 0) {
                level.setBlock(target.above(), ModBlocks.LOTUS_ROOTS.get().defaultBlockState(), 3);
            }
            bloom(level, target, GREEN);
            return;
        }
        if (state.isAir() && level.getFluidState(target.below()).is(Fluids.WATER)) {
            level.setBlock(target, ModBlocks.INFECTED_LOTUS.get().defaultBlockState(), 3);
            bloom(level, target, PINK);
        }
    }

    private int phaseForPosition(ServerLevel level, BlockPos position) {
        int result = 1;
        for (GlobalPos anchor : ANCHOR_PHASES.keySet()) {
            if (!anchor.dimension().equals(level.dimension())) continue;
            if (anchor.pos().distSqr(position) > 64.0 * 64.0) continue;
            long age = Math.max(0L, level.getGameTime() - ANCHOR_START_TICKS.getOrDefault(anchor, level.getGameTime()));
            result = Math.max(result, age < 2400L ? 1 : age < 7200L ? 2 : age < 14400L ? 3 : 4);
        }
        return result;
    }

    private int countInfection(ServerLevel level, BlockPos center, int radius) {
        int count = 0;
        for (BlockPos target : BlockPos.betweenClosed(center.offset(-radius, -3, -radius), center.offset(radius, 3, radius))) {
            BlockState state = level.getBlockState(target);
            if (state.is(ModBlocks.INFECTED_LOTUS.get()) || state.is(ModBlocks.LOTUS_SHOOT.get()) || state.is(ModBlocks.INFECTED_SOIL.get()) || state.is(ModBlocks.LOTUS_ROOTS.get()) || state.is(ModBlocks.LOTUS_LOG.get()) || state.is(ModBlocks.LOTUS_LEAVES.get())) count++;
        }
        return count;
    }

    private BlockPos findWaterDownstream(ServerLevel level, BlockPos source) {
        var flow = level.getFluidState(source).getFlow(level, source);
        BlockPos best = source;
        double bestScore = -Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(source.offset(-1, -1, -1), source.offset(1, 0, 1))) {
            if (!level.getFluidState(candidate).is(Fluids.WATER)) continue;
            double dx = candidate.getX() - source.getX();
            double dz = candidate.getZ() - source.getZ();
            double score = dx * flow.x + dz * flow.z - Math.max(0, candidate.getY() - source.getY()) * 0.75;
            if (!candidate.equals(source) && score > bestScore) {
                best = candidate.immutable();
                bestScore = score;
            }
        }
        return best.equals(source) ? source.relative(net.minecraft.core.Direction.getRandom(level.random)) : best;
    }

    private boolean isNaturalGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.MUD);
    }

    private void infectLiving(ServerLevel level, BlockPos source) {
        AABB box = new AABB(source).inflate(3.5);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!(entity instanceof Player)) {
                entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(ModEffects.LOTUS_SPORES.get(), 240, 0));
            }
        }
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
        trySpread(level, pos);
        infectLiving(level, pos);
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
        long remaining = PHASE_LENGTH_TICKS - (level.getGameTime() % PHASE_LENGTH_TICKS);
        player.displayClientMessage(Component.literal("До следующей фазы заражения: " + (remaining / 20) + " сек."), false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickItem event) {
        if (event.getItemStack().is(ModItems.CLEANSING_POWDER.get()) && event.getEntity().level() instanceof ServerLevel level) {
            BlockPos pos = event.getEntity().blockPosition();
            for (BlockPos target : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
                if (level.getBlockState(target).is(ModBlocks.INFECTED_SOIL.get()) || level.getBlockState(target).is(ModBlocks.INFECTED_WATER.get()) || level.getBlockState(target).is(ModBlocks.LOTUS_ROOTS.get())) {
                    level.setBlock(target, Blocks.DIRT.defaultBlockState(), 3);
                    level.sendParticles(GREEN, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.01);
                }
            }
            if (!event.getEntity().getAbilities().instabuild) event.getItemStack().shrink(1);
        }
    }
}
