package com.sqc.nuclearsq.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.oil.OilClearJob;
import com.sqc.nuclearsq.radiation.RadiationDebug;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class NuclearCommands {
    private NuclearCommands() {}

    public static final int PERMISSION = 2;

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal(NuclearSQ.MODID)
                .requires(s -> s.hasPermission(PERMISSION))
                .then(Commands.literal("debug")
                        .then(Commands.literal("radiation")
                                .executes(c -> radiation(c.getSource(), null))
                                .then(Commands.literal("on").executes(c -> radiation(c.getSource(), true)))
                                .then(Commands.literal("off").executes(c -> radiation(c.getSource(), false)))))
                .then(Commands.literal("oil")
                        .then(Commands.literal("clear")
                                .then(Commands.literal("all").executes(c -> oilClear(c.getSource(), -1)))
                                .then(Commands.literal("stop").executes(c -> OilClearJob.stop(c.getSource())))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                                        .executes(c -> oilClear(c.getSource(), IntegerArgumentType.getInteger(c, "radius"))))))
                .then(Commands.literal("account")
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", net.minecraft.commands.arguments.GameProfileArgument.gameProfile())
                                        .executes(c -> accountReset(c.getSource(),
                                                net.minecraft.commands.arguments.GameProfileArgument.getGameProfiles(c, "player"))))))
                .then(loops(Commands.literal("testbuild")
                        .then(Commands.literal("clustermachine").executes(c -> testbau(c.getSource()))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> loops(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node) {
        for (String name : com.sqc.nuclearsq.machine.big.LoopTestBuild.NAMES) {
            node = node.then(Commands.literal(name).executes(c -> loop(c.getSource(), name)));
        }
        return node;
    }

    private static int oilClear(CommandSourceStack src, int radius) {
        ServerLevel level = src.getServer().overworld();
        if (src.getLevel().dimension() != Level.OVERWORLD) {
            src.sendFailure(Component.translatable("commands.nuclearsq.oil.overworld"));
            return 0;
        }
        if (radius < 0) return OilClearJob.startAll(level, src);
        return OilClearJob.startRadius(level, src, new ChunkPos(BlockPos.containing(src.getPosition())), radius);
    }

    private static int accountReset(CommandSourceStack src, java.util.Collection<com.mojang.authlib.GameProfile> profiles) {
        var accounts = com.sqc.nuclearsq.apps.AppAccounts.get(src.getServer());
        int n = 0;
        for (var p : profiles) {
            boolean had = accounts.reset(p.getId());
            ServerPlayer online = src.getServer().getPlayerList().getPlayer(p.getId());
            if (online != null) com.sqc.nuclearsq.apps.AppSessions.logout(online);
            if (had) n++;
            String name = p.getName();
            src.sendSuccess(() -> Component.translatable(had ? "commands.nuclearsq.account.reset" : "commands.nuclearsq.account.none", name), true);
        }
        return n;
    }

    private static int testbau(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        net.minecraft.core.BlockPos at = sp.blockPosition().relative(sp.getDirection(), 6);
        String msg = com.sqc.nuclearsq.storage.ClusterTestBuild.machine(src.getLevel(), at);
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int loop(CommandSourceStack src, String name) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        net.minecraft.core.BlockPos origin = com.sqc.nuclearsq.machine.big.LoopTestBuild.originFor(name, sp.blockPosition(), sp.getDirection());
        String msg = com.sqc.nuclearsq.machine.big.LoopTestBuild.build(src.getLevel(), origin, name).message();
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int radiation(CommandSourceStack src, Boolean on) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        boolean now = on != null ? on : !RadiationDebug.enabled(sp);
        RadiationDebug.set(sp, now);
        src.sendSuccess(() -> Component.translatable(now ? "commands.nuclearsq.debug.radiation.on"
                : "commands.nuclearsq.debug.radiation.off"), false);
        return 1;
    }
}
