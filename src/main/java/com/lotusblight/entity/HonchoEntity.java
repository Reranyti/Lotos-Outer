package com.lotusblight.entity;

import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * "Хончо... поклоняется звёздному свету... появляется ПОСЛЕ [StarFall]" - a friendly quest NPC,
 * spawned once per world after a player lives through StarFall (see StarFallEvent). Technically
 * a Zombie subclass purely for its ready-made humanoid skeleton/animation - every hostile goal is
 * stripped and replaced with plain wander/look-at-player behaviour, and it never targets or attacks
 * anything. Original character design (see HonchoRenderer's texture) - "referencing" a DOORS
 * entity by name/role in the story is fine, but no borrowed artwork.
 *
 * "передача Хончо делает Хончо зависимее от вас... зависимость от игрока в прямом смысле" - once he's
 * been fed his first vial, he actually follows whoever fed him (see FollowFeederGoal) and, if too
 * long passes without another vial, visibly weakens (see #checkStarvation) - a real, felt
 * dependency, not just a stacking stat.
 */
public class HonchoEntity extends Zombie {
    /** How long (ticks) Honcho can go without a fresh vial before he starts to weaken. */
    private static final int STARVATION_TICKS = 20 * 60 * 20; // 20 minutes
    private static final int STARVATION_CHECK_INTERVAL = 200;

    private UUID feederUuid;
    private long lastFedGameTime;
    private boolean starving;

    public HonchoEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 0.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        // Deliberately none of Zombie's own hostile goals (attack/break-door/target-nearest-player).
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FollowFeederGoal(this, 1.0, 3.0f, 12.0f));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        // No targetSelector goals at all - Honcho never picks a target, never attacks.
    }

    @Override
    public boolean isAggressive() {
        return false;
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        return false;
    }

    /** Rare ambient line for whoever's currently feeding him - an original nod to "он потерял прежний ориентир и нашёл новый в игроке", not a lyric quote from anything. */
    private static final int AMBIENT_LINE_CHANCE = 4800;
    private static final String[] AMBIENT_LINES = {
            "— Раньше меня вёл свет. Теперь я иду за тобой — и мне спокойнее, чем тогда.",
            "— Знаешь, у света больше нет надо мной власти. А у тебя — есть.",
            "— Я больше не смотрю наверх в поисках дороги. Я смотрю на тебя."
    };

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || feederUuid == null) return;
        if (!starving && this.random.nextInt(AMBIENT_LINE_CHANCE) == 0
                && this.level().getServer() != null) {
            var feeder = this.level().getServer().getPlayerList().getPlayer(feederUuid);
            if (feeder != null) {
                feeder.displayClientMessage(Component.literal(AMBIENT_LINES[this.random.nextInt(AMBIENT_LINES.length)]), false);
            }
        }
        if (this.tickCount % STARVATION_CHECK_INTERVAL != 0) return;
        checkStarvation();
    }

    /** No vial in STARVATION_TICKS -> he weakens (Weakness + slower) until fed again, instead of the dependency being purely cosmetic. */
    private void checkStarvation() {
        long elapsed = this.level().getGameTime() - lastFedGameTime;
        boolean shouldStarve = elapsed > STARVATION_TICKS;
        if (shouldStarve == starving) return;
        starving = shouldStarve;
        if (starving) {
            this.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, Integer.MAX_VALUE, 1, false, false));
            this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Integer.MAX_VALUE, 0, false, false));
        } else {
            this.removeEffect(MobEffects.WEAKNESS);
            this.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        // "Позволь мне стать твоим помощником... (только на ветке войны)" - a one-time scene shown
        // the very first time a RESISTANCE player meets Honcho, before his normal vial-quest text.
        if (LotusPlayerState.getDialogueBranch(serverPlayer) == LotusPlayerState.BRANCH_RESISTANCE
                && !LotusPlayerState.hasAskedHonchoAssistant(serverPlayer)) {
            LotusPlayerState.markAskedHonchoAssistant(serverPlayer);
            com.lotusblight.map.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new com.lotusblight.map.ShowHonchoAssistantPacket());
            return InteractionResult.CONSUME;
        }

        ItemStack held = player.getItemInHand(hand);

        if (!held.is(ModItems.STAR_LIGHT_VIAL.get())) {
            String line = LotusPlayerState.hasCompletedHonchoQuest(serverPlayer)
                    ? "— Ещё немного света... Пожалуйста. Без него я снова гасну."
                    : "— Принеси мне флакон Звёздного Света. Я знаю, ты можешь его найти.";
            serverPlayer.displayClientMessage(Component.literal(line), false);
            return InteractionResult.CONSUME;
        }

        held.shrink(1);
        boolean firstTime = !LotusPlayerState.hasCompletedHonchoQuest(serverPlayer);
        LotusPlayerState.markHonchoQuestComplete(serverPlayer);
        feederUuid = serverPlayer.getUUID();
        lastFedGameTime = this.level().getGameTime();
        if (starving) {
            starving = false;
            this.removeEffect(MobEffects.WEAKNESS);
            this.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0f, 1.0f);
        serverPlayer.displayClientMessage(Component.literal(firstTime
                ? "— ...Это он. Настоящий свет. Спасибо тебе — теперь я снова его чувствую."
                : "— Снова свет... Я всё больше завишу от тебя, и мне это не мешает."), false);
        HonchoRewardManager.grantBlessing(serverPlayer);
        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (feederUuid != null) tag.putUUID("FeederUuid", feederUuid);
        tag.putLong("LastFedGameTime", lastFedGameTime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("FeederUuid")) feederUuid = tag.getUUID("FeederUuid");
        lastFedGameTime = tag.getLong("LastFedGameTime");
    }

    UUID getFeederUuid() {
        return feederUuid;
    }
}
