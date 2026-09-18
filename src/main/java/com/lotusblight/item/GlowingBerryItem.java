package com.lotusblight.item;

import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.dialogue.InnerVoiceLibrary;
import com.lotusblight.map.ClientPlayerStateCache;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.PlayerStateSyncPacket;
import com.lotusblight.map.ShowInnerVoicePacket;
import com.lotusblight.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
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
    public GlowingBerryItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return result;

        player.addEffect(new MobEffectInstance(ModEffects.TRUE_LIGHT.get(), 200, 0));

        if (LotusPlayerState.canTriggerInnerVoiceFreely(player)) {
            LotusPlayerState.incrementInnerVoiceUses(player);
            List<String> scene = InnerVoiceLibrary.randomScene(player.getRandom());
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShowInnerVoicePacket(scene));
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PlayerStateSyncPacket(
                    LotusPlayerState.getDialogueBranch(player), LotusPlayerState.hasFullMapVisibility(player),
                    LotusPlayerState.hasHeardInnerVoice(player)));
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (!ClientPlayerStateCache.heardInnerVoice()) {
            tooltip.add(Component.literal("Съешь — кажется, тебе есть что услышать.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
