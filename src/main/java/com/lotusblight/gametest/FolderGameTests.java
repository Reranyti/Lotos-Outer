package com.lotusblight.gametest;

import com.lotusblight.LotusBlight;
import com.lotusblight.item.FolderItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The folder of the event "Лейфайлс" keeps every stack whole - counts and tags included - and gives them back in order. Only for ./gradlew runGameTestServer. */
@GameTestHolder(LotusBlight.MODID)
@PrefixGameTestTemplate(false)
public final class FolderGameTests {
    private FolderGameTests() {}

    @GameTest(template = "honcho_arena", timeoutTicks = 40, batch = "folder")
    public static void folderKeepsEverything(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        CompoundTag tag = sword.getOrCreateTag();
        tag.putInt("Damage", 7);
        ItemStack bread = new ItemStack(Items.BREAD, 37);
        ItemStack empty = ItemStack.EMPTY;

        ItemStack folder = FolderItem.pack(List.of(sword, empty, bread));
        if (FolderItem.count(folder) != 2) helper.fail("the folder holds " + FolderItem.count(folder) + " stacks, expected 2 (the empty one is left out)");
        List<ItemStack> back = FolderItem.unpack(folder);
        if (back.size() != 2) helper.fail("unpacked " + back.size() + " stacks");
        if (!back.get(0).is(Items.DIAMOND_SWORD) || back.get(0).getTag() == null || back.get(0).getTag().getInt("Damage") != 7) helper.fail("the sword lost its tag");
        if (!back.get(1).is(Items.BREAD) || back.get(1).getCount() != 37) helper.fail("the bread lost its count");
        helper.succeed();
    }
}
