package com.lotusblight.escape;

import com.lotusblight.LotusConfig;
import com.lotusblight.data.LotusLabSavedData;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.map.ChaseStatePacket;
import com.lotusblight.map.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * "Побег от лотоса" — a PERSISTENT lab (see {@link LotusChaseStructure} / {@link LotusLabSavedData}),
 * built once at a fixed spot and sealed shut. "он не может попасть туда даже если найдёт его до 15
 * процентов" - the door only opens once the infection covers roughly 15% of the world (see
 * {@link LotusConfig#WORLD_INFECTION_REFERENCE}). Once open, whichever player walks up to the
 * start-trigger a few steps inside begins their own run - the entrance seals again behind them
 * ("не может пойти назад") until they resolve it, one runner at a time.
 *
 * A junction partway in has two branches - the real one lit yellow, a decoy dead end lit red. They
 * have {@link #DURATION_TICKS} to physically reach the exit trigger; obstacles ("упавшие полки" -
 * cobweb) periodically appear along the correct path, escalating partway through per the theme
 * track's own structure, and {@link com.lotusblight.item.VitaminItem}'s Speed burst / the
 * crouch+sprint dash are what's meant to carry a player through in time.
 *
 * Outcome on running out of time: the Lotus "grabs" the player - every item is shaken out onto the
 * ground first (bypasses keepInventory on purpose, unlike a normal death) before a lethal,
 * guaranteed-kill impact. Outcome on reaching the exit: nothing is lost, the door reopens for the
 * next attempt.
 */
public final class LotusChaseEvent {
    private static final int SWEEP_INTERVAL_TICKS = 100;
    private static final float TRIGGER_FRACTION = 0.15f;
    // Timed against lotus_chase_theme.ogg's own structure: 0:11 the track settles into the escape
    // proper, 0:23 it escalates hard, 1:50 is the actual deadline, 2:14 is the track's full length
    // (the 1:50-2:14 tail only ever plays as a successful-escape outro, see ChaseStatePacket).
    private static final int GRACE_END_TICKS = 20 * 11;
    private static final int PHASE2_START_TICKS = 20 * 23;
    private static final int DURATION_TICKS = 20 * 110;
    private static final int DASH_BURST_TICKS = 12;
    private static final int DASH_AMPLIFIER = 3;
    private static final int DASH_COOLDOWN_TICKS = 20 * 3;
    /** "1 фаза более менее лёгкая" / "вторая фаза... сильное усложнение" - obstacles roughly triple in frequency once phase 2 starts. */
    private static final int PHASE1_OBSTACLE_INTERVAL_TICKS = 20 * 6;
    private static final int PHASE2_OBSTACLE_INTERVAL_TICKS = 20 * 2;
    /** How far from world spawn (X+, same Y as spawn) the lab gets carved - arbitrary but fixed, so it always ends up at the same real spot for a given world. */
    private static final int LAB_OFFSET_FROM_SPAWN = 48;

    private static LotusChaseEvent instance;

    private LotusChaseStructure structure;
    private UUID runnerUuid;
    private static final int FORFEIT_HIT_DELAY_TICKS = 80;
    private final java.util.Map<UUID, Long> pendingForfeitHits = new java.util.HashMap<>();
    private long runnerStartTick;
    private long dashReadyAtTick;
    private long nextObstacleAtTick;

    public LotusChaseEvent() {
        instance = this;
    }

    /** For /lotus chase test commands (see com.lotusblight.command.LotusCommands) - null only if the mod's own registration in LotusBlight never ran, which should never happen. */
    public static LotusChaseEvent get() {
        return instance;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        long gameTick = overworld.getGameTime();
        // Before the lab check - it doesn't depend on the lab being loaded at all.
        deliverForfeitHits(server);

        ensureLabExists(overworld);
        if (structure == null) return; // area not chunk-loaded yet - retry next tick, see ensureLabExists

        tickActiveRun(server, gameTick);

        if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0) return;
        maybeUnlock(server, overworld);
        checkForNewRunner(overworld);
    }

    /**
     * Deliberately waits for {@link ServerLevel#hasChunkAt} before touching a single block - this
     * used to build() unconditionally the moment the overworld ticked once, and if the lab's spot
     * (spawn + {@link #LAB_OFFSET_FROM_SPAWN}) wasn't already loaded (no spawn-chunk keep-alive,
     * or nobody near it yet), Level#setBlock's own getChunk() call would force a synchronous chunk
     * load/generation and deadlock the server tick thread against itself - the exact freeze a
     * player hit after crossing the unlock threshold nowhere near the lab. LotusChaseStructure's
     * own place()/sealEntrance()/unsealEntrance()/dropObstacle() now also guard individually, since
     * the lab's footprint can span more than one chunk.
     */
    private void ensureLabExists(ServerLevel overworld) {
        if (structure != null) return;
        LotusLabSavedData labData = LotusLabSavedData.get(overworld);
        if (!labData.isBuilt()) {
            BlockPos spawn = overworld.getSharedSpawnPos();
            int x = spawn.getX() + LAB_OFFSET_FROM_SPAWN;
            int z = spawn.getZ();
            if (!overworld.hasChunkAt(new BlockPos(x, spawn.getY(), z))) return;
            // Used to reuse spawn.getY() outright - fine right at spawn itself, but LAB_OFFSET_FROM_SPAWN
            // (48 blocks) is easily a different biome/elevation entirely (a mesa canyon, a lake, a
            // cliff), so the lab's fixed shell ended up built floating over or half-submerged in
            // whatever was actually there instead of sitting on real ground. Sample the real surface
            // height at the lab's own X/Z instead of trusting spawn's.
            int surfaceY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos entrance = new BlockPos(x, surfaceY, z);
            if (!overworld.hasChunkAt(entrance)) return;
            Direction facing = Direction.EAST;
            LotusChaseStructure built = new LotusChaseStructure(overworld, entrance, facing, overworld.random);
            if (!built.isFootprintLoaded()) return;
            built.build();
            labData.markBuilt(entrance, facing, built.correctIsLeft());
            structure = built;
        } else {
            if (!overworld.hasChunkAt(labData.entrance())) return;
            LotusChaseStructure rebuilt = new LotusChaseStructure(overworld, labData.entrance(), labData.facing(), labData.correctIsLeft());
            if (!rebuilt.isFootprintLoaded()) return;
            rebuilt.build();
            if (labData.isUnlocked() && runnerUuid == null) {
                rebuilt.unsealEntrance();
            }
            structure = rebuilt;
        }
    }

    private void maybeUnlock(MinecraftServer server, ServerLevel overworld) {
        LotusLabSavedData labData = LotusLabSavedData.get(overworld);
        if (labData.isUnlocked()) return;

        long totalInfected = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (OutbreakRecord outbreak : OutbreakSavedData.get(level).allOutbreaks()) {
                totalInfected += outbreak.infectedBlockCount();
            }
        }
        long threshold = (long) (LotusConfig.WORLD_INFECTION_REFERENCE.get() * TRIGGER_FRACTION);
        if (totalInfected < threshold) return;

        labData.setUnlocked(true);
        structure.unsealEntrance();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(Component.literal(
                    "— Где-то в мире дверь, которая была заперта, только что открылась."), false);
        }
    }

    private void checkForNewRunner(ServerLevel overworld) {
        if (runnerUuid != null) return;
        LotusLabSavedData labData = LotusLabSavedData.get(overworld);
        if (!labData.isUnlocked()) return;

        for (ServerPlayer player : overworld.players()) {
            if (structure.isAtStartTrigger(player.blockPosition())) {
                startRun(player, overworld.getGameTime());
                return;
            }
        }
    }

    private void startRun(ServerPlayer player, long gameTick) {
        structure.resetForNextRun();
        structure.sealEntrance();
        runnerUuid = player.getUUID();
        runnerStartTick = gameTick;
        dashReadyAtTick = gameTick;
        nextObstacleAtTick = gameTick + GRACE_END_TICKS;

        player.displayClientMessage(Component.literal(
                "— Ты чувствуешь, как мир под тобой начинает уходить. Беги. Ищи жёлтый свет."), false);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ChaseStatePacket(ChaseStatePacket.State.STARTED, DURATION_TICKS));
    }

    private void tickActiveRun(MinecraftServer server, long gameTick) {
        if (runnerUuid == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(runnerUuid);
        if (player == null || !player.isAlive()) {
            endRun();
            return;
        }

        long elapsed = gameTick - runnerStartTick;
        if (elapsed >= DURATION_TICKS) {
            resolveCaught(player);
            return;
        }
        if (structure.isAtExitTrigger(player.blockPosition())) {
            resolveSurvived(player);
            return;
        }

        if (player.isCrouching() && player.isSprinting() && gameTick >= dashReadyAtTick) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DASH_BURST_TICKS, DASH_AMPLIFIER, false, true, true));
            dashReadyAtTick = gameTick + DASH_COOLDOWN_TICKS;
        }

        if (elapsed >= GRACE_END_TICKS && gameTick >= nextObstacleAtTick) {
            structure.dropObstacle(player.blockPosition().relative(player.getDirection(), 3));
            int interval = elapsed >= PHASE2_START_TICKS ? PHASE2_OBSTACLE_INTERVAL_TICKS : PHASE1_OBSTACLE_INTERVAL_TICKS;
            nextObstacleAtTick = gameTick + interval;
        }
    }

    /**
     * "не может пойти назад" - quitting mid-run used to just end it with nothing lost, a free way out
     * of the one outcome the chase is built around. Leaving counts as being caught.
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (runnerUuid == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!runnerUuid.equals(player.getUUID()) || !player.isAlive()) return;
        endRun();
        // Killing a player in the middle of being removed from the server isn't safe - drop the
        // inventory now, where they were caught, and deliver the hit on their next login.
        if (player.level() instanceof ServerLevel level) {
            shakeOutInventory(level, player);
        }
        LotusPlayerState.setChaseForfeited(player, true);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !LotusPlayerState.hasChaseForfeited(player)) return;
        // A freshly joined player is spawn-invulnerable for ~3 seconds and fall damage is simply
        // dropped then - wait it out instead of hitting straight away.
        pendingForfeitHits.put(player.getUUID(), event.getEntity().getServer().getTickCount() + (long) FORFEIT_HIT_DELAY_TICKS);
    }

    private void deliverForfeitHits(MinecraftServer server) {
        if (pendingForfeitHits.isEmpty()) return;
        var it = pendingForfeitHits.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (server.getTickCount() < entry.getValue()) continue;
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !LotusPlayerState.hasChaseForfeited(player)) continue;
            LotusPlayerState.setChaseForfeited(player, false);
            player.hurt(player.damageSources().fall(), Float.MAX_VALUE);
        }
    }

    private void endRun() {
        runnerUuid = null;
        structure.unsealEntrance();
    }

    private void resolveSurvived(ServerPlayer player) {
        endRun();
        player.displayClientMessage(Component.literal("— ...Ушёл. В этот раз."), false);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ChaseStatePacket(ChaseStatePacket.State.SURVIVED, 0));
    }

    /**
     * "лотос берёт его щупальцей и вытрясывает из него все предметы и бросает от землю, смерть от
     * кинетической энергии" - deliberately bypasses keepInventory: every item is dropped as a real
     * world entity BEFORE death (so there is nothing left for keepInventory to preserve), then a
     * guaranteed-lethal impact follows.
     */
    private void resolveCaught(ServerPlayer player) {
        endRun();
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ChaseStatePacket(ChaseStatePacket.State.CAUGHT, 0));
        if (!(player.level() instanceof ServerLevel level)) return;

        shakeOutInventory(level, player);
        player.hurt(player.damageSources().fall(), Float.MAX_VALUE);
    }

    private void shakeOutInventory(ServerLevel level, ServerPlayer player) {
        var inventory = player.getInventory();
        dropAll(level, player, inventory.items);
        dropAll(level, player, inventory.armor);
        dropAll(level, player, inventory.offhand);
        inventory.clearContent();
    }

    private void dropAll(ServerLevel level, ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            ItemEntity drop = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack.copy());
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
    }

    // ---- Testing hooks (see com.lotusblight.command.LotusCommands) ----------------------------

    /** May return null if the lab's chunk isn't loaded yet (see ensureLabExists) - the caller commands report that instead of touching a still-null structure. */
    public BlockPos labEntrance(ServerLevel overworld) {
        ensureLabExists(overworld);
        return structure == null ? null : structure.entrance();
    }

    public void forceUnlock(ServerLevel overworld) {
        ensureLabExists(overworld);
        LotusLabSavedData labData = LotusLabSavedData.get(overworld);
        labData.setUnlocked(true);
        if (structure != null && runnerUuid == null) structure.unsealEntrance();
    }

    public String statusReport(ServerLevel overworld) {
        ensureLabExists(overworld);
        LotusLabSavedData labData = LotusLabSavedData.get(overworld);
        String labPos = structure == null ? "chunk not loaded yet" : structure.entrance().toShortString();
        return String.format("lab=%s unlocked=%b runner=%s", labPos, labData.isUnlocked(), runnerUuid);
    }
}
