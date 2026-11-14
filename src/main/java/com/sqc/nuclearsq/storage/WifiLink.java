package com.sqc.nuclearsq.storage;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.data.ComputeNet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class WifiLink {
    private WifiLink() {}

    public enum Status {
        OK, NOT_LINKED, OTHER_DIMENSION, ADAPTER_GONE, ADAPTER_OFFLINE, OUT_OF_RANGE;

        public Component message() {
            return Component.translatable("message.nuclearsq.wifi.err." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public static Status check(ServerPlayer player, ItemStack terminal) {
        GlobalPos link = WifiTerminalItem.link(terminal);
        if (link == null) return Status.NOT_LINKED;
        if (!link.dimension().equals(player.level().dimension())) return Status.OTHER_DIMENSION;
        WifiAdapterBlockEntity a = WifiAdapterBlockEntity.at(player.serverLevel(), link.pos());
        if (a == null) return Status.ADAPTER_GONE;
        if (!a.online()) return Status.ADAPTER_OFFLINE;
        if (!a.reaches(player.position())) return Status.OUT_OF_RANGE;
        return Status.OK;
    }

    public static int slotOf(ServerPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
    }

    public static void open(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Status st = check(player, stack);
        if (st != Status.OK) {
            player.displayClientMessage(st.message(), true);
            return;
        }
        BlockPos adapter = WifiTerminalItem.link(stack).pos();
        if (WifiTerminalItem.energy(stack) < WifiValues.terminal().openCost()) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.wifi.err.no_energy", WifiValues.terminal().openCost()), true);
            return;
        }
        if (!ComputeNet.allow(player, player.serverLevel(), adapter)) return;
        WifiTerminalItem.drain(stack, WifiValues.terminal().openCost());
        CraftingTerminalMenu.openWireless(player, adapter, slotOf(player, hand));
    }

    @Nullable
    public static WifiAdapterBlockEntity adapter(ServerLevel level, ItemStack terminal) {
        GlobalPos link = WifiTerminalItem.link(terminal);
        return link == null || !link.dimension().equals(level.dimension()) ? null : WifiAdapterBlockEntity.at(level, link.pos());
    }
}
