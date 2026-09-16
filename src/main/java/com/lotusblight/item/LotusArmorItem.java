package com.lotusblight.item;

import com.lotusblight.registry.ModEffects;
import com.lotusblight.registry.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Lotus alloy armor piece. Wearing the full four-piece set grants passive
 * immunity to the mod's own {@link ModEffects#LOTUS_SPORES} effect, a small
 * thematic bonus for committing to the lotus_alloy gear line.
 */
public final class LotusArmorItem extends ArmorItem {
    public LotusArmorItem(ArmorMaterial material, Type type, Item.Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void onArmorTick(ItemStack stack, Level level, Player player) {
        if (level.isClientSide()) {
            return;
        }
        if (hasFullLotusSet(player) && player.hasEffect(ModEffects.LOTUS_SPORES.get())) {
            player.removeEffect(ModEffects.LOTUS_SPORES.get());
        }
    }

    private boolean hasFullLotusSet(Player player) {
        return isPiece(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD), ModItems.LOTUS_HELMET)
                && isPiece(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST), ModItems.LOTUS_CHESTPLATE)
                && isPiece(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS), ModItems.LOTUS_LEGGINGS)
                && isPiece(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET), ModItems.LOTUS_BOOTS);
    }

    private boolean isPiece(ItemStack stack, net.minecraftforge.registries.RegistryObject<Item> expected) {
        return !stack.isEmpty() && stack.is(expected.get());
    }
}
