package com.lotusblight.item;

import com.lotusblight.dialogue.ObjectZeroPages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * "Страницы дневника Объекта Ноль" — a single collectible page found in the world, not handed
 * out at spawn like {@link LotusWikiItem}. Mirrors LotusWikiItem's own plain-Item-plus-custom-
 * screen pattern (see that class's javadoc: vanilla WrittenBookItem's client GUI hardcodes an
 * exact-reference check against {@code Items.WRITTEN_BOOK} and never opens for a modded
 * subclass), rather than fighting the vanilla book screen a second time.
 *
 * One item id holds five different {@link ObjectZeroPages} variants, picked once at creation
 * and stored in NBT ("Variant") instead of registering five separate items — the stack is
 * otherwise identical, so nothing but the read content differs.
 */
public class ScientistPageItem extends Item {
    private static final String VARIANT_TAG = "Variant";

    public ScientistPageItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack createRandomStack(RandomSource random) {
        ItemStack stack = new ItemStack(com.lotusblight.registry.ModItems.SCIENTIST_PAGE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(VARIANT_TAG, random.nextInt(ObjectZeroPages.VARIANTS.size()));
        return stack;
    }

    public static int variantOf(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        int variant = stack.getTag().getInt(VARIANT_TAG);
        return variant >= 0 && variant < ObjectZeroPages.VARIANTS.size() ? variant : 0;
    }

    @Override
    public Component getName(ItemStack stack) {
        ObjectZeroPages.Variant variant = ObjectZeroPages.VARIANTS.get(variantOf(stack));
        return Component.literal("Страница дневника: " + variant.title());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Записи Объекта Ноль").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            int variant = variantOf(stack);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    net.minecraft.client.Minecraft.getInstance().setScreen(new com.lotusblight.client.ScientistPageScreen(variant)));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
