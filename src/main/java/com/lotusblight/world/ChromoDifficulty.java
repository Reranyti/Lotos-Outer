package com.lotusblight.world;

import com.lotusblight.data.LotusChromoData;
import com.lotusblight.map.ChromoStatePacket;
import com.lotusblight.map.NetworkHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/**
 * The Chromo difficulty: above Hard, for the whole world, and once switched on it cannot be switched
 * off again - not by a command either. It is picked in Minecraft's own difficulty selector (see
 * {@code ChromoClient}); the world then runs on Hard underneath, held there and locked, and hostile
 * mobs hit twice as hard, see twice as far and move faster. The special mechanics of the mod's
 * bosses, and the Chromo maps of the desktop fight, key off {@link #isActive}.
 */
public final class ChromoDifficulty {
    private static final UUID DAMAGE_ID = UUID.fromString("5c1e6a52-7f2b-4c59-9d63-0b7d6a1f3c01");
    private static final UUID RANGE_ID = UUID.fromString("5c1e6a52-7f2b-4c59-9d63-0b7d6a1f3c02");
    private static final UUID SPEED_ID = UUID.fromString("5c1e6a52-7f2b-4c59-9d63-0b7d6a1f3c03");

    private ChromoDifficulty() {}

    public static boolean isActive(MinecraftServer server) {
        return LotusChromoData.get(server).isEnabled();
    }

    /** Whether this player may switch the world over: anyone in a single-player world, otherwise an operator. */
    public static boolean mayEnable(ServerPlayer player) {
        return player.getServer().isSingleplayer() || player.hasPermissions(2);
    }

    /** Switches the world to Chromo. Returns false if it already was. */
    public static boolean enable(MinecraftServer server) {
        LotusChromoData data = LotusChromoData.get(server);
        if (data.isEnabled()) return false;
        data.enable();
        hold(server);
        server.getPlayerList().broadcastSystemMessage(Component.literal("Мир переведён на ХРОМО-СЛОЖНОСТЬ. Пути назад нет.")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living && living instanceof Enemy) harden(living);
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
        return true;
    }

    public static void sync(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ChromoStatePacket(isActive(player.getServer())));
    }

    /** Underneath it is Hard, and it stays Hard whatever is typed or clicked. */
    private static void hold(MinecraftServer server) {
        if (server.getWorldData().getDifficulty() != Difficulty.HARD) server.setDifficulty(Difficulty.HARD, true);
        if (!server.getWorldData().isDifficultyLocked()) server.setDifficultyLocked(true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) return;
        if (isActive(event.getServer())) hold(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    /**
     * A world picked as Chromo in the world-creation screen is switched over when it is actually made: the spawn is only
     * created once, for a new world.
     */
    @SubscribeEvent
    public static void onNewWorld(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        boolean wanted = DistExecutor.unsafeRunForDist(() -> () -> com.lotusblight.client.ChromoClient.takePendingNewWorld(), () -> () -> false);
        if (wanted && level.getServer().isSingleplayer()) enable(level.getServer());
    }

    /** Every hostile mob that joins a Chromo world - including ones loaded from the save - gets the modifiers once. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity living) || !(living instanceof Enemy)) return;
        var server = event.getLevel().getServer();
        if (server == null || !isActive(server)) return;
        harden(living);
    }

    private static void harden(LivingEntity mob) {
        add(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "chromo_damage", 1.0);
        add(mob, Attributes.FOLLOW_RANGE, RANGE_ID, "chromo_range", 1.0);
        add(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, "chromo_speed", 0.25);
    }

    private static void add(LivingEntity mob, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null || instance.getModifier(id) != null) return;
        instance.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }
}
