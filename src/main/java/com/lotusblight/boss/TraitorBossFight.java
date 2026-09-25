package com.lotusblight.boss;

import com.lotusblight.advancement.TraitorBossDefeatedTrigger;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.entity.WorldLotusGuardianWolf;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.TraitorBossThemePacket;
import com.lotusblight.registry.ModEntities;
import com.lotusblight.spread.GuardianManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The traitor-branch boss fight itself - everything from "the arena appears around the player"
 * (see {@link TraitorBossArena}) through 6 waves of mobs to the victory rewards. Reuses
 * {@link GuardianManager}'s regular/heart guardian stats wholesale (now-public
 * {@link GuardianManager#applyGuardianStats}) instead of duplicating those numbers, but tags its
 * own mobs with {@link #BOSS_MOB_TAG} rather than {@code GuardianManager.GUARDIAN_TAG} so none of
 * GuardianManager's unrelated taming/ally/loot/sighting listeners ever fire on them.
 *
 * Tracks every fight entirely in memory (no SavedData) - a server restart mid-fight simply drops
 * the fight, same accepted limitation as {@link TraitorBossArena}'s own protection registry.
 */
public final class TraitorBossFight {

    /** Deliberately NOT GuardianManager.GUARDIAN_TAG - see class javadoc. */
    public static final String BOSS_MOB_TAG = "traitor_boss_mob";

    private static final int REGULAR_PHASE = 3;
    private static final int HEART_PHASE = 4;

    /** Regular/heart guardian counts, world-lotus-guardian count, lotus-undead count - exact wave table from the spec, do not rebalance. */
    private static final int[][] WAVE_TABLE = {
            {8, 0, 0, 0},
            {4, 5, 0, 0},
            {0, 0, 0, 3},
            {0, 4, 2, 2},
            {7, 8, 4, 6},
    };

    private static final int WAVE_ADVANCE_DELAY_TICKS = 60;
    /** Matches GuardianManager.SWEEP_INTERVAL_TICKS - no reason for a different cadence. */
    private static final int BUFF_INTERVAL_TICKS = 100;
    /** Longer than the interval so the vanilla effect never actually expires before the next refresh. */
    private static final int BUFF_DURATION_TICKS = 120;
    private static final int BUFF_AMPLIFIER = 1;
    private static final double BEAM_STEP = 0.5;

    private static final double UNDEAD_MAX_HEALTH = 40.0;
    private static final double UNDEAD_ATTACK_DAMAGE = 6.0;

    private static final class FightState {
        UUID playerId;
        ServerLevel level;
        TraitorBossArena arena;
        int waveIndex;
        List<UUID> currentWaveMobs;
        long nextWaveSpawnTick;
        boolean waitingForNextWave;
        UUID buffedMobId;
        long nextBuffTick;
    }

    private static final Map<UUID, Long> pendingStarts = new HashMap<>();
    private static final Map<UUID, FightState> ACTIVE = new HashMap<>();

    /** Called by GuardianManager.triggerBetrayal right after the incineration effects are applied. */
    public static void scheduleStart(ServerPlayer player, int delayTicks) {
        UUID id = player.getUUID();
        if (pendingStarts.containsKey(id) || ACTIVE.containsKey(id)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        pendingStarts.put(id, server.getTickCount() + (long) delayTicks);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();

        drainPendingStarts(server);
        // Defensive copy - a fight can remove itself from ACTIVE mid-iteration (victory/abort),
        // same as GuardianManager.sweepLevel's own copy of data.allOutbreaks().
        for (FightState state : new ArrayList<>(ACTIVE.values())) {
            tickFight(state);
        }
    }

    private static void drainPendingStarts(MinecraftServer server) {
        if (pendingStarts.isEmpty()) return;
        long tick = server.getTickCount();

        List<UUID> due = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : pendingStarts.entrySet()) {
            if (entry.getValue() <= tick) due.add(entry.getKey());
        }
        for (UUID id : due) {
            pendingStarts.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue; // logged off before the delay elapsed - just drop it
            startFight(player);
        }
    }

    /** Position captured HERE, at the actual start tick - not back when triggerBetrayal ran scheduleStart. */
    private static void startFight(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Built around the block under the player - centered on their feet, the floor and the
        // lotus heart landed in the very cells the player was standing in.
        TraitorBossArena arena = new TraitorBossArena(level, player.blockPosition().below());
        arena.build();
        BlockPos start = arena.playerStartPos();
        player.teleportTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);

        FightState state = new FightState();
        state.playerId = player.getUUID();
        state.level = level;
        state.arena = arena;
        state.waveIndex = 0;
        state.currentWaveMobs = new ArrayList<>();
        state.waitingForNextWave = false;

        ACTIVE.put(state.playerId, state);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TraitorBossThemePacket(true));
        spawnWave(state, 0);
    }

    private static void tickFight(FightState state) {
        MinecraftServer server = state.level.getServer();
        long serverTick = server.getTickCount();
        ServerPlayer player = server.getPlayerList().getPlayer(state.playerId);
        if (player == null || !player.isAlive()) {
            abortFight(state, player);
            return;
        }

        pruneDeadMobs(state);
        tickBuffBeam(state, serverTick);

        if (!state.currentWaveMobs.isEmpty()) return; // wave still has survivors

        if (!state.waitingForNextWave) {
            state.waitingForNextWave = true;
            state.nextWaveSpawnTick = serverTick + WAVE_ADVANCE_DELAY_TICKS;
            return;
        }
        if (serverTick < state.nextWaveSpawnTick) return;

        int nextWaveIndex = state.waveIndex + 1;
        if (nextWaveIndex >= WAVE_TABLE.length) {
            victory(state, player);
            return;
        }
        state.waveIndex = nextWaveIndex;
        state.waitingForNextWave = false;
        spawnWave(state, nextWaveIndex);
    }

    /** No victory rewards - only a full clear reaches {@link #victory}. */
    private static void abortFight(FightState state, ServerPlayer player) {
        if (player != null) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TraitorBossThemePacket(false));
        }
        // Wave mobs are persistent - left alone they'd roam the world forever once the arena is gone.
        for (UUID mobId : state.currentWaveMobs) {
            if (state.level.getEntity(mobId) instanceof LivingEntity mob) {
                mob.discard();
            }
        }
        state.currentWaveMobs.clear();
        state.arena.teardown();
        ACTIVE.remove(state.playerId);
    }

    /**
     * Fights live only in memory, so after a restart any boss mob loaded back from disk belongs to
     * nothing - no wave tracks it and no arena holds it. Spawning always happens while its fight is
     * already in ACTIVE, so an empty ACTIVE means an orphan.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !ACTIVE.isEmpty()) return;
        if (event.getEntity().getTags().contains(BOSS_MOB_TAG)) {
            event.setCanceled(true);
        }
    }

    private static void victory(FightState state, ServerPlayer player) {
        TraitorBossDefeatedTrigger.INSTANCE.trigger(player);
        LotusPlayerState.setDefeatedTraitorBoss(player);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TraitorBossThemePacket(false));
        state.arena.teardown();
        ACTIVE.remove(state.playerId);
    }

    private static void spawnWave(FightState state, int waveIndex) {
        int[] counts = WAVE_TABLE[waveIndex];
        List<UUID> mobs = new ArrayList<>();
        for (int i = 0; i < counts[0]; i++) spawnGuardian(state, mobs, REGULAR_PHASE);
        for (int i = 0; i < counts[1]; i++) spawnGuardian(state, mobs, HEART_PHASE);
        for (int i = 0; i < counts[2]; i++) spawnWorldLotusGuardian(state, mobs);
        for (int i = 0; i < counts[3]; i++) spawnLotusUndead(state, mobs);

        state.currentWaveMobs = mobs;
        state.buffedMobId = null;
        state.nextBuffTick = state.level.getServer().getTickCount(); // pick a beam target on the very next tick
    }

    /** Mirrors GuardianManager.trySpawnGuardian's exact goal-injection/tag/name shape, then reuses its stats via the now-public applyGuardianStats. */
    private static void spawnGuardian(FightState state, List<UUID> mobs, int phase) {
        ServerLevel level = state.level;
        BlockPos pos = state.arena.randomSpawnPos(level.random);
        Wolf wolf = EntityType.WOLF.create(level);
        if (wolf == null) return;

        wolf.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0f, 0.0f);
        wolf.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null, null);
        wolf.addTag(BOSS_MOB_TAG);
        // Set BEFORE applyGuardianStats - its own phase>=4 branch overrides this to "Сердце-страж лотоса".
        wolf.setCustomName(Component.literal("Страж лотоса"));
        wolf.setCustomNameVisible(true);
        wolf.setPersistenceRequired();
        GuardianManager.addHostileGoals(wolf);
        GuardianManager.applyGuardianStats(wolf, phase);

        level.addFreshEntity(wolf);
        mobs.add(wolf.getUUID());
    }

    /** WORLD_LOTUS_GUARDIAN extends Wolf, passive by default - same manual goal injection reasoning as the base tier above. */
    private static void spawnWorldLotusGuardian(FightState state, List<UUID> mobs) {
        ServerLevel level = state.level;
        BlockPos pos = state.arena.randomSpawnPos(level.random);
        WorldLotusGuardianWolf wolf = ModEntities.WORLD_LOTUS_GUARDIAN.get().create(level);
        if (wolf == null) return;

        wolf.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0f, 0.0f);
        wolf.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null, null);
        wolf.addTag(BOSS_MOB_TAG);
        wolf.setCustomName(Component.literal("Страж мирового лотоса"));
        wolf.setCustomNameVisible(true);
        wolf.setPersistenceRequired();
        GuardianManager.addHostileGoals(wolf);

        level.addFreshEntity(wolf);
        mobs.add(wolf.getUUID());
    }

    /** No manual goals - Stray/AbstractSkeleton already registers its own hostile ranged-Weakness-arrow AI, and finalizeSpawn equips its bow. */
    private static void spawnLotusUndead(FightState state, List<UUID> mobs) {
        ServerLevel level = state.level;
        BlockPos pos = state.arena.randomSpawnPos(level.random);
        Stray stray = EntityType.STRAY.create(level);
        if (stray == null) return;

        stray.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0f, 0.0f);
        stray.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null, null);

        var healthAttr = stray.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(UNDEAD_MAX_HEALTH);
            stray.setHealth((float) stray.getMaxHealth());
        }
        var damageAttr = stray.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(UNDEAD_ATTACK_DAMAGE);
        }
        stray.addTag(BOSS_MOB_TAG);
        stray.setCustomName(Component.literal("Нежить лотоса"));
        stray.setCustomNameVisible(true);
        stray.setPersistenceRequired();

        level.addFreshEntity(stray);
        mobs.add(stray.getUUID());
    }

    private static void pruneDeadMobs(FightState state) {
        state.currentWaveMobs.removeIf(id -> aliveLivingOrNull(state.level, id) == null);
    }

    private static void tickBuffBeam(FightState state, long serverTick) {
        LivingEntity buffed = state.buffedMobId == null ? null : aliveLivingOrNull(state.level, state.buffedMobId);
        if (buffed == null) state.buffedMobId = null; // died since last tick - beam stops immediately

        if (buffed == null || serverTick >= state.nextBuffTick) {
            buffed = selectBuffTarget(state, serverTick);
        }
        if (buffed != null) {
            renderBeam(state, buffed);
        }
    }

    private static LivingEntity selectBuffTarget(FightState state, long serverTick) {
        state.nextBuffTick = serverTick + BUFF_INTERVAL_TICKS;
        if (state.currentWaveMobs.isEmpty()) {
            state.buffedMobId = null;
            return null;
        }

        UUID pick = state.currentWaveMobs.get(state.level.random.nextInt(state.currentWaveMobs.size()));
        LivingEntity living = aliveLivingOrNull(state.level, pick);
        if (living == null) return null; // keep whatever was already buffed (if anything) until the next reselect

        state.buffedMobId = pick;
        living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BUFF_DURATION_TICKS, BUFF_AMPLIFIER));
        living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, BUFF_DURATION_TICKS, BUFF_AMPLIFIER));
        return living;
    }

    /** Approximates an Ender-Crystal-style beam with a dense WITCH particle stream - explicitly accepted as such by the spec. */
    private static void renderBeam(FightState state, LivingEntity target) {
        Vec3 from = Vec3.atCenterOf(state.arena.lotusPos());
        Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < BEAM_STEP) return;

        Vec3 step = delta.scale(BEAM_STEP / length);
        Vec3 point = from;
        int steps = (int) (length / BEAM_STEP);
        for (int i = 0; i < steps; i++) {
            point = point.add(step);
            state.level.sendParticles(ParticleTypes.WITCH, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static LivingEntity aliveLivingOrNull(ServerLevel level, UUID id) {
        if (!(level.getEntity(id) instanceof LivingEntity living) || !living.isAlive()) return null;
        return living;
    }

    private TraitorBossFight() {
    }
}
