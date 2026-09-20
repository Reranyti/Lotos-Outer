package com.lotusblight.map;

import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Client-to-server: the player picked one of the branch-specific dialogue answers (see
 * LotusDialogueLibrary#playerAnswers / LotusDialogueScreen#choosePrimary). These used to just
 * advance the displayed text with no actual effect on the world - "Где твой настоящий якорь?"
 * asked a question and got nothing back, "Я найду твоё Сердце" found nothing. Specific answer
 * indices now grant a real, matching payoff.
 */
public class DialogueAnswerPacket {
    /** Matches LotusDialogueLibrary.Branch ordinals (UNDECIDED, RESISTANCE, ALLIANCE). */
    private final int branch;
    private final int answerIndex;

    public DialogueAnswerPacket(int branch, int answerIndex) {
        this.branch = branch;
        this.answerIndex = answerIndex;
    }

    public static void encode(DialogueAnswerPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.branch);
        buf.writeVarInt(packet.answerIndex);
    }

    public static DialogueAnswerPacket decode(FriendlyByteBuf buf) {
        return new DialogueAnswerPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(DialogueAnswerPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) return;

            // The packet's branch field used to be trusted outright - nothing here checked it
            // against the player's own actual, server-persisted branch (LotusPlayerState), so a
            // modified client could just send branch=2 while really being RESISTANCE (or vice
            // versa) and claim the other side's exclusive reward (grafting rod / cleansing powder
            // gift) on top of its own. Each reward already has its own one-time flag, so this
            // couldn't be farmed repeatedly, but it could bypass the branch exclusivity entirely.
            if (packet.branch != LotusPlayerState.getDialogueBranch(player)) return;

            // branch 1 = RESISTANCE, 2 = ALLIANCE (see LotusDialogueLibrary.Branch) — matches the
            // fixed answer lists LotusDialogueLibrary#playerAnswers returns for each branch.
            if (packet.branch == 1) {
                switch (packet.answerIndex) {
                    case 0 -> revealNearestOutbreak(player, level);
                    case 1 -> giveCleansingPowderOnce(player);
                    case 2 -> revealHeart(player, level);
                }
            } else if (packet.branch == 2) {
                switch (packet.answerIndex) {
                    case 0 -> giveGraftingRodOnce(player);
                    case 1 -> revealNearestOutbreak(player, level);
                }
            }
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Both dialogue reward answers used to grant their item on every single click, no gate at
     * all - reopening the dialogue and picking the same answer over and over was an infinite
     * item duplication exploit. Each is now a one-time gift per player (see LotusPlayerState).
     */
    private static void giveCleansingPowderOnce(ServerPlayer player) {
        if (com.lotusblight.data.LotusPlayerState.hasReceivedCleansingPowderGift(player)) {
            player.displayClientMessage(Component.literal("Берег уже поделился с тобой запасом — новый порошок ищи сам."), false);
            return;
        }
        com.lotusblight.data.LotusPlayerState.setReceivedCleansingPowderGift(player);
        giveItem(player, ModItems.CLEANSING_POWDER.get(), 2, "Порошок принят берегом — держи запас.");
    }

    private static void giveGraftingRodOnce(ServerPlayer player) {
        if (com.lotusblight.data.LotusPlayerState.hasReceivedGraftingRodGift(player)) {
            player.displayClientMessage(Component.literal("Жезл прививки у тебя уже есть — второй тебе ни к чему."), false);
            return;
        }
        com.lotusblight.data.LotusPlayerState.setReceivedGraftingRodGift(player);
        giveItem(player, ModItems.LOTUS_GRAFTING_ROD.get(), 1, "Жезл прививки лёг тебе в руку — теперь ты можешь направлять рост сам.");
    }

    private static void giveItem(ServerPlayer player, net.minecraft.world.item.Item item, int count, String message) {
        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.displayClientMessage(Component.literal(message), false);
    }

    private static void revealNearestOutbreak(ServerPlayer player, ServerLevel level) {
        OutbreakSavedData data = OutbreakSavedData.get(level);
        OutbreakRecord nearest = data.nearestOutbreak(player.blockPosition(), 512.0, false);
        if (nearest == null) {
            player.displayClientMessage(Component.literal("Ты слишком далеко от воды, чтобы я мог указать якорь."), false);
            return;
        }
        data.updateOutbreak(nearest.withHidden(false));
        BlockPos pos = nearest.pos();
        player.displayClientMessage(Component.literal(String.format(
                "Настоящий якорь: X=%d, Y=%d, Z=%d.", pos.getX(), pos.getY(), pos.getZ())), false);
    }

    /** Only one lotus heart can exist per dimension (see OutbreakSavedData#claimHeart) - find it by its actual block, not a hidden flag. */
    private static void revealHeart(ServerPlayer player, ServerLevel level) {
        OutbreakSavedData data = OutbreakSavedData.get(level);
        if (!data.isHeartClaimed()) {
            player.displayClientMessage(Component.literal("Сердца ещё нет — оно ещё не выросло нигде."), false);
            return;
        }
        for (OutbreakRecord record : data.allOutbreaks()) {
            if (level.hasChunkAt(record.pos()) && level.getBlockState(record.pos()).is(ModBlocks.LOTUS_HEART.get())) {
                data.updateOutbreak(record.withHidden(false));
                BlockPos pos = record.pos();
                double distance = Math.sqrt(player.blockPosition().distSqr(pos));
                player.displayClientMessage(Component.literal(String.format(
                        "Сердце бьётся в %d блоках отсюда: X=%d, Y=%d, Z=%d.", Math.round(distance), pos.getX(), pos.getY(), pos.getZ())), false);
                return;
            }
        }
        player.displayClientMessage(Component.literal("Сердце существует, но его чанк сейчас не загружен — попробуй позже."), false);
    }
}
