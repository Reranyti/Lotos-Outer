package com.lotusblight.effect;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.LotusPlayerState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * "теперь любые золотые предметы на ветке войны дают черные сердца, тем больше золотых сердец тем
 * меньше оз макс при смерти можно только снять" - eating ANY golden food on the ALLIANCE branch
 * permanently lowers max health instead of helping. Star Light's hostile StarFall script is the one
 * ALLIANCE players get ("Оно не поможет"), so this has to follow the same branch.
 * One black heart = -2.0 max health (1 vanilla heart), stacking, cleared only by dying.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlackHeartManager {
    private static final UUID MODIFIER_ID = UUID.fromString("8f2b6a1e-6b1a-4f3e-9c2f-7b5c9d1a2e30");
    private static final double HEALTH_PER_BLACK_HEART = -2.0;

    private BlackHeartManager() {}

    @SubscribeEvent
    public static void onFinishUsingItem(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var item = event.getItem().getItem();
        if (item != Items.GOLDEN_APPLE && item != Items.ENCHANTED_GOLDEN_APPLE && item != Items.GOLDEN_CARROT) return;
        if (LotusPlayerState.getDialogueBranch(player) != LotusPlayerState.BRANCH_ALLIANCE) return;

        // "Оно не поможет" - strip whatever the golden item just granted instead of fighting the
        // vanilla food-effect pipeline to prevent it from applying in the first place.
        player.removeEffect(MobEffects.REGENERATION);
        player.removeEffect(MobEffects.ABSORPTION);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.removeEffect(MobEffects.FIRE_RESISTANCE);
        player.setAbsorptionAmount(0f);

        int count = LotusPlayerState.incrementBlackHeartCount(player);
        applyModifier(player, count);
        player.displayClientMessage(Component.literal("— Чёрное сердце. Оно не помогает — оно забирает."), true);
    }

    /** Admin/testing override (see com.lotusblight.command.LotusCommands) - sets the count directly and reapplies the health modifier, instead of the real one-per-golden-item path. */
    public static void forceCount(ServerPlayer player, int count) {
        LotusPlayerState.setBlackHeartCount(player, count);
        applyModifier(player, count);
    }

    private static void applyModifier(ServerPlayer player, int blackHeartCount) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(MODIFIER_ID);
        if (blackHeartCount > 0) {
            attr.addPermanentModifier(new AttributeModifier(MODIFIER_ID, "Lotus black heart curse",
                    HEALTH_PER_BLACK_HEART * blackHeartCount, AttributeModifier.Operation.ADDITION));
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Reapplies the modifier on (re)join, since a fresh AttributeInstance may not carry it over reliably across every respawn/relog path. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        applyModifier(player, LotusPlayerState.getBlackHeartCount(player));
    }

    /** "при смерти можно только снять" - dying is the only cure. */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        if (!event.isWasDeath()) {
            // End exit portal - the new player's attributes start fresh, the curse doesn't.
            applyModifier(newPlayer, LotusPlayerState.getBlackHeartCount(newPlayer));
            return;
        }
        if (LotusPlayerState.getBlackHeartCount(newPlayer) <= 0) return;
        LotusPlayerState.clearBlackHeartCount(newPlayer);
        applyModifier(newPlayer, 0);
        newPlayer.displayClientMessage(Component.literal("— Смерть сняла проклятье чёрных сердец."), false);
    }
}
