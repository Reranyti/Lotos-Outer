package com.lotusblight.escape;

import com.lotusblight.LotusConfig;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.map.ChaseStatePacket;
import com.lotusblight.map.NetworkHandler;
import net.minecraft.core.BlockPos;
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
 * 1-in-{@link #TRIGGER_CHANCE} chance to grab a random online player. A wall of infection expands
 * outward from wherever they're standing; they have {@link #DURATION_TICKS} to keep ahead of it.
 * Plain walking can't outrun the expansion — sprinting barely can, {@link com.lotusblight.item.VitaminItem}
 * and the crouch+sprint dash burst are what's actually meant to carry a player through it, per the
 * "преобладает скорость" design call.
 *
 * Outcome on being caught: the Lotus "grabs" the player - every item is shaken out onto the ground
 * (bypassing keepInventory on purpose, unlike a normal death) before a lethal, guaranteed-kill
 * impact. Outcome on surviving the full duration: nothing is lost, the event just ends.
 */
public final class LotusChaseEvent {
    private static final int SWEEP_INTERVAL_TICKS = 100;
    private static final int TRIGGER_CHANCE = 1_000_000;
    private static final float TRIGGER_FRACTION = 0.15f;
    private static final int DURATION_TICKS = 20 * 30;
    /** Blocks/tick the danger radius grows - tuned above plain sprint speed (~0.28/tick) on purpose, so outrunning it needs vitamins/the dash burst, not just holding forward. */
    private static final double EXPANSION_BLOCKS_PER_TICK = 0.30;
    private static final int DASH_BURST_TICKS = 12;
    private static final int DASH_AMPLIFIER = 3;
    private static final int DASH_COOLDOWN_TICKS = 20 * 3;

    private record ChaseState(BlockPos origin, long startTick, long dashReadyAtTick) {
        ChaseState withDashUsed(long now) {
            return new ChaseState(origin, startTick, now + DASH_COOLDOWN_TICKS);
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

        active.put(target.getUUID(), new ChaseState(target.blockPosition(), gameTick, gameTick));
        target.displayClientMessage(Component.literal(
                "— Ты чувствуешь, как мир под тобой начинает уходить. Беги."), false);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new ChaseStatePacket(true, DURATION_TICKS));
    }

    private void tickActiveChases(MinecraftServer server, long gameTick) {
        if (active.isEmpty()) return;
        for (UUID uuid : List.copyOf(active.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            ChaseState state = active.get(uuid);
            if (player == null || !player.isAlive()) {
                active.remove(uuid);
                continue;
            }

            long elapsed = gameTick - state.startTick();
            if (elapsed >= DURATION_TICKS) {
                resolveSurvived(player);
                continue;
            }

            double radius = EXPANSION_BLOCKS_PER_TICK * elapsed;
            double distance = Math.sqrt(player.blockPosition().distSqr(state.origin()));
            if (distance <= radius) {
                resolveCaught(player);
                continue;
            }

            if (player.isCrouching() && player.isSprinting() && gameTick >= state.dashReadyAtTick()) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DASH_BURST_TICKS, DASH_AMPLIFIER, false, true, true));
                active.put(uuid, state.withDashUsed(gameTick));
            }
        }
    }

    private void resolveSurvived(ServerPlayer player) {
        active.remove(player.getUUID());
        player.displayClientMessage(Component.literal("— ...Ушёл. В этот раз."), false);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ChaseStatePacket(false, 0));
    }

    /**
     * "лотос берёт его щупальцей и вытрясывает из него все предметы и бросает от землю, смерть от
     * кинетической энергии" - deliberately bypasses keepInventory: every item is dropped as a real
     * world entity BEFORE death (so there is nothing left for keepInventory to preserve), then a
     * guaranteed-lethal impact follows.
     */
    private void resolveCaught(ServerPlayer player) {
        active.remove(player.getUUID());
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
