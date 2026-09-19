package com.lotusblight.command;

import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.PlayerStateSyncPacket;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.spread.InfectionPhases;
import com.lotusblight.spread.InfectionSpreadEngine;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;

/**
 * Admin/testing commands for the infection systems — spawning, forcing
 * phases/hearts, and overriding per-player dialogue state without having to
 * play through a real outbreak or dialogue every time. All require operator
 * permission (level 2), same as vanilla /gamerule.
 */
public final class LotusCommands {

    private LotusCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotus")
                .requires(source -> source.hasPermission(2))
                .then(outbreakCommands())
                .then(branchCommands())
                .then(mapCommands()));
    }

    // ---- /lotus outbreak ... --------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> outbreakCommands() {
        return Commands.literal("outbreak")
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawnOutbreak(ctx.getSource(), BlockPos.containing(ctx.getSource().getPosition()), 1))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> spawnOutbreak(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"), 1))
                                .then(Commands.argument("phase", IntegerArgumentType.integer(1, 4))
                                        .executes(ctx -> spawnOutbreak(ctx.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                IntegerArgumentType.getInteger(ctx, "phase"))))))
                .then(Commands.literal("list").executes(ctx -> listOutbreaks(ctx.getSource())))
                .then(Commands.literal("setphase")
                        .then(Commands.argument("phase", IntegerArgumentType.integer(1, 4))
                                .executes(ctx -> setNearestPhase(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "phase")))
                                .then(Commands.argument("progress", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(ctx -> setNearestPhase(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "phase"),
                                                FloatArgumentType.getFloat(ctx, "progress"))))))
                .then(Commands.literal("heart").executes(ctx -> forceHeart(ctx.getSource())))
                .then(Commands.literal("remove").executes(ctx -> removeNearest(ctx.getSource())));
    }

    private static int spawnOutbreak(CommandSourceStack source, BlockPos pos, int phase) {
        ServerLevel level = source.getLevel();
        if (!level.getBlockState(pos).isAir()) {
            source.sendFailure(Component.literal("Позиция " + pos.toShortString() + " занята — очисти место для якоря."));
            return 0;
        }
        level.setBlock(pos, ModBlocks.INFECTED_LOTUS.get().defaultBlockState(), 3);
        OutbreakSavedData data = OutbreakSavedData.get(level);
        OutbreakRecord record = data.registerOutbreak(pos.immutable(), level.getGameTime(), false)
                .withPhase(phase)
                .withInfectedBlockCount(InfectionPhases.minBlockCountForPhase(phase));
        data.updateOutbreak(record);
        // Setting/spawning phase 4 for testing used to unconditionally call promoteAnchorToHeart -
        // silently swapping the anchor for the one-and-only Heart in the world just because you
        // wanted to preview phase 4's numbers/density. That's a real, consequential side effect
        // (see OutbreakSavedData#claimHeart) that should only ever happen through the explicit
        // "heart" command below, never as a side effect of setting a number for a test.
        source.sendSuccess(() -> Component.literal("Очаг создан в " + pos.toShortString() + ", фаза " + phase + "."), true);
        return 1;
    }

    private static int listOutbreaks(CommandSourceStack source) {
        OutbreakSavedData data = OutbreakSavedData.get(source.getLevel());
        List<OutbreakRecord> outbreaks = data.allOutbreaks().stream()
                .sorted(Comparator.comparingDouble(o -> o.pos().distSqr(BlockPos.containing(source.getPosition()))))
                .toList();
        if (outbreaks.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Очагов нет."), false);
            return 0;
        }
        for (OutbreakRecord record : outbreaks) {
            source.sendSuccess(() -> Component.literal(String.format("%s фаза %d, блоков %d, %s%s",
                    record.pos().toShortString(), record.phase(), record.infectedBlockCount(),
                    record.hidden() ? "скрыт" : "виден",
                    source.getLevel().getBlockState(record.pos()).is(ModBlocks.LOTUS_HEART.get()) ? ", СЕРДЦЕ" : "")), false);
        }
        return outbreaks.size();
    }

    private static int setNearestPhase(CommandSourceStack source, int phase) {
        return setNearestPhase(source, phase, 0.0f);
    }

    /**
     * progress 0.0 lands exactly on this phase's own minimum block count (the old, only, behaviour
     * - straight to the very start of the phase, never partway through it or near its end). 1.0
     * lands just short of the NEXT phase's threshold. Phase 4 has no upper threshold to interpolate
     * against, so progress there scales against InfectionPhases#progressWithinPhase's own 120-block
     * span instead.
     */
    private static int setNearestPhase(CommandSourceStack source, int phase, float progress) {
        OutbreakRecord nearest = nearest(source);
        if (nearest == null) {
            source.sendFailure(Component.literal("Рядом нет очагов."));
            return 0;
        }
        OutbreakSavedData data = OutbreakSavedData.get(source.getLevel());
        int lower = InfectionPhases.minBlockCountForPhase(phase);
        int upper = phase < 4 ? InfectionPhases.minBlockCountForPhase(phase + 1) - 1 : lower + 120;
        int blockCount = lower + Math.round(net.minecraft.util.Mth.clamp(progress, 0f, 1f) * (upper - lower));
        // Setting a phase for testing is just numbers - it should never silently swap the anchor
        // for the world's one Heart as a side effect. Use the explicit "heart" command for that.
        data.updateOutbreak(nearest.withPhase(phase).withInfectedBlockCount(blockCount));
        source.sendSuccess(() -> Component.literal("Очаг в " + nearest.pos().toShortString() + " переведён на фазу " + phase + " (" + Math.round(progress * 100) + "%)."), true);
        return 1;
    }

    private static int forceHeart(CommandSourceStack source) {
        OutbreakRecord nearest = nearest(source);
        if (nearest == null) {
            source.sendFailure(Component.literal("Рядом нет очагов."));
            return 0;
        }
        boolean placed = InfectionSpreadEngine.promoteAnchorToHeart(source.getLevel(), nearest.pos());
        if (!placed) {
            source.sendFailure(Component.literal("В " + nearest.pos().toShortString() + " нет обычного якоря для апгрейда (может, это уже Сердце)."));
            return 0;
        }
        OutbreakSavedData data = OutbreakSavedData.get(source.getLevel());
        data.updateOutbreak(nearest.withPhase(4).withInfectedBlockCount(InfectionPhases.minBlockCountForPhase(4)));
        source.sendSuccess(() -> Component.literal("Сердце выращено в " + nearest.pos().toShortString() + "."), true);
        return 1;
    }

    private static int removeNearest(CommandSourceStack source) {
        OutbreakRecord nearest = nearest(source);
        if (nearest == null) {
            source.sendFailure(Component.literal("Рядом нет очагов."));
            return 0;
        }
        OutbreakSavedData.get(source.getLevel()).removeOutbreak(nearest.id());
        source.sendSuccess(() -> Component.literal("Очаг в " + nearest.pos().toShortString() + " удалён из реестра (блоки остались — сноси вручную)."), true);
        return 1;
    }

    private static OutbreakRecord nearest(CommandSourceStack source) {
        return OutbreakSavedData.get(source.getLevel()).nearestOutbreak(BlockPos.containing(source.getPosition()), 4096.0, false);
    }

    // ---- /lotus branch ... -----------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> branchCommands() {
        return Commands.literal("branch")
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> getBranch(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("undecided").executes(ctx -> setBranch(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), LotusPlayerState.BRANCH_UNDECIDED)))
                                .then(Commands.literal("alliance").executes(ctx -> setBranch(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), LotusPlayerState.BRANCH_ALLIANCE)))
                                .then(Commands.literal("resistance").executes(ctx -> setBranch(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), LotusPlayerState.BRANCH_RESISTANCE)))));
    }

    private static int getBranch(CommandSourceStack source, ServerPlayer player) {
        int branch = LotusPlayerState.getDialogueBranch(player);
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + ": " + branchName(branch)), false);
        return branch;
    }

    private static int setBranch(CommandSourceStack source, ServerPlayer player, int branch) {
        LotusPlayerState.forceDialogueBranch(player, branch);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PlayerStateSyncPacket(branch, LotusPlayerState.hasFullMapVisibility(player), LotusPlayerState.hasHeardInnerVoice(player)));
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " -> " + branchName(branch) + " (это админ-оверрайд, обходит блокировку одноразового выбора)."), true);
        return 1;
    }

    private static String branchName(int branch) {
        return switch (branch) {
            case LotusPlayerState.BRANCH_ALLIANCE -> "Альянс";
            case LotusPlayerState.BRANCH_RESISTANCE -> "Война";
            default -> "Не выбрано";
        };
    }

    // ---- /lotus map ... ----------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mapCommands() {
        return Commands.literal("map")
                .then(Commands.literal("reveal")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("true").executes(ctx -> setReveal(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), true)))
                                .then(Commands.literal("false").executes(ctx -> setReveal(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false)))));
    }

    private static int setReveal(CommandSourceStack source, ServerPlayer player, boolean reveal) {
        LotusPlayerState.setFullMapVisibility(player, reveal);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PlayerStateSyncPacket(LotusPlayerState.getDialogueBranch(player), reveal, LotusPlayerState.hasHeardInnerVoice(player)));
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + ": полная видимость карты = " + reveal), true);
        return 1;
    }
}
