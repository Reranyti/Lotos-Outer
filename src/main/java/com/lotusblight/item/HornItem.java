package com.lotusblight.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The horn: given once, at the end of the fight on the desktop. It does nothing by itself - what it does is
 * decided by the hotbar slot it is held in when Shift is pressed (see {@link HornHandler}).
 */
public class HornItem extends Item {
    public HornItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Держи в руке и нажми Shift.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("Ячейка 1: позвать Хончо").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Ячейка 2: очистить блок под прицелом").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Ячейка 3: протрубить — лечение и золотые сердца").withStyle(ChatFormatting.GRAY));
    }
}
