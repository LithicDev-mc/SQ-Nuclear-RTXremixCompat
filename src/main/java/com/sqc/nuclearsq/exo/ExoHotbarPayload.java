package com.sqc.nuclearsq.exo;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record ExoHotbarPayload(int n) implements CustomPacketPayload {
    public static final Type<ExoHotbarPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "exo_hotbar"));
    public static final StreamCodec<ByteBuf, ExoHotbarPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(ExoHotbarPayload::new, ExoHotbarPayload::n);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void swap(ServerPlayer p) {
        if (n < 0 || n > 8 || p.isSpectator() || ExoEvents.level(p, ExoModule.Kind.HOTBAR) <= 0) return;
        var inv = p.getInventory();
        ItemStack held = inv.getItem(inv.selected);
        inv.setItem(inv.selected, inv.getItem(9 + n));
        inv.setItem(9 + n, held);
    }
}
