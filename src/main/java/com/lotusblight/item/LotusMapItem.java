package com.lotusblight.item;

import com.lotusblight.LotusConfig;
import com.lotusblight.registry.ModBlocks;
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
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LotusMapItem extends Item {
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.2f, 1.0f, 0.35f), 1.2f);
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.55f), 1.2f);

    public LotusMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide && level instanceof ServerLevel server) {
            List<BlockPos> found = scan(server, player.blockPosition());
            for (BlockPos pos : found) {
                boolean heart = server.getBlockState(pos).is(ModBlocks.LOTUS_HEART.get());
                DustParticleOptions color = heart ? PINK : GREEN;
                server.sendParticles(color, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, heart ? 18 : 8, 0.6, 0.8, 0.6, 0.02);
                if (pos.distSqr(player.blockPosition()) > 48 * 48) {
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
                BlockPos nearest = found.get(0);
                double distance = Math.sqrt(nearest.distSqr(player.blockPosition()));
                int phase = found.size() < 2 ? 1 : found.size() < 4 ? 2 : found.size() < 6 ? 3 : 4;
                String phaseName = phase == 1 ? "Цветение" : phase == 2 ? "Захват реки" : phase == 3 ? "Захват ближников" : "Лотосовый мини-биом";
                player.displayClientMessage(Component.literal("Атлас: очагов " + found.size() + " | ближайший: " + Math.round(distance) + " м | " + phaseName), true);
            }
            player.getCooldowns().addCooldown(this, LotusConfig.MAP_COOLDOWN_TICKS.get());
            player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, found.isEmpty() ? 0.7f : 1.35f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private List<BlockPos> scan(ServerLevel level, BlockPos center) {
        int radius = LotusConfig.SCAN_RADIUS.get();
        int step = 4;
        List<BlockPos> found = new ArrayList<>();
        for (int x = -radius; x <= radius && found.size() < 8; x += step) {
            for (int z = -radius; z <= radius && found.size() < 8; z += step) {
                for (int y = -12; y <= 12 && found.size() < 8; y += step) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(ModBlocks.LOTUS_HEART.get()) || state.is(ModBlocks.INFECTED_LOTUS.get()) || state.is(ModBlocks.INFECTED_WATER.get()) || state.is(ModBlocks.INFECTED_SOIL.get())) {
                        if (found.stream().noneMatch(old -> old.distSqr(pos) < 24 * 24)) found.add(pos.immutable());
                    }
                }
            }
        }
        return found.stream().sorted(Comparator.comparingDouble(pos -> pos.distSqr(center))).toList();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
