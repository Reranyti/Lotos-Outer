package com.lotusblight.spread;

import com.lotusblight.LotusConfig;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * "Мобы: заражённые варианты существ и страж растения" (ALPHA_SCOPE.md) — the
 * one row of the alpha scope that was never built. Without this, an outbreak
 * was pure block-conversion tug-of-war: nothing ever opposed a player standing
 * next to a mature outbreak and cleansing it block by block. A mature (phase
 * &gt;= {@link #MIN_GUARDIAN_PHASE}) outbreak now keeps a small number of
 * hostile guardians nearby, giving RESISTANCE players (and anyone else who
 * gets close) an actual reason to be careful instead of a purely passive
 * garden to tidy up.
 *
 * Reuses vanilla {@link Wolf} instead of a fully custom entity: no new
 * model/texture pipeline needed for a first pass. A plain {@link Wolf} is
 * tameable and passive-until-provoked by default — neither fits a guardian —
 * so this adds hostile-targeting goals directly onto the instance (Mob's
 * {@code goalSelector}/{@code targetSelector} are public) and separately
 * cancels any interaction with a tagged guardian ({@link #onInteract} below)
 * so it can never actually be tamed. Tagged with {@link #GUARDIAN_TAG} so the
 * sweep can count existing guardians per outbreak without any extra
 * bookkeeping of its own.
 *
 * Follows the same throttling shape as {@link com.lotusblight.spread.roots.RootGrowthEngine}:
 * a periodic sweep bounded by active player range, at most one spawn per
 * outbreak per sweep.
 */
public final class GuardianManager {

    public static final String GUARDIAN_TAG = "lotus_guardian";
    private static final String GUARDIAN_PHASE_KEY = "LotusGuardianPhase";
    private static final int PHASE3_POWDER_DROPS = 2;
    private static final int PHASE4_POWDER_DROPS = 5;
    /** 1-in-8 per guardian kill — rare enough to feel like a real find, not a guaranteed second drop alongside the powder. */
    private static final int SCIENTIST_PAGE_DROP_CHANCE = 8;

    private static final int SWEEP_INTERVAL_TICKS = 100;
    private static final int MIN_GUARDIAN_PHASE = 3;
    private static final int MAX_GUARDIANS_PER_OUTBREAK = 2;
    private static final double GUARDIAN_CHECK_RADIUS = 20.0;
    private static final int SPAWN_SEARCH_RADIUS = 10;
    private static final int SPAWN_SEARCH_ATTEMPTS = 6;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            sweepLevel(level);
        }
    }

    private void sweepLevel(ServerLevel level) {
        OutbreakSavedData data = OutbreakSavedData.get(level);
        double activeBlocks = LotusConfig.ACTIVE_CHUNK_RADIUS.get() * 16.0;
        double activeRangeSq = activeBlocks * activeBlocks;

        for (OutbreakRecord outbreak : new ArrayList<>(data.allOutbreaks())) {
            if (outbreak.phase() < MIN_GUARDIAN_PHASE) continue;
            if (!level.hasChunkAt(outbreak.pos())) continue;
            // Same rule InfectionSpreadEngine#isInBlessingBiome already applies to spread itself -
            // the blessing biome is the infection's one sanctuary from spread, so it shouldn't be
            // patrolled by the infection's own guardians either.
            if (level.getBiome(outbreak.pos()).is(com.lotusblight.registry.ModBiomes.BLESSING_BIOME)) continue;
            if (!withinActiveRange(level, outbreak.pos(), activeRangeSq)) continue;
            if (countNearbyGuardians(level, outbreak.pos()) >= MAX_GUARDIANS_PER_OUTBREAK) continue;

            trySpawnGuardian(level, outbreak);
        }
    }

    private boolean withinActiveRange(ServerLevel level, BlockPos pos, double rangeSq) {
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) <= rangeSq) return true;
        }
        return false;
    }

    private int countNearbyGuardians(ServerLevel level, BlockPos anchor) {
        var box = new net.minecraft.world.phys.AABB(anchor).inflate(GUARDIAN_CHECK_RADIUS);
        List<Wolf> nearby = level.getEntitiesOfClass(Wolf.class, box, e -> e.getTags().contains(GUARDIAN_TAG));
        return nearby.size();
    }

    private void trySpawnGuardian(ServerLevel level, OutbreakRecord outbreak) {
        BlockPos spawnPos = findSpawnPos(level, outbreak.pos());
        if (spawnPos == null) return;

        Wolf wolf = EntityType.WOLF.create(level);
        if (wolf == null) return;

        wolf.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.random.nextFloat() * 360.0f, 0.0f);
        wolf.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null, null);
        wolf.addTag(GUARDIAN_TAG);
        wolf.setCustomName(Component.literal("Страж лотоса"));
        wolf.setCustomNameVisible(true);
        wolf.setPersistenceRequired();
        // Vanilla Wolf only fights back once provoked - a guardian needs to actively hunt players
        // on sight instead, so these are added on top of (not instead of) Wolf's own default goals.
        wolf.goalSelector.addGoal(2, new MeleeAttackGoal(wolf, 1.0, true));
        // A player who has joined the lotus (ALLIANCE branch) is helping this outbreak grow - see
        // InfectionSpreadEngine#branchInfluence - so its own guardians recognizing and sparing them
        // is the other half of that same choice actually mattering, not just a spread-rate number.
        wolf.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(wolf, Player.class, true,
                target -> target instanceof Player player && !LotusPlayerState.hasJoinedLotus(player)));
        applyGuardianStats(wolf, outbreak.phase());
        // Read back by onDrops below - a plain Wolf has no phase of its own, so it has to be
        // stashed somewhere that survives to death.
        wolf.getPersistentData().putInt(GUARDIAN_PHASE_KEY, outbreak.phase());

        level.addFreshEntity(wolf);
    }

    /** Vanilla Wolf baseline, used as the phase-3 (fresh mature outbreak) tier. */
    private static final double PHASE3_MAX_HEALTH = 8.0;
    private static final double PHASE3_ATTACK_DAMAGE = 4.0;
    /** Phase 4 (mini-biome) guardians read as a real escalation, not just a bigger flower bed. */
    private static final double PHASE4_MULTIPLIER = 2.0;

    private void applyGuardianStats(Wolf guardian, int phase) {
        double multiplier = phase >= 4 ? PHASE4_MULTIPLIER : 1.0;

        var healthAttr = guardian.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(PHASE3_MAX_HEALTH * multiplier);
            guardian.setHealth((float) guardian.getMaxHealth());
        }
        var damageAttr = guardian.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(PHASE3_ATTACK_DAMAGE * multiplier);
        }

        if (phase >= 4) {
            // Distinct name so a player can tell the more dangerous tier apart before engaging.
            guardian.setCustomName(Component.literal("Сердце-страж лотоса"));
        }
    }

    /**
     * A plain Wolf drops nothing on death. Killing a guardian is the only source of combat risk
     * this mod has, so it needs a payoff - dropping {@link ModItems#CLEANSING_POWDER}, the RESISTANCE
     * branch's own anti-infection tool, closes the loop: fighting off a guardian directly funds
     * pushing its outbreak back, instead of powder only ever coming from the villager trade.
     */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf) || !wolf.getTags().contains(GUARDIAN_TAG)) return;
        int phase = wolf.getPersistentData().getInt(GUARDIAN_PHASE_KEY);
        int count = phase >= 4 ? PHASE4_POWDER_DROPS : PHASE3_POWDER_DROPS;
        ItemEntity drop = new ItemEntity(wolf.level(), wolf.getX(), wolf.getY(), wolf.getZ(),
                new ItemStack(ModItems.CLEANSING_POWDER.get(), count));
        event.getDrops().add(drop);

        // Separate low-probability roll, not a second guaranteed drop alongside the powder above -
        // "Страницы дневника Объекта Ноль" (see ScientistPageItem/ObjectZeroPages) needs to read as
        // a rare find from actually fighting a guardian, the mod's one source of combat risk, not
        // just another guaranteed reward that dilutes the powder's own payoff.
        if (wolf.level().getRandom().nextInt(SCIENTIST_PAGE_DROP_CHANCE) == 0) {
            ItemEntity pageDrop = new ItemEntity(wolf.level(), wolf.getX(), wolf.getY(), wolf.getZ(),
                    com.lotusblight.item.ScientistPageItem.createRandomStack(wolf.level().getRandom()));
            event.getDrops().add(pageDrop);
        }
    }

    /**
     * A tagged guardian is a hostile Wolf, not a pet - block every interaction with it (taming
     * with bones, sitting, breeding, etc. all go through this same entry point on a Wolf) instead
     * of letting a player right-click it into becoming a tame ally mid-fight.
     */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Wolf wolf && wolf.getTags().contains(GUARDIAN_TAG)) {
            event.setCanceled(true);
        }
    }

    /** A flat, air-above-solid-ground spot within radius of the anchor, avoiding water/lava. */
    private BlockPos findSpawnPos(ServerLevel level, BlockPos anchor) {
        for (int i = 0; i < SPAWN_SEARCH_ATTEMPTS; i++) {
            int dx = level.random.nextInt(SPAWN_SEARCH_RADIUS * 2 + 1) - SPAWN_SEARCH_RADIUS;
            int dz = level.random.nextInt(SPAWN_SEARCH_RADIUS * 2 + 1) - SPAWN_SEARCH_RADIUS;
            BlockPos candidate = anchor.offset(dx, 0, dz);
            if (!level.hasChunkAt(candidate)) continue;
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, candidate.getX(), candidate.getZ());
            BlockPos ground = new BlockPos(candidate.getX(), surfaceY - 1, candidate.getZ());
            BlockPos above = ground.above();
            if (level.getBlockState(ground).isAir() || level.getFluidState(ground).isSource()) continue;
            if (level.getBlockState(ground).is(Blocks.LAVA) || level.getFluidState(ground).is(net.minecraft.world.level.material.Fluids.LAVA)) continue;
            if (!level.getBlockState(above).isAir()) continue;
            return above.immutable();
        }
        return null;
    }
}
