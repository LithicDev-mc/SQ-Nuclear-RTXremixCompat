package com.sqc.nuclearsq.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.RadiationDebug;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
                .then(Commands.literal("testbau")
                        .then(Commands.literal("clustermaschine").executes(c -> testbau(c.getSource())))));
    }

    private static int testbau(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        net.minecraft.core.BlockPos at = sp.blockPosition().relative(sp.getDirection(), 6);
        String msg = com.sqc.nuclearsq.storage.ClusterTestBuild.machine(src.getLevel(), at);
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
