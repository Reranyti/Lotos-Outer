package com.lotusblight.entity;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.map.HonchoPromptPacket;
import com.lotusblight.map.NetworkHandler;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * After the fight on the desktop, when the player is back in the game: the same portal opens in front of them and
 * Honcho - thrown out of it - tumbles out, dazed. He doesn't remember anything, says hello, and waits for a hand;
 * then he asks whether he may come along, and once the player agrees he goes with them. The player is held in place
 * and unhurt until it is over.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HonchoReturnScene {
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.62f, 0.4f, 1.0f), 1.3f);
    private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.4f, 0.9f, 1.0f), 1.0f);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1f, 1f, 1f), 1.6f);

    private static final int PORTAL_OPEN = 50, HONCHO_OUT = 60, PORTAL_CLOSE = 150, PORTAL_GONE = 190;
    private static final int SAY_1 = 85, SAY_2 = 150, ASK_HAND = 200;
    private static final double ABORT_DISTANCE = 24.0;

    private enum Stage { PORTAL, WAIT_HAND, HAND_TAKEN, WAIT_YES, SHAKE, DONE }

    private static final class Scene {
        final UUID playerId;
        UUID honchoId;
        Vec3 portal;
        Vec3 axis;                      // across the portal, horizontally
        Stage stage = Stage.PORTAL;
        int ticks, stageTicks;
        boolean said1, said2, portalFading;

        Scene(UUID playerId) {
            this.playerId = playerId;
        }
    }

    private static final Map<UUID, Scene> SCENES = new HashMap<>();

    private HonchoReturnScene() {}

    /** Starts the scene for a player who has just come back. Does nothing if Honcho is gone from this world or busy. */
    public static void begin(ServerPlayer player) {
        if (SCENES.containsKey(player.getUUID()) || HonchoMeetingManager.isInMeeting(player)) return;
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Scene s = new Scene(player.getUUID());
        s.portal = player.position().add(flat.scale(4.0)).add(0, 1.7, 0);
        s.axis = new Vec3(-flat.z, 0, flat.x);
        SCENES.put(player.getUUID(), s);
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, 0.7f, 1.4f);
    }

    /** Ends every running scene without a result (Honcho was reset). */
    public static void cancelAll(MinecraftServer server) {
        for (Scene s : new ArrayList<>(SCENES.values())) {
            finish(s, server.getPlayerList().getPlayer(s.playerId), false);
        }
    }

    public static boolean isInScene(ServerPlayer player) {
        return SCENES.containsKey(player.getUUID());
    }

    /** The player pressed the window's button. Returns whether a scene took it. */
    public static boolean handleChoice(ServerPlayer player) {
        Scene s = SCENES.get(player.getUUID());
        if (s == null) return false;
        HonchoEntity honcho = honcho(player, s);
        if (honcho == null) return true;
        if (s.stage == Stage.WAIT_HAND) {
            setStage(s, Stage.HAND_TAKEN);
            honcho.setScene(HonchoEntity.SCENE_DUST);
            HonchoSpeech.say(player, "— Спасибо.");
        } else if (s.stage == Stage.WAIT_YES) {
            setStage(s, Stage.SHAKE);
            honcho.setScene(HonchoEntity.SCENE_SHAKE);
            HonchoSpeech.say(player, "— Спасибо...");
        }
        return true;
    }

    private static void setStage(Scene s, Stage stage) {
        s.stage = stage;
        s.stageTicks = 0;
    }

    private static HonchoEntity honcho(ServerPlayer player, Scene s) {
        if (s.honchoId == null) return null;
        return player.serverLevel().getEntity(s.honchoId) instanceof HonchoEntity h && h.isAlive() ? h : null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SCENES.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (Scene s : new ArrayList<>(SCENES.values())) tick(server, s);
    }

    private static void tick(MinecraftServer server, Scene s) {
        ServerPlayer player = server.getPlayerList().getPlayer(s.playerId);
        if (player == null || !player.isAlive()) {
            finish(s, player, false);
            return;
        }
        ServerLevel level = player.serverLevel();
        s.ticks++;
        s.stageTicks++;
        // Held in place: the player does not walk until it is over.
        if (s.ticks % 10 == 1) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 6, false, false, false));
        }
        portalParticles(level, s);

        HonchoEntity honcho = honcho(player, s);
        if (s.ticks == HONCHO_OUT) {
            HonchoEntity h = HonchoMeetingManager.summonTo(player);
            if (h == null) {
                finish(s, player, false);
                return;
            }
            s.honchoId = h.getUUID();
            Vec3 toPlayer = player.position().subtract(s.portal).multiply(1, 0, 1).normalize();
            h.moveTo(s.portal.x, s.portal.y - 0.8, s.portal.z, (float) (Math.toDegrees(Math.atan2(toPlayer.z, toPlayer.x)) - 90), 0);
            h.setDeltaMovement(toPlayer.scale(0.32).add(0, 0.42, 0));
            h.hurtMarked = true;
            h.beginMeeting(player);
            h.setScene(HonchoEntity.SCENE_NONE);
            level.playSound(null, h.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.8f, 0.8f);
            level.sendParticles(WHITE, s.portal.x, s.portal.y, s.portal.z, 30, 0.5, 0.9, 0.5, 0.05);
            honcho = h;
        }
        if (honcho != null && honcho.distanceTo(player) > ABORT_DISTANCE) {
            finish(s, player, false);
            return;
        }
        // Honcho gone (dead, discarded, in another dimension) after he should be out, or the player never answering for a minute: the scene ends
        if ((honcho == null && s.ticks > HONCHO_OUT + 20) || ((s.stage == Stage.WAIT_HAND || s.stage == Stage.WAIT_YES) && s.stageTicks > 1200)) {
            finish(s, player, false);
            return;
        }
        if (honcho != null && s.ticks > HONCHO_OUT + 10) {
            // Turned to face the player once he has landed.
            honcho.getLookControl().setLookAt(player, 30f, 30f);
        }
        if (s.stage == Stage.PORTAL) {
            if (honcho != null && s.ticks == SAY_1) HonchoSpeech.say(player, "— Что это было... Я ничего не помню...");
            if (honcho != null && s.ticks == SAY_2) HonchoSpeech.say(player, "— В любом случае привет... Я Хончо....");
            if (honcho != null && s.ticks == ASK_HAND) {
                setStage(s, Stage.WAIT_HAND);
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HonchoPromptPacket("Хончо протягивает руку?", "Подать руку"));
                honcho.setScene(HonchoEntity.SCENE_OFFER_HAND);
            }
        } else if (s.stage == Stage.HAND_TAKEN) {
            if (honcho != null && s.stageTicks == 45) {
                HonchoSpeech.say(player, "— Можно я пойду с тобой?");
            }
            if (honcho != null && s.stageTicks == 70) {
                setStage(s, Stage.WAIT_YES);
                honcho.setScene(HonchoEntity.SCENE_NONE);
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HonchoPromptPacket("Пойти вместе?", "Хорошо"));
            }
        } else if (s.stage == Stage.SHAKE) {
            if (s.stageTicks >= 50) finish(s, player, true);
        }
    }

    /** The oval of the portal in front of the player: a ring of light round a dark hollow, opening, then closing again. */
    private static void portalParticles(ServerLevel level, Scene s) {
        double open = Math.min(1, s.ticks / (double) PORTAL_OPEN);
        double close = s.ticks < PORTAL_CLOSE ? 1 : Math.max(0, 1 - (s.ticks - PORTAL_CLOSE) / (double) (PORTAL_GONE - PORTAL_CLOSE));
        double a = open * close;
        if (a <= 0.02) return;
        double ra = 0.95 * a, rb = 1.7 * a;
        long t = level.getGameTime();
        for (int i = 0; i < 26; i++) {
            double ang = (i / 26.0) * Math.PI * 2 + t * 0.12;
            double x = s.portal.x + s.axis.x * Math.cos(ang) * ra;
            double z = s.portal.z + s.axis.z * Math.cos(ang) * ra;
            double y = s.portal.y + Math.sin(ang) * rb;
            level.sendParticles(i % 3 == 0 ? CYAN : VIOLET, x, y, z, 1, 0.02, 0.02, 0.02, 0);
        }
        for (int i = 0; i < 3; i++) {
            double k = level.random.nextDouble() * Math.PI * 2, r = level.random.nextDouble() * 0.9;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, s.portal.x + s.axis.x * Math.cos(k) * ra * r, s.portal.y + Math.sin(k) * rb * r,
                    s.portal.z + s.axis.z * Math.cos(k) * ra * r, 1, 0, 0, 0, 0.04);
        }
        if (t % 4 == 0) level.sendParticles(ParticleTypes.END_ROD, s.portal.x, s.portal.y, s.portal.z, 1, ra * 0.5, rb * 0.5, ra * 0.5, 0.02);
    }

    /** The scene ends: Honcho goes with the player if they agreed, otherwise he just stays put. */
    private static void finish(Scene s, ServerPlayer player, boolean agreed) {
        SCENES.remove(s.playerId);
        if (player == null) return;
        HonchoEntity honcho = honcho(player, s);
        if (honcho != null) {
            honcho.endMeeting();
            if (agreed) honcho.followPlayer(player);
        }
        if (agreed) LotusPlayerState.markMetHoncho(player);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HonchoPromptPacket("", ""));
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    /** Nothing hurts the player while it goes on. */
    @SubscribeEvent
    public static void onPlayerAttacked(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SCENES.containsKey(player.getUUID())
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Scene s = SCENES.get(player.getUUID());
            if (s != null) finish(s, player, false);
        }
    }
}
