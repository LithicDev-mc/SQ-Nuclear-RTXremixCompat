package com.sqc.nuclearsq.nanobot;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GadgetTemplatePayload(List<BlockPos> rels) implements CustomPacketPayload {
    public static final Type<GadgetTemplatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "gadget_template"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GadgetTemplatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.rels.size());
                for (BlockPos pos : p.rels) buf.writeLong(pos.asLong());
            },
            buf -> {
                int n = buf.readVarInt();
                List<BlockPos> out = new ArrayList<>(Math.min(n, 65536));
                for (int i = 0; i < n && i < 65536; i++) out.add(BlockPos.of(buf.readLong()));
                return new GadgetTemplatePayload(out);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
