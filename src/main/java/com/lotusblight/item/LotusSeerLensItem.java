package com.lotusblight.item;

import com.lotusblight.data.LotusPlayerState;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A one-shot consumable: using it permanently grants the player full map
 * visibility (every known outbreak, not just physically discovered ones —
 * see com.lotusblight.map.MapSyncManager, which already checks
 * LotusPlayerState.hasFullMapVisibility on every sync).
 */
public class LotusSeerLensItem extends Item {

    public LotusSeerLensItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (LotusPlayerState.hasFullMapVisibility(player)) {
            player.displayClientMessage(Component.literal("Ты уже видишь все известные очаги заражения."), true);
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide()) {
            LotusPlayerState.setFullMapVisibility(player, true);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 0.6f);
            player.displayClientMessage(Component.literal("Теперь карта показывает все известные очаги заражения."), true);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
