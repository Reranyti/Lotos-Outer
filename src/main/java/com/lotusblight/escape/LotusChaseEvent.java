package com.lotusblight.escape;

import com.lotusblight.LotusConfig;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Побег от лотоса" — a rare, world-scale event: once the infection covers roughly 15% of the
 * world (see {@link LotusConfig#WORLD_INFECTION_REFERENCE}), each sweep additionally rolls a
 * 1-in-{@link #TRIGGER_CHANCE} chance to grab a random online player. A sealed lab corridor
 * ({@link LotusChaseStructure}) is built around them and they're placed at its entrance - one
 * branch at the junction leads to the real exit (yellow-lit), the other is a decoy dead end
 * (red-lit). They have {@link #DURATION_TICKS} to physically reach the exit trigger; obstacles
 * ("упавшие полки" - cobweb) periodically appear along the correct path to slow them down, and
 * {@link com.lotusblight.item.VitaminItem}'s Speed burst / the crouch+sprint dash are what's meant
 * to carry a player through in time.
 *
 * Outcome on running out of time: the Lotus "grabs" the player - every item is shaken out onto the
 * ground first (bypasses keepInventory on purpose, unlike a normal death) before a lethal,
 * guaranteed-kill impact. Outcome on reaching the exit: nothing is lost, the structure tears down.
 */
public final class LotusChaseEvent {
    private static final int SWEEP_INTERVAL_TICKS = 100;
    private static final int TRIGGER_CHANCE = 1_000_000;
    private static final float TRIGGER_FRACTION = 0.15f;
    private static final int DURATION_TICKS = 20 * 45;
    private static final int DASH_BURST_TICKS = 12;
    private static final int DASH_AMPLIFIER = 3;
    private static final int DASH_COOLDOWN_TICKS = 20 * 3;
    private static final int OBSTACLE_INTERVAL_TICKS = 20 * 4;

    private static final class ChaseState {
        final LotusChaseStructure structure;
        final long startTick;
        long dashReadyAtTick;
        long nextObstacleAtTick;

        ChaseState(LotusChaseStructure structure, long startTick) {
            this.structure = structure;
            this.startTick = startTick;
            this.dashReadyAtTick = startTick;
            this.nextObstacleAtTick = startTick + OBSTACLE_INTERVAL_TICKS;
        }
    }

    private final Map<UUID, ChaseState> active = new HashMap<>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long gameTick = server.overworld().getGameTime();

        tickActiveChases(server, gameTick);

        if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0) return;
        maybeStartNewChase(server, gameTick);
    }

    private void maybeStartNewChase(MinecraftServer server, long gameTick) {
        long totalInfected = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (OutbreakRecord outbreak : OutbreakSavedData.get(level).allOutbreaks()) {
                totalInfected += outbreak.infectedBlockCount();
            }
        }
        long threshold = (long) (LotusConfig.WORLD_INFECTION_REFERENCE.get() * TRIGGER_FRACTION);
        if (totalInfected < threshold) return;
        if (server.overworld().getRandom().nextInt(TRIGGER_CHANCE) != 0) return;

        List<ServerPlayer> candidates = server.getPlayerList().getPlayers().stream()
                .filter(p -> !active.containsKey(p.getUUID()))
                .toList();
        if (candidates.isEmpty()) return;
        ServerPlayer target = candidates.get(server.overworld().getRandom().nextInt(candidates.size()));
        if (!(target.level() instanceof ServerLevel level)) return;

        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(level.random);
        BlockPos entrance = target.blockPosition();
        LotusChaseStructure structure = new LotusChaseStructure(level, entrance, facing, level.random);
        structure.build();
        target.teleportTo(entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5);

        active.put(target.getUUID(), new ChaseState(structure, gameTick));
        target.displayClientMessage(Component.literal(
                "— Ты чувствуешь, как мир под тобой начинает уходить. Беги. Ищи жёлтый свет."), false);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new ChaseStatePacket(true, DURATION_TICKS));
    }

    private void tickActiveChases(MinecraftServer server, long gameTick) {
        if (active.isEmpty()) return;
        for (UUID uuid : List.copyOf(active.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            ChaseState state = active.get(uuid);
            if (player == null || !player.isAlive()) {
                if (state != null) state.structure.teardown();
                active.remove(uuid);
                continue;
            }

            long elapsed = gameTick - state.startTick;
            if (elapsed >= DURATION_TICKS) {
                resolveCaught(player, state);
                continue;
            }
            if (player.blockPosition().equals(state.structure.exitTrigger())
                    || player.blockPosition().equals(state.structure.exitTrigger().below())) {
                resolveSurvived(player, state);
                continue;
            }

            if (player.isCrouching() && player.isSprinting() && gameTick >= state.dashReadyAtTick) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DASH_BURST_TICKS, DASH_AMPLIFIER, false, true, true));
                state.dashReadyAtTick = gameTick + DASH_COOLDOWN_TICKS;
            }

            if (gameTick >= state.nextObstacleAtTick) {
                state.structure.dropObstacle(player.blockPosition().relative(player.getDirection(), 3));
                state.nextObstacleAtTick = gameTick + OBSTACLE_INTERVAL_TICKS;
            }
        }
    }

    private void resolveSurvived(ServerPlayer player, ChaseState state) {
        active.remove(player.getUUID());
        state.structure.teardown();
        player.displayClientMessage(Component.literal("— ...Ушёл. В этот раз."), false);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ChaseStatePacket(false, 0));
    }

    /**
     * "лотос берёт его щупальцей и вытрясывает из него все предметы и бросает от землю, смерть от
     * кинетической энергии" - deliberately bypasses keepInventory: every item is dropped as a real
     * world entity BEFORE death (so there is nothing left for keepInventory to preserve), then a
     * guaranteed-lethal impact follows.
     */
    private void resolveCaught(ServerPlayer player, ChaseState state) {
        active.remove(player.getUUID());
        state.structure.teardown();
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ChaseStatePacket(false, 0));
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
}
