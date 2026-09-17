package com.lotusblight.item;

import com.lotusblight.data.LotusPlayerState;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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
 *
 * A Curios-accessory variant (worn-only visibility instead of a permanent
 * unlock) was attempted but reverted: `implements ICurioItem` directly on
 * this class would make the JVM require curios-api's ICurioItem to be
 * resolvable at class-LOAD time (interfaces are part of a class's hierarchy,
 * unlike a method parameter type, which resolves lazily) — so with Curios
 * merely a soft/optional dependency, this item would throw
 * NoClassDefFoundError and fail to register entirely for every player
 * without Curios installed. Doing this safely needs either a separate
 * conditionally-instantiated ICurioItem adapter class, or promoting Curios
 * to a mandatory dependency (like GeckoLib) — neither was worth deciding
 * unilaterally overnight. See IDEAS_AND_MODS.md.
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
