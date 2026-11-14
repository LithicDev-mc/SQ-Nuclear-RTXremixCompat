package com.sqc.nuclearsq.nanobot;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.storage.StorageAccess;
import com.sqc.nuclearsq.storage.StorageNet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class NanoPort {
    private NanoPort() {}

    @Nullable
    public static GlobalPos coupled(ItemStack canister) {
        return canister.isEmpty() ? null : canister.get(NanobotRegistry.PORT.get());
    }

    public static void couple(ServerPlayer sp, ItemStack canister, BlockPos port) {
        if (canister.isEmpty() || !(sp.level().getBlockState(port).getBlock() instanceof DroneItemPortBlock)) return;
        canister.set(NanobotRegistry.PORT.get(), GlobalPos.of(sp.level().dimension(), port.immutable()));
        sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.port_coupled"), true);
    }

    public static void uncouple(ServerPlayer sp, ItemStack canister, boolean message) {
        if (canister.isEmpty() || canister.get(NanobotRegistry.PORT.get()) == null) return;
        canister.remove(NanobotRegistry.PORT.get());
        if (message) sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.port_uncoupled"), true);
    }

    @Nullable
    public static BlockPos valid(ServerPlayer sp, ItemStack canister) {
        GlobalPos g = coupled(canister);
        if (g == null) return null;
        Level level = sp.level();
        if (!g.dimension().equals(level.dimension()) || !level.isLoaded(g.pos())) return null;
        if (!(level.getBlockState(g.pos()).getBlock() instanceof DroneItemPortBlock)) {
            uncouple(sp, canister, false);
            return null;
        }
        int range = NuclearConfig.value(NuclearConfig.NANOBOT_PORT_RANGE);
        return sp.blockPosition().distSqr(g.pos()) > (double) range * range ? null : g.pos();
    }

    @Nullable
    public static StorageAccess access(ServerPlayer sp, ItemStack canister) {
        BlockPos p = valid(sp, canister);
        if (p == null) return null;
        StorageAccess a = StorageNet.access(sp.level(), p);
        return a.complete() && a.onlineRacks() > 0 ? a : null;
    }
}
