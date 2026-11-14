package com.sqc.nuclearsq.progression;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class Streak {
    private Streak() {}

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NuclearSQ.MODID);

    public static final Supplier<AttachmentType<Integer>> TICKS = TYPES.register("streak_ticks",
            () -> AttachmentType.<Integer>builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    public static final int STEP = 40;

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static int ticks(ServerPlayer p) {
        return p.getData(TICKS.get());
    }

    public static void step(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) return;
        boolean running = false, alarm = false;
        int pcx = SectionPos.blockToSectionCoord(p.getBlockX()), pcz = SectionPos.blockToSectionCoord(p.getBlockZ());
        for (int cx = pcx - GuideProgress.SCAN_CHUNKS; cx <= pcx + GuideProgress.SCAN_CHUNKS; cx++) {
            for (int cz = pcz - GuideProgress.SCAN_CHUNKS; cz <= pcz + GuideProgress.SCAN_CHUNKS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : java.util.List.copyOf(chunk.getBlockEntities().values())) {
                    if (!(be instanceof BigMachineBlockEntity m) || m.machine() != BigMachine.CONTROL_ROD_DRIVE || !m.isFormed()) continue;
                    var c = m.core();
                    if (c != null && (c.overheated() || c.hot()) || m.vesselDry() || m.rodFault()) alarm = true;
                    else if (m.rodCount() > 0 && m.reactorPower() > 0) running = true;
                }
            }
        }
        apply(p, alarm, running);
    }

    public static void apply(ServerPlayer p, boolean alarm, boolean running) {
        if (alarm) p.setData(TICKS.get(), 0);
        else if (running) p.setData(TICKS.get(), ticks(p) + STEP);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % STEP == 29) step(sp);
    }
}
