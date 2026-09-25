package com.lotusblight.map;

import com.lotusblight.dialogue.StarFallLibrary;
import com.lotusblight.escape.StarFallEvent;
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
            if (!StarFallEvent.acceptWarLine(player, packet.lineIndex, StarFallLibrary.WAR_FINAL_LINE_INDEX)) return;

            if (lines.get(packet.lineIndex).dealsDamage()) {
                player.hurt(player.damageSources().magic(), 3.0f);
            }
            if (packet.lineIndex == StarFallLibrary.WAR_FINAL_LINE_INDEX) {
                // Inventory#removeItem(ItemStack) compares by reference, so handing it a fresh stack
                // never matched anything - clear the actual slots instead.
                boolean hadRod = removeGraftingRods(player);
                if (!hadRod && player.level() instanceof ServerLevel level) {
                    // "посох удаляется если ты его брал, если не брал то на игрока падает метеорит
                    // и появляется ультра сильный блесковый биом (даже сильнее чем на альянсе)" -
                    // the immediate patch below is the footprint at the moment of impact;
                    // MeteoriteSpreadEngine.seed keeps it growing into whatever it touches afterward.
                    player.hurt(player.damageSources().flyIntoWall(), 6.0f);
                    player.push(0, -0.6, 0);
                    BlockPos impact = player.blockPosition();
                    placePurpleBlessingPatch(level, impact);
                    level.setBlock(impact.below(), ModBlocks.METEORITE_STONE.get().defaultBlockState(), 3);
                    // "метеориты распространяют биом абсолютно на любой блок" - the immediate patch
                    // above is just the initial footprint; this keeps it growing on its own afterward.
                    com.lotusblight.spread.MeteoriteSpreadEngine.seed(level, impact);
                }
            }
        });
        ctx.setPacketHandled(true);
    }

    // "нужно прям реально огромные кусики метеорита" - the original radius-9 reading of "80 blocks"
    // was itself already an interpretation (a literal 80-block radius is ~16,000 blocks); this
    // widens the immediate impact footprint substantially instead of leaving it to
    // MeteoriteSpreadEngine's slower organic growth to eventually catch up.
    private static final int PATCH_RADIUS = 24;

    /** Ground only - not air, not something with contents (chests, furnaces), not bedrock/other unbreakable blocks. */
    private static boolean canRecolor(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return !state.isAir() && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0;
    }

    private static boolean removeGraftingRods(ServerPlayer player) {
        boolean removed = false;
        var inventory = player.getInventory();
        for (var compartment : java.util.List.of(inventory.items, inventory.offhand)) {
            for (int i = 0; i < compartment.size(); i++) {
                if (compartment.get(i).is(ModItems.LOTUS_GRAFTING_ROD.get())) {
                    compartment.set(i, ItemStack.EMPTY);
                    removed = true;
                }
            }
        }
        return removed;
    }

    private static void placePurpleBlessingPatch(ServerLevel level, BlockPos center) {
        var sand = ModBlocks.BLESSING_SAND_PURPLE.get().defaultBlockState();
        var soil = ModBlocks.BLESSING_SOIL_PURPLE.get().defaultBlockState();
        for (int dx = -PATCH_RADIUS; dx <= PATCH_RADIUS; dx++) {
            for (int dz = -PATCH_RADIUS; dz <= PATCH_RADIUS; dz++) {
                if (dx * dx + dz * dz > PATCH_RADIUS * PATCH_RADIUS) continue;
                BlockPos top = center.offset(dx, -1, dz);
                // A column near the very edge of the loaded/simulation area isn't guaranteed
                // loaded - an unguarded getBlockState/setBlock here risks the same synchronous
                // chunk-load deadlock already fixed in LotusChaseStructure.
                if (!level.hasChunkAt(top)) continue;
                if (canRecolor(level, top)) {
                    level.setBlock(top, sand, 3);
                }
                BlockPos below = top.below();
                if (canRecolor(level, below)) {
                    level.setBlock(below, soil, 3);
                }
            }
        }
    }
}
