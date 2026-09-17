package com.lotusblight.item;

import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.dialogue.InnerVoiceLibrary;
import com.lotusblight.map.ClientPlayerStateCache;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.PlayerStateSyncPacket;
import com.lotusblight.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * The edible glowing_berry — eating it grants True Light (see TrueLightEffect)
 * and surfaces a short scene from InnerVoiceLibrary, the mod's second, much
 * smaller "dialogue system": a flash of the player's own voice instead of
 * the Lotus's. Shows a one-time tooltip hint before the player has ever
 * eaten one (LotusPlayerState#hasHeardInnerVoice).
 */
public class GlowingBerryItem extends Item {
    public GlowingBerryItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!(entity instanceof Player player)) return result;
        if (!level.isClientSide) {
            player.addEffect(new MobEffectInstance(ModEffects.TRUE_LIGHT.get(), 200, 0));
            if (!LotusPlayerState.hasHeardInnerVoice(player)) {
                LotusPlayerState.setHeardInnerVoice(player, true);
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new PlayerStateSyncPacket(
                            LotusPlayerState.getDialogueBranch(serverPlayer), LotusPlayerState.hasFullMapVisibility(serverPlayer), true));
                }
            }
            return result;
        }
        // finishUsingItem runs on both sides for food; the subtitle-style overlay
        // (InnerVoiceOverlay) is client-only, so it's triggered here, not server-side.
        List<String> scene = InnerVoiceLibrary.randomScene(player.getRandom());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lotusblight.client.InnerVoiceOverlay.show(scene));
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (!ClientPlayerStateCache.heardInnerVoice()) {
            tooltip.add(Component.literal("Съешь — кажется, тебе есть что услышать.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
