package com.lotusblight.map;

import com.lotusblight.dialogue.StarFallLibrary;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server: "StarFallOverlay just showed war line #N" - health/inventory are
 * server-authoritative, so the war script's mid-scene beats ((урон), the grafting rod vanishing,
 * the closing meteor impact) have to be applied here rather than client-side. Only the war branch
 * sends this - the alliance script has no such beats.
 */
public class StarFallLineReachedPacket {
    private final int lineIndex;

    public StarFallLineReachedPacket(int lineIndex) {
        this.lineIndex = lineIndex;
    }

    public static void encode(StarFallLineReachedPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.lineIndex);
    }

    public static StarFallLineReachedPacket decode(FriendlyByteBuf buf) {
        return new StarFallLineReachedPacket(buf.readVarInt());
    }

    public static void handle(StarFallLineReachedPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            var lines = StarFallLibrary.warLines();
            if (packet.lineIndex < 0 || packet.lineIndex >= lines.size()) return;

            if (lines.get(packet.lineIndex).dealsDamage()) {
                player.hurt(player.damageSources().magic(), 3.0f);
            }
            if (packet.lineIndex == StarFallLibrary.WAR_FINAL_LINE_INDEX) {
                boolean hadRod = player.getInventory().items.stream().anyMatch(s -> s.is(ModItems.LOTUS_GRAFTING_ROD.get()))
                        || player.getInventory().offhand.stream().anyMatch(s -> s.is(ModItems.LOTUS_GRAFTING_ROD.get()));
                if (hadRod) {
                    player.getInventory().removeItem(new ItemStack(ModItems.LOTUS_GRAFTING_ROD.get()));
                } else if (player.level() instanceof ServerLevel level) {
                    // "посох удаляется если ты его брал, если не брал то на игрока падает метеорит
                    // и появляется ультра сильный блесковый биом (даже сильнее чем на альянсе)" -
                    // real spread-to-any-block conversion is a bigger follow-up; this places an
                    // immediate patch as a working first version.
                    player.hurt(player.damageSources().flyIntoWall(), 6.0f);
                    player.push(0, -0.6, 0);
                    placePurpleBlessingPatch(level, player.blockPosition());
                }
            }
        });
        ctx.setPacketHandled(true);
    }

    private static final int PATCH_RADIUS = 4;

    private static void placePurpleBlessingPatch(ServerLevel level, BlockPos center) {
        var sand = ModBlocks.BLESSING_SAND_PURPLE.get().defaultBlockState();
        var soil = ModBlocks.BLESSING_SOIL_PURPLE.get().defaultBlockState();
        for (int dx = -PATCH_RADIUS; dx <= PATCH_RADIUS; dx++) {
            for (int dz = -PATCH_RADIUS; dz <= PATCH_RADIUS; dz++) {
                if (dx * dx + dz * dz > PATCH_RADIUS * PATCH_RADIUS) continue;
                BlockPos top = center.offset(dx, -1, dz);
                if (!level.getBlockState(top).isAir()) {
                    level.setBlock(top, sand, 3);
                }
                BlockPos below = top.below();
                if (!level.getBlockState(below).isAir()) {
                    level.setBlock(below, soil, 3);
                }
            }
        }
    }
}
