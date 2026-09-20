package com.lotusblight.item;

import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.dialogue.InnerVoiceLibrary;
import com.lotusblight.map.ChatOverhaulBranchColor;
import com.lotusblight.map.ClientPlayerStateCache;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.PlayerStateSyncPacket;
import com.lotusblight.map.ShowInnerVoicePacket;
import com.lotusblight.effect.TrueLightEffect;
import com.lotusblight.effect.TrueLightHeartsManager;
import com.lotusblight.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * The edible glowing_berry — always grants True Light (see TrueLightEffect).
 * The inner-voice scene (InnerVoiceLibrary, shown via InnerVoiceOverlay) only
 * fires unconditionally for the first LotusPlayerState#INNER_VOICE_FREE_USES
 * berries — an introduction, not something every berry triggers. Beyond
 * that, later scenes are meant to be gated behind specific story triggers
 * (not implemented yet), so eating just feeds you until one fires.
 */
public class GlowingBerryItem extends Item {
    /** Blocks eating another berry mid-scene/mid-flicker instead of relying on hunger to gate it - alwaysEat() bypasses hunger entirely. */
    private static final int EAT_COOLDOWN_TICKS = 100;

    public GlowingBerryItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return result;

        player.getCooldowns().addCooldown(this, EAT_COOLDOWN_TICKS);
        player.addEffect(new MobEffectInstance(ModEffects.TRUE_LIGHT.get(), TrueLightEffect.DURATION_TICKS, 0));
        // Granted once for the full duration instead of TrueLightEffect topping it up every second
        // - repeatedly removing/re-adding Night Vision mid-duration was what read as constant
        // screen flicker instead of one clean 20-minute effect.
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, TrueLightEffect.DURATION_TICKS, 0, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, TrueLightEffect.DURATION_TICKS, 0, true, false));
        ChatOverhaulBranchColor.applyTrueLightColor(player);

        if (LotusPlayerState.canTriggerInnerVoiceFreely(player)) {
            LotusPlayerState.incrementInnerVoiceUses(player);
            List<String> scene = InnerVoiceLibrary.randomScene(player.getRandom());
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShowInnerVoicePacket(scene));
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PlayerStateSyncPacket(
                    LotusPlayerState.getDialogueBranch(player), LotusPlayerState.hasFullMapVisibility(player),
                    LotusPlayerState.hasHeardInnerVoice(player), LotusPlayerState.hasSeenGuardian(player),
                    LotusPlayerState.getAllianceCleanseUses(player)));
        } else {
            // No scene left to give — grant/refresh the persistent bonus hearts instead, tied
            // 1:1 to how long the True Light effect just (re)applied will last.
            long newExpiry = player.level().getGameTime() + TrueLightEffect.DURATION_TICKS;
            if (LotusPlayerState.getTrueLightHeartsExpireAt(player) <= 0) {
                player.setAbsorptionAmount(player.getAbsorptionAmount() + TrueLightHeartsManager.BONUS_ABSORPTION);
            }
            LotusPlayerState.setTrueLightHeartsExpireAt(player, newExpiry);
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        // "до сих пор до едения ягод запись от света появляется" - this hint kept promising
        // "something to hear" to players who structurally never could: canTriggerInnerVoiceFreely
        // only ever fires on the RESISTANCE branch (see its own doc). An ALLIANCE player was shown
        // this exact same "eat and listen" hint forever, no matter how many berries they ate, since
        // heardInnerVoice() can never become true for them under the current design.
        boolean canEverHear = ClientPlayerStateCache.dialogueBranch() != LotusPlayerState.BRANCH_ALLIANCE;
        if (canEverHear && !ClientPlayerStateCache.heardInnerVoice()) {
            tooltip.add(Component.literal("Съешь — кажется, тебе есть что услышать.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
