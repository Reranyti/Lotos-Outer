package com.lotusblight.item;

import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.registry.ModEffects;
import com.lotusblight.registry.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Lotus alloy armor piece. Wearing the full four-piece set grants passive
 * immunity to the mod's own {@link ModEffects#LOTUS_SPORES} effect, a small
 * thematic bonus for committing to the lotus_alloy gear line. A player who
 * has also joined the lotus (ALLIANCE branch, {@link LotusPlayerState#hasJoinedLotus})
 * gets a further Resistance bonus on top - the set is meant to visibly
 * reward that story choice, not just gate crafting it.
 */
public final class LotusArmorItem extends ArmorItem {
    private static final int RESISTANCE_TOPUP_TICKS = 60;

    public LotusArmorItem(ArmorMaterial material, Type type, Item.Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void onArmorTick(ItemStack stack, Level level, Player player) {
        if (level.isClientSide()) {
            return;
        }
        if (!hasFullLotusSet(player)) {
            return;
        }
        if (player.hasEffect(ModEffects.LOTUS_SPORES.get())) {
            player.removeEffect(ModEffects.LOTUS_SPORES.get());
        }
        if (LotusPlayerState.hasJoinedLotus(player) && !player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, RESISTANCE_TOPUP_TICKS, 0, true, false));
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
