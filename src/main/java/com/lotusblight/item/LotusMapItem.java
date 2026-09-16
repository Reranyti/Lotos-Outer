package com.lotusblight.item;

import com.lotusblight.LotusConfig;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.spread.InfectionPhases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;

/**
 * Right-click reveals the nearest outbreaks the player is currently allowed
 * to know about. Unlike the old implementation, this never scans blocks: it
 * queries {@link OutbreakSavedData}'s indexed lookups directly, the same
 * persisted registry {@link com.lotusblight.map.MapSyncManager} uses to feed
 * the HUD/atlas — an O(outbreak count) lookup instead of an O(volume) scan.
 */
public class LotusMapItem extends Item {
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.2f, 1.0f, 0.35f), 1.2f);
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.55f), 1.2f);
    private static final int MAX_MARKERS_SHOWN = 8;

    public LotusMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide && level instanceof ServerLevel server) {
            OutbreakSavedData data = OutbreakSavedData.get(server);
            boolean fullVisibility = LotusPlayerState.hasFullMapVisibility(player);
            int radius = LotusConfig.SCAN_RADIUS.get();
            BlockPos origin = player.blockPosition();

            List<OutbreakRecord> found = data.outbreaksWithin(origin, radius, !fullVisibility).stream()
                    .sorted(Comparator.comparingDouble(o -> o.pos().distSqr(origin)))
                    .limit(MAX_MARKERS_SHOWN)
                    .toList();

            for (OutbreakRecord record : found) {
                BlockPos pos = record.pos();
                boolean heart = server.getBlockState(pos).is(ModBlocks.LOTUS_HEART.get());
                DustParticleOptions color = heart ? PINK : GREEN;
                server.sendParticles(color, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, heart ? 18 : 8, 0.6, 0.8, 0.6, 0.02);
                if (pos.distSqr(origin) > 48 * 48) {
                    double dx = pos.getX() - player.getX();
                    double dz = pos.getZ() - player.getZ();
                    double length = Math.sqrt(dx * dx + dz * dz);
                    if (length > 0) server.sendParticles(color, player.getX() + dx / length * 5, player.getY() + 1.3, player.getZ() + dz / length * 5, 8, 0.25, 0.3, 0.25, 0.01);
                }
            }

            if (found.isEmpty()) {
                server.sendParticles(GREEN, player.getX(), player.getY() + 1.5, player.getZ(), 4, 0.2, 0.2, 0.2, 0.01);
                player.displayClientMessage(Component.literal("Атлас: рядом нет обнаруженных очагов. Ищи воду."), true);
            } else {
                OutbreakRecord nearest = found.get(0);
                double distance = Math.sqrt(nearest.pos().distSqr(origin));
                String phaseName = InfectionPhases.phaseName(nearest.phase());
                player.displayClientMessage(Component.literal("Атлас: очагов " + found.size() + " | ближайший: " + Math.round(distance) + " м | " + phaseName), true);
            }
            player.getCooldowns().addCooldown(this, LotusConfig.MAP_COOLDOWN_TICKS.get());
            player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, found.isEmpty() ? 0.7f : 1.35f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
