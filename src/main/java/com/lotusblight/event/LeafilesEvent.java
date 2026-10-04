package com.lotusblight.event;

import com.lotusblight.item.FolderItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The event "Лейфайлс": the player's whole inventory - hotbar, backpack, armour and off hand - turns into a single folder
 * ({@link FolderItem}) that holds all of it. Available only on the neutral line (see {@link NeutralEvents}), that is to a player who has chosen neither the
 * alliance nor the war. This is the skeleton: the trigger that starts it by itself, the way the items come back and the texts are
 * still to be decided; for now it is started with {@code /lotus event leafiles}.
 */
public final class LeafilesEvent {
    private LeafilesEvent() {}

    /** Packs the player's inventory into a folder. The line check is done by {@link NeutralEvents#leafiles}. */
    static EventResult run(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> all = new ArrayList<>();
        for (ItemStack s : inventory.items) if (!s.isEmpty()) all.add(s.copy());
        for (ItemStack s : inventory.armor) if (!s.isEmpty()) all.add(s.copy());
        for (ItemStack s : inventory.offhand) if (!s.isEmpty()) all.add(s.copy());
        if (all.isEmpty()) return EventResult.NOTHING_TO_DO;

        List<ItemStack> folders = FolderItem.packSafely(all);
        inventory.clearContent();
        inventory.setItem(inventory.selected, folders.get(0));
        for (int i = 1; i < folders.size(); i++) {
            net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player, folders.get(i));           // a very heavy inventory needs more than one folder
        }
        player.inventoryMenu.broadcastChanges();
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.6f);
        player.displayClientMessage(Component.translatable("event.lotusblight.leafiles"), false);
        return EventResult.DONE;
    }
}
