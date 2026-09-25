package com.lotusblight.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Used to extend WrittenBookItem and rely on the vanilla book-viewer GUI —
 * that never actually opened (see LotusWikiScreen's javadoc for why). Now a
 * plain item that opens a fully custom two-tab screen directly.
 */
public class LotusWikiItem extends Item {
    public LotusWikiItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // Via LotusClientHooks, not inline: building the Screen here made the server verify this
            // item class against client-only Screen and refuse to register it.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.lotusblight.client.LotusClientHooks::openWiki);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static ItemStack createStack() {
        return new ItemStack(com.lotusblight.registry.ModItems.LOTUS_WIKI.get());
    }
}
