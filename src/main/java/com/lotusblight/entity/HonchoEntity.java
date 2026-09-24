package com.lotusblight.entity;

import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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

/**
 * "Хончо... поклоняется звёздному свету... появляется ПОСЛЕ [StarFall]" - a friendly quest NPC,
 * spawned once per world after a player lives through StarFall (see StarFallEvent). Technically
 * a Zombie subclass purely for its ready-made humanoid skeleton/animation - every hostile goal is
 * stripped and replaced with plain wander/look-at-player behaviour, and it never targets or attacks
 * anything. Original character design (see HonchoRenderer's texture) - "referencing" a DOORS
 * entity by name/role in the story is fine, but no borrowed artwork.
 */
public class HonchoEntity extends Zombie {
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
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
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

        if (LotusPlayerState.hasCompletedHonchoQuest(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.literal("— Свет уже видел тебя. Этого достаточно."), false);
            return InteractionResult.CONSUME;
        }

        if (!held.is(ModItems.STAR_LIGHT_VIAL.get())) {
            serverPlayer.displayClientMessage(Component.literal("— Принеси мне флакон Звёздного Света. Я знаю, ты можешь его найти."), false);
            return InteractionResult.CONSUME;
        }

        held.shrink(1);
        LotusPlayerState.markHonchoQuestComplete(serverPlayer);
        this.level().playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0f, 1.0f);
        serverPlayer.displayClientMessage(Component.literal(
                "— ...Это он. Настоящий свет. Спасибо тебе — теперь я снова его чувствую."), false);
        HonchoRewardManager.grantBlessing(serverPlayer);
        return InteractionResult.CONSUME;
    }
}
