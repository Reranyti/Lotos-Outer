package com.lotusblight.item;

import com.lotusblight.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The folder the event "Лейфайлс" turns the whole inventory into: every stack is kept whole, with all its tags, in the folder's own
 * data. For now using the folder (right click) gives everything back - how the player gets it back in the story is decided later.
 * Fireproof, so that what is inside survives lava and fire.
 */
public class FolderItem extends Item {
    private static final String KEY = "Contents";

    public FolderItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    /** A folder holding copies of the given stacks (empty ones are left out). */
    public static ItemStack pack(List<ItemStack> stacks) {
        ItemStack folder = new ItemStack(ModItems.FOLDER.get());
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            list.add(stack.save(new CompoundTag()));
        }
        folder.getOrCreateTag().put(KEY, list);
        return folder;
    }

    /** The stacks inside a folder, in the order they were put in. */
    public static List<ItemStack> unpack(ItemStack folder) {
        List<ItemStack> out = new ArrayList<>();
        CompoundTag tag = folder.getTag();
        if (tag == null) return out;
        for (Tag t : tag.getList(KEY, Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) t);
            if (!stack.isEmpty()) out.add(stack);
        }
        return out;
    }

    public static int count(ItemStack folder) {
        CompoundTag tag = folder.getTag();
        return tag == null ? 0 : tag.getList(KEY, Tag.TAG_COMPOUND).size();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack folder = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(folder);
        List<ItemStack> contents = unpack(folder);
        folder.shrink(1);
        for (ItemStack stack : contents) {
            if (!player.getInventory().add(stack)) player.drop(stack, false);           // what does not fit lies at the feet, never lost
        }
        return InteractionResultHolder.consume(folder);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lotusblight.folder.count", count(stack)).withStyle(ChatFormatting.GRAY));
    }
}
